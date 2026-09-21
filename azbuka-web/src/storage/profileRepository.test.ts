import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { reactive } from 'vue'
import { clearProfile, defaultProfile, loadProfile, saveProfile } from './profileRepository'

describe('profile repository', () => {
  beforeEach(async () => {
    await clearProfile()
  })

  it('persists a user profile in IndexedDB', async () => {
    const profile = { ...defaultProfile(), locale: 'de' as const, learnedLetterIds: ['az'] }

    await saveProfile(profile)

    await expect(loadProfile()).resolves.toEqual(profile)
  })

  it('stores an isolated snapshot instead of retaining mutable input', async () => {
    const profile = defaultProfile()
    await saveProfile(profile)
    profile.learnedLetterIds.push('az')

    await expect(loadProfile()).resolves.toEqual(defaultProfile())
  })

  it('accepts a reactive Pinia-style profile', async () => {
    const profile = reactive({ ...defaultProfile(), learnedLetterIds: ['az'] })

    await expect(saveProfile(profile)).resolves.toBeUndefined()
    await expect(loadProfile()).resolves.toEqual({ ...defaultProfile(), learnedLetterIds: ['az'] })
  })

  it('returns null before onboarding', async () => {
    await expect(loadProfile()).resolves.toBeNull()
  })
})
