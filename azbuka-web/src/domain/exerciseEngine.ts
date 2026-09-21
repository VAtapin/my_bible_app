import { letters } from '@/data/letters'
import type { ExerciseKind, ExerciseQuestion, Letter, Locale } from './types'

const shuffle = <T>(items: T[], random: () => number): T[] => {
  const copy = [...items]
  for (let index = copy.length - 1; index > 0; index -= 1) {
    const target = Math.floor(random() * (index + 1))
    ;[copy[index], copy[target]] = [copy[target]!, copy[index]!]
  }
  return copy
}

const buildOptions = (
  letter: Letter,
  locale: Locale,
  kind: ExerciseKind,
  random: () => number,
): ExerciseQuestion['options'] => {
  const candidates = kind === 'numeric-value'
    ? letters.filter((candidate) => candidate.numericValue !== null)
    : letters
  const distractors = shuffle(candidates.filter((candidate) => candidate.id !== letter.id), random).slice(0, 1)
  return shuffle([letter, ...distractors], random).map((candidate) => ({
    id: candidate.id,
    label: kind === 'name-to-glyph'
      ? candidate.glyph
      : kind === 'numeric-value'
        ? String(candidate.numericValue)
        : candidate.name[locale]
  }))
}

export const createSession = (
  locale: Locale,
  count = 10,
  random: () => number = Math.random,
): ExerciseQuestion[] => {
  const pool = shuffle(letters, random)
  return Array.from({ length: Math.min(count, pool.length) }, (_, index) => {
    const letter = pool[index]!
    const availableKinds: ExerciseKind[] = letter.numericValue === null
      ? ['glyph-to-name', 'name-to-glyph']
      : ['glyph-to-name', 'name-to-glyph', 'numeric-value']
    const kind = availableKinds[Math.floor(random() * availableKinds.length)]!
    const prompt = kind === 'glyph-to-name'
      ? letter.glyph
      : kind === 'name-to-glyph'
        ? letter.name[locale]
        : letter.glyph

    return {
      id: `${letter.id}-${index}-${kind}`,
      kind,
      prompt,
      options: buildOptions(letter, locale, kind, random),
      correctOptionId: letter.id,
      letterId: letter.id
    }
  })
}

export const isCorrectAnswer = (question: ExerciseQuestion, optionId: string): boolean =>
  question.correctOptionId === optionId
