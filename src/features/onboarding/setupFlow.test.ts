import { describe, expect, it } from 'vitest'
import { initialSetupStep, stepAfterPreset, stepBeforeSummary } from './setupFlow'

describe('setup flow', () => {
  it('keeps quick setup to preset and summary only', () => {
    expect(initialSetupStep('quick')).toBe('preset')
    expect(stepAfterPreset('quick')).toBe('summary')
    expect(stepBeforeSummary('quick')).toBe('preset')
  })

  it('keeps detailed controls in manual setup', () => {
    expect(initialSetupStep('manual')).toBe('details')
    expect(stepAfterPreset('manual')).toBe('details')
    expect(stepBeforeSummary('manual')).toBe('details')
  })
})
