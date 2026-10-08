import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('BibleDesktop is the only data API', () => {
  it('has no separate calendar host, credentials or client in runtime/configuration', () => {
    const root = resolve(import.meta.dirname, '../..')
    const files = [...readdirSync(resolve(root, 'src'), { recursive: true }).map(String).filter((path) => /\.(ts|vue|css)$/u.test(path) && !path.endsWith('.test.ts')).map((path) => resolve(root, 'src', path)), ...readdirSync(resolve(root, 'config')).map((path) => resolve(root, 'config', path)), resolve(root, '.env.example')]
    const obsoleteHost = ['kalender', 'georg-kloster', 'ru'].join('.')
    for (const file of files) {
      const content = readFileSync(file, 'utf8')
      expect(content, file).not.toContain(obsoleteHost)
      expect(content, file).not.toMatch(/VITE_CALENDAR_API_BASE_URL|calendarApiBaseUrl|createKalendarApi|X-Calendar-Client|orthocal-wordpress/u)
    }
  })
})
