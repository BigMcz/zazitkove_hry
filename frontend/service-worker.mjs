// Runs after prerender.mjs (npm run build): service worker for playing without a signal (docs/02, kap. 4
// "Offline režim"). It keeps only the app shell; pictures and sounds of a game are cached when they are first
// loaded or when the game package is downloaded, so visitors of the landing page do not download them.
import { generateSW } from 'workbox-build'

const { count, size, warnings } = await generateSW({
  globDirectory: 'dist',
  globPatterns: ['index.html', 'app.html', 'favicon.svg', 'assets/**/*.{js,css,svg,woff2}'],
  swDest: 'dist/sw.js',
  mode: 'production',
  inlineWorkboxRuntime: true,
  skipWaiting: true,
  clientsClaim: true,
  cleanupOutdatedCaches: true,
  // "/" is the prerendered index.html, the other routes of the app get app.html (as from the server)
  navigateFallback: '/app.html',
  navigateFallbackDenylist: [/^\/api\//, /^\/actuator\//],
  runtimeCaching: [
    {
      urlPattern: ({ url }) => url.pathname.startsWith('/images/') || url.pathname.startsWith('/audio/'),
      handler: 'CacheFirst',
      options: {
        cacheName: 'game-media',
        // iPhone plays audio only with Range requests; the cache answers them from the whole file
        rangeRequests: true,
        cacheableResponse: { statuses: [200] },
      },
    },
  ],
})
for (const warning of warnings) {
  console.warn(warning)
}
console.log(`Service worker: ${count} files (${Math.round(size / 1024)} kB) in the app shell`)
