// Shared Tailwind classes. Large touch targets (min 56 px) and high contrast for use outdoors in sunlight.

const buttonBase =
  'inline-flex min-h-14 w-full items-center justify-center rounded-xl px-5 text-lg font-semibold ' +
  'transition-colors focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-amber-500 ' +
  'disabled:cursor-not-allowed disabled:opacity-50'

export const buttonPrimary = `${buttonBase} bg-stone-900 text-white hover:bg-stone-700 active:bg-stone-800`

export const buttonSecondary =
  `${buttonBase} border-2 border-stone-900 bg-white text-stone-900 hover:bg-stone-100 active:bg-stone-200`

export const buttonChoice =
  `${buttonBase} border-2 border-stone-400 bg-white text-stone-900 hover:border-stone-900 active:bg-stone-100`

export const linkSubtle =
  'inline-flex min-h-11 items-center text-base font-medium text-stone-700 underline underline-offset-4 ' +
  'hover:text-stone-950'

export const input =
  'min-h-14 w-full rounded-xl border-2 border-stone-400 bg-white px-4 text-xl text-stone-950 ' +
  'placeholder:text-stone-500 focus:border-stone-900 focus:outline-3 focus:outline-amber-500'

export const card = 'rounded-2xl border border-stone-300 bg-white p-5 shadow-sm'
