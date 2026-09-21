import { describe, expect, it } from 'vitest'
import { createSession, isCorrectAnswer } from './exerciseEngine'

const deterministicRandom = () => 0.42

describe('exercise engine', () => {
  it('creates a session with two unique answer options', () => {
    const session = createSession('ru', 6, deterministicRandom)

    expect(session).toHaveLength(6)
    for (const question of session) {
      expect(question.options).toHaveLength(2)
      expect(new Set(question.options.map((option) => option.id)).size).toBe(2)
      expect(question.options.some((option) => option.id === question.correctOptionId)).toBe(true)
    }
  })

  it('evaluates an answer by stable letter id', () => {
    const [question] = createSession('de', 1, deterministicRandom)

    expect(question).toBeDefined()
    expect(isCorrectAnswer(question!, question!.correctOptionId)).toBe(true)
    expect(isCorrectAnswer(question!, 'not-the-answer')).toBe(false)
  })

  it('keeps number exercises out of alphabet practice', () => {
    const session = createSession('cu', 30, deterministicRandom)

    expect(session.every((question) => question.kind === 'glyph-to-name' || question.kind === 'name-to-glyph')).toBe(true)
  })
})
