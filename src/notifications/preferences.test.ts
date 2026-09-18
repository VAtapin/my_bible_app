import { describe, expect, it } from 'vitest'
import { defaultNotificationPreferences, isNotificationPreferences } from './preferences'

describe('notification preferences', () => {
  it('accepts the versioned default preferences', () => {
    expect(isNotificationPreferences(defaultNotificationPreferences())).toBe(true)
  })

  it('rejects invalid category times', () => {
    const value = defaultNotificationPreferences()
    value.categories.morning.time = '25:00'
    expect(isNotificationPreferences(value)).toBe(false)
  })
})
