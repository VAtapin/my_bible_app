import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AppConfiguration } from './configuration'
import {
  enqueueProfileSync,
  flushProfileSyncQueue,
  loadProfileSyncQueue,
  loadRemoteProfileMeta,
  parsePersonalRestoreLink,
  personalRestoreLink,
  saveProfileSecret,
  saveRemoteProfileMeta,
} from './profileSync'

class MemoryStorage implements Storage {
  private values = new Map<string, string>()
  get length() { return this.values.size }
  clear() { this.values.clear() }
  getItem(key: string) { return this.values.get(key) ?? null }
  key(index: number) { return [...this.values.keys()][index] ?? null }
  removeItem(key: string) { this.values.delete(key) }
  setItem(key: string, value: string) { this.values.set(key, value) }
}

const configuration: AppConfiguration = {
  version: 2,
  interfaceLanguage: 'ru',
  setupMode: 'manual',
  preset: null,
  sections: ['bible'],
  bible: { translationCode: 'RST', translationCodes: ['RST', 'ELB'] },
  prayers: {
    morning: false, evening: false, prayerBook: false, akathists: true,
    canons: false, horologion: false, languageCodes: ['ru', 'de'],
  },
  calendar: { level: 'major', languageCode: 'ru' },
  notifications: { enabled: false, time: '08:00' },
  createdAt: '2026-09-18T00:00:00.000Z',
  updatedAt: '2026-09-18T00:00:00.000Z',
}

beforeEach(() => {
  vi.stubGlobal('localStorage', new MemoryStorage())
  vi.stubGlobal('sessionStorage', new MemoryStorage())
  vi.stubGlobal('window', { location: { origin: 'https://my.bible-desktop.com' } })
})

describe('profile sync', () => {
  it('keeps only the latest local configuration in the queue', () => {
    saveRemoteProfileMeta({ profileId: 'profile-1', revision: 3 })
    enqueueProfileSync(configuration)
    enqueueProfileSync({ ...configuration, sections: ['bible', 'calendar'] })

    expect(loadProfileSyncQueue()).toHaveLength(1)
    expect(loadProfileSyncQueue()[0]?.configuration.sections).toEqual(['bible', 'calendar'])
  })

  it('puts the secret in the URL fragment and parses it back', () => {
    const link = personalRestoreLink('profile-1', 'secret-value')
    expect(link).toContain('/restore#')
    expect(link).not.toContain('?secret=')
    expect(parsePersonalRestoreLink(link)).toEqual({ profileId: 'profile-1', secret: 'secret-value' })
  })

  it('flushes a queued update with revision and session secret', async () => {
    saveRemoteProfileMeta({ profileId: '11111111-1111-4111-8111-111111111111', revision: 1 })
    saveProfileSecret('session-secret')
    enqueueProfileSync(configuration)
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ data: {
      profile_id: '11111111-1111-4111-8111-111111111111', schema_version: 2, revision: 2,
      configuration, created_at: '2026-09-18T00:00:00Z', updated_at: '2026-09-18T00:01:00Z',
    } }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetcher)

    expect(await flushProfileSyncQueue()).toBe('synced')
    expect(loadProfileSyncQueue()).toHaveLength(0)
    expect(loadRemoteProfileMeta()?.revision).toBe(2)
    expect(fetcher).toHaveBeenCalledWith(
      expect.stringContaining('/v1/profiles/11111111-1111-4111-8111-111111111111'),
      expect.objectContaining({ method: 'PUT' }),
    )
    const request = fetcher.mock.calls[0]?.[1]
    expect(new Headers(request?.headers).get('X-Profile-Secret')).toBe('session-secret')
  })
})
