export type AnswerPhase = 'idle' | 'wrong' | 'resolved'

export interface PracticeAnswerState {
  phase: AnswerPhase
  wrongOptionId: string | null
  firstTryCorrect: boolean | null
}

export type AnswerEvent = 'ignored' | 'wrong' | 'correct' | 'corrected'

export interface AnswerTransition {
  state: PracticeAnswerState
  event: AnswerEvent
  recordResult: boolean | null
}

export const initialAnswerState = (): PracticeAnswerState => ({
  phase: 'idle',
  wrongOptionId: null,
  firstTryCorrect: null,
})

export const applyAnswer = (
  state: PracticeAnswerState,
  optionId: string,
  correctOptionId: string,
): AnswerTransition => {
  if (state.phase === 'resolved') {
    return { state, event: 'ignored', recordResult: null }
  }

  if (state.phase === 'idle') {
    if (optionId === correctOptionId) {
      return {
        state: { phase: 'resolved', wrongOptionId: null, firstTryCorrect: true },
        event: 'correct',
        recordResult: true,
      }
    }

    return {
      state: { phase: 'wrong', wrongOptionId: optionId, firstTryCorrect: false },
      event: 'wrong',
      recordResult: false,
    }
  }

  if (optionId === correctOptionId) {
    return {
      state: { ...state, phase: 'resolved' },
      event: 'corrected',
      recordResult: null,
    }
  }

  return { state, event: 'ignored', recordResult: null }
}
