import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
})

// jsdom does not implement scrolling; the app scrolls to the top on every page change and to the code entry on errors
window.scrollTo = () => {}
Element.prototype.scrollIntoView = () => {}
