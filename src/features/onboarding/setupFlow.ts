import type { SetupMode } from '@/profile/configuration'

export type SetupStep = 'preset' | 'details' | 'summary'

export function initialSetupStep(mode: SetupMode): SetupStep {
  return mode === 'quick' ? 'preset' : 'details'
}

export function stepAfterPreset(mode: SetupMode): SetupStep {
  return mode === 'quick' ? 'summary' : 'details'
}

export function stepBeforeSummary(mode: SetupMode): SetupStep {
  return mode === 'quick' ? 'preset' : 'details'
}
