// AudioWorklet processor: streams Int16 PCM (24kHz) from a ring buffer.
// Main thread posts raw Int16 ArrayBuffers via port; this worklet converts to
// Float32 on ingest and emits sample-accurate audio into the graph. When the
// ring empties, output silence — no scheduling races, no chunk overlap.

const RING_SIZE = 24000 * 30; // 30 seconds of 24kHz audio

class PcmPlayerProcessor extends AudioWorkletProcessor {
	constructor() {
		super();
		this.ring = new Float32Array(RING_SIZE);
		this.writePos = 0;
		this.readPos = 0;
		this.available = 0;

		this.port.onmessage = (ev) => {
			const msg = ev.data;
			if (msg && msg.type === 'clear') {
				this.writePos = 0;
				this.readPos = 0;
				this.available = 0;
				return;
			}
			// msg is an ArrayBuffer of Int16 PCM samples
			const pcm = new Int16Array(msg);
			for (let i = 0; i < pcm.length; i++) {
				if (this.available >= RING_SIZE) {
					// Ring full — drop oldest to make room. Shouldn't happen in normal flow.
					this.readPos = (this.readPos + 1) % RING_SIZE;
					this.available--;
				}
				this.ring[this.writePos] = pcm[i] / 32768;
				this.writePos = (this.writePos + 1) % RING_SIZE;
				this.available++;
			}
			// Report buffered samples back so main thread can track fill level
			this.port.postMessage({type: 'buffered', samples: this.available});
		};
	}

	process(inputs, outputs) {
		const channel = outputs[0][0];
		if (!channel) return true;
		for (let i = 0; i < channel.length; i++) {
			if (this.available > 0) {
				channel[i] = this.ring[this.readPos];
				this.readPos = (this.readPos + 1) % RING_SIZE;
				this.available--;
			} else {
				channel[i] = 0; // underrun → silence (no click — zero crossing)
			}
		}
		return true;
	}
}

registerProcessor('pcm-player', PcmPlayerProcessor);
