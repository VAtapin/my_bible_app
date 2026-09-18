import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { ru } from './ru'

describe('Russian interface assets', () => {
  it('uses deployable application icon URLs for setup cards', () => {
    const icons = [
      ...Object.values(ru.presets).map(({ icon }) => icon),
      ...Object.values(ru.sections).map(({ icon }) => icon),
    ]

    for (const icon of icons) {
      expect(icon).toMatch(/^\/app-icons\/[a-z-]+\.png$/)
      expect(existsSync(join(process.cwd(), 'public', icon.slice(1)))).toBe(true)
    }
  })
})
