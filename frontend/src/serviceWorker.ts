/**
 * Registers the service worker built by service-worker.mjs, so that the app opens and plays without a signal.
 * Only in the production build; the Vite dev server has no service worker.
 */
export function registerServiceWorker(): void {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) {
    return
  }
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // Without the service worker the app still works online
    })
  })
}
