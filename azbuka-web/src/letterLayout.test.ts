import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const styles = readFileSync(new URL('./styles.css', import.meta.url), 'utf8')
const view = readFileSync(new URL('./views/LetterView.vue', import.meta.url), 'utf8')

describe('shared letter detail proportions', () => {
  it('keeps the letter header compact without shrinking its readable glyph', () => {
    expect(styles).toMatch(/\.letter-hero \{ min-height: 160px;/)
    expect(styles).toContain('.large-glyph { min-height: 130px; font-size: 5.5rem; }')
  })
  it('enlarges the Slavonic quotation on phones and wide screens, preserving its font and language', () => {
    expect(view).toContain('<blockquote lang="cu">')
    expect(styles).toMatch(/\.letter-example blockquote \{[^}]*font-family: Ponomar, Georgia, serif; font-size: clamp\(1\.75rem, 4\.5vw, 2\.5rem\)/)
    expect(styles).toContain('.letter-example blockquote { padding: 18px; font-size: 1.75rem; }')
  })
})
