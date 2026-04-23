// AudioWorklet processor: captures Float32 input, resamples to 16kHz if needed,
// converts to Int16 PCM, and batches frames before posting to main thread.
// Runs on the audio render thread — not affected by main-thread GC/jank.

class PcmRecorderProcessor extends AudioWorkletProcessor {
	constructor(options) {
		super();
		const opts = (options && options.processorOptions) || {};
		this.targetSampleRate = opts.targetSampleRate || 16000;
		// Post ~4096 target-rate samples per batch (256ms @ 16kHz) to mirror
		// previous ScriptProcessor behavior and keep packet cadence stable.
		this.batchSize = opts.batchSize || 4096;
		this.inputSampleRate = sampleRate; // global in AudioWorkletGlobalScope
		this.resampleRatio = this.inputSampleRate / this.targetSampleRate;
		this.outBuffer = new Int16Array(this.batchSize);
		this.outIndex = 0;
		this.resampleCursor = 0;
	}

	process(inputs) {
		const input = inputs[0];
		if (!input || input.length === 0) return true;
		const channel = input[0];
		if (!channel) return true;

		if (this.resampleRatio === 1) {
			for (let i = 0; i < channel.length; i++) {
				this.pushSample(channel[i]);
			}
		} else {
			// Linear-interpolation resample (sufficient for 48k→16k speech)
			while (this.resampleCursor < channel.length) {
				const idx = Math.floor(this.resampleCursor);
				const frac = this.resampleCursor - idx;
				const s0 = channel[idx] || 0;
				const s1 = channel[idx + 1] !== undefined ? channel[idx + 1] : s0;
				this.pushSample(s0 + (s1 - s0) * frac);
				this.resampleCursor += this.resampleRatio;
			}
			this.resampleCursor -= channel.length;
		}
		return true;
	}

	pushSample(f) {
		let s = f < -1 ? -1 : f > 1 ? 1 : f;
		this.outBuffer[this.outIndex++] = s < 0 ? s * 0x8000 : s * 0x7FFF;
		if (this.outIndex >= this.batchSize) {
			// Transfer ownership of a copy to keep the processor buffer reusable
			const copy = new Int16Array(this.outBuffer);
			this.port.postMessage(copy.buffer, [copy.buffer]);
			this.outIndex = 0;
		}
	}
}

registerProcessor('pcm-recorder', PcmRecorderProcessor);
