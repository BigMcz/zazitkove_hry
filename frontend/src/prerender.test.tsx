import { describe, expect, it } from 'vitest'
import { render } from './prerender.tsx'

describe('prerendered landing page', () => {
  it('contains the content for search engines', () => {
    const html = render()
    expect(html).toContain('<h1')
    expect(html).toContain('Nová pravidla hry.')
    expect(html).toContain('Katova povinnost')
    expect(html).toContain('info@storyhold.cz')
  })

  it('has no game in progress', () => {
    expect(render()).not.toContain('Pokračovat ve hře')
  })
})
