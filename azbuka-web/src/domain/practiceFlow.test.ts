import { describe, expect, it } from 'vitest'
import { applyAnswer, initialAnswerState } from './practiceFlow'

describe('practice answer flow', () => {
  it('resolves and records a correct first answer', () => {
    const result = applyAnswer(initialAnswerState(), 'az', 'az')

    expect(result.event).toBe('correct')
    expect(result.recordResult).toBe(true)
    expect(result.state).toEqual({
      phase: 'resolved',
      wrongOptionId: null,
      firstTryCorrect: true,
    })
  })

  it('records one mistake and then waits for the correct option', () => {
    const mistake = applyAnswer(initialAnswerState(), 'buki', 'az')

    expect(mistake.event).toBe('wrong')
    expect(mistake.recordResult).toBe(false)
    expect(mistake.state.phase).toBe('wrong')

    const repeatedMistake = applyAnswer(mistake.state, 'vedi', 'az')
    expect(repeatedMistake.event).toBe('ignored')
    expect(repeatedMistake.recordResult).toBeNull()

    const correction = applyAnswer(mistake.state, 'az', 'az')
    expect(correction.event).toBe('corrected')
    expect(correction.recordResult).toBeNull()
    expect(correction.state).toEqual({
      phase: 'resolved',
      wrongOptionId: 'buki',
      firstTryCorrect: false,
    })
  })

  it('ignores taps after the question has been resolved', () => {
    const resolved = applyAnswer(initialAnswerState(), 'az', 'az').state
    const result = applyAnswer(resolved, 'buki', 'az')

    expect(result.event).toBe('ignored')
    expect(result.state).toBe(resolved)
  })
})
