/** @type {import('tailwindcss').Config} */
module.exports = {
	content: [
		'./src/main/resources/templates/**/*.html',
		'./src/main/resources/static/js/**/*.js'
	],
	theme: {
		extend: {
			screens: {
				'tall': { 'raw': '(min-height: 820px)' }
			}
		}
	},
	safelist: [
		// Dynamic status badge classes (interview.js, audio-processor.js)
		'bg-red-500/20', 'text-red-400', 'border-red-500/50',
		'bg-blue-500/20', 'text-blue-400', 'border-blue-500/50',
		'bg-green-500/20', 'text-green-400', 'border-green-500/50',
		'bg-yellow-500/20', 'text-yellow-400', 'border-yellow-500/50',
		'bg-slate-500/20', 'text-slate-400', 'border-slate-500/50',
		// Dynamic classList operations (interview.js, apikey.js)
		'bg-slate-700', 'bg-transparent', 'text-slate-500',
		'ring-2', 'ring-green-500',
		'px-4', 'py-1.5', 'rounded-full', 'border',
		'text-sm', 'font-bold', 'tracking-wider', 'uppercase',
		'transition-all', 'duration-300',
		'hidden',
		// Language switcher disabled state
		'opacity-50', 'cursor-not-allowed',
		// Step 2 position chip class swapping
		'bg-blue-500/10',
		'shadow-[0_0_12px_rgba(59,130,246,0.5)]',
	],
	plugins: [],
}
