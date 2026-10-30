// Runs after vite build (npm run build): puts the landing page rendered by src/prerender.tsx into
// dist/index.html, served on "/". Other routes of the app get dist/app.html without it (backend SpaPages).
import { readFile, rm, writeFile } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'
import { build } from 'vite'

const ssrDir = 'dist-ssr'
await build({ logLevel: 'warn', build: { ssr: 'src/prerender.tsx', outDir: ssrDir, emptyOutDir: true } })
try {
  const { render } = await import(pathToFileURL(`${ssrDir}/prerender.js`).href)
  const app = await readFile('dist/index.html', 'utf8')
  const root = '<div id="root"></div>'
  if (!app.includes(root)) {
    throw new Error(`dist/index.html does not contain ${root}`)
  }
  await writeFile('dist/app.html', app)
  // bg-night: the dark background of the landing page also behind the content, as HomePage sets it
  const landing = app.replace('<body>', '<body class="bg-night">').replace(root, `<div id="root">${render()}</div>`)
  await writeFile('dist/index.html', landing)
} finally {
  await rm(ssrDir, { recursive: true, force: true })
}
