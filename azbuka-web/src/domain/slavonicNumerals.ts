export interface NumeralPart {
  value: number
  glyph: string
}

export const SLAVONIC_NUMERAL_GROUPS = {
  units: [{ value: 1, glyph: 'а' }, { value: 2, glyph: 'в' }, { value: 3, glyph: 'г' }, { value: 4, glyph: 'д' }, { value: 5, glyph: 'є' }, { value: 6, glyph: 'ѕ' }, { value: 7, glyph: 'з' }, { value: 8, glyph: 'и' }, { value: 9, glyph: 'ѳ' }],
  tens: [{ value: 10, glyph: 'і' }, { value: 20, glyph: 'к' }, { value: 30, glyph: 'л' }, { value: 40, glyph: 'м' }, { value: 50, glyph: 'н' }, { value: 60, glyph: 'ѯ' }, { value: 70, glyph: 'о' }, { value: 80, glyph: 'п' }, { value: 90, glyph: 'ч' }],
  hundreds: [{ value: 100, glyph: 'р' }, { value: 200, glyph: 'с' }, { value: 300, glyph: 'т' }, { value: 400, glyph: 'ꙋ' }, { value: 500, glyph: 'ф' }, { value: 600, glyph: 'х' }, { value: 700, glyph: 'ѱ' }, { value: 800, glyph: 'ѡ' }, { value: 900, glyph: 'ц' }],
} as const

const units = Object.fromEntries(SLAVONIC_NUMERAL_GROUPS.units.map((part) => [part.value, part.glyph])) as Record<number, string>
const tens = Object.fromEntries(SLAVONIC_NUMERAL_GROUPS.tens.map((part) => [part.value, part.glyph])) as Record<number, string>
const hundreds = Object.fromEntries(SLAVONIC_NUMERAL_GROUPS.hundreds.map((part) => [part.value, part.glyph])) as Record<number, string>
const titlo = '҃'

const assertSupportedValue = (value: number) => {
  if (!Number.isInteger(value) || value < 1 || value > 999) {
    throw new RangeError('Church Slavonic numeral must be an integer from 1 to 999')
  }
}

export const decomposeSlavonicNumeral = (value: number): NumeralPart[] => {
  assertSupportedValue(value)

  const parts: NumeralPart[] = []
  let remainder = value

  const hundred = Math.floor(remainder / 100) * 100
  if (hundred) {
    parts.push({ value: hundred, glyph: hundreds[hundred]! })
    remainder %= 100
  }

  if (remainder >= 11 && remainder <= 19) {
    parts.push({ value: remainder - 10, glyph: units[remainder - 10]! }, { value: 10, glyph: tens[10]! })
  } else {
    const ten = Math.floor(remainder / 10) * 10
    if (ten) parts.push({ value: ten, glyph: tens[ten]! })
    const unit = remainder % 10
    if (unit) parts.push({ value: unit, glyph: units[unit]! })
  }

  return parts
}

export const toSlavonicNumeral = (value: number): string => {
  const parts = decomposeSlavonicNumeral(value)
  return `${parts.map((part) => part.glyph).join('')}${titlo}`
}

export interface SlavonicClockValue {
  hours: string
  minutes: string
  seconds: string
}

export const toSlavonicClockValue = (hours: number, minutes: number, seconds: number): SlavonicClockValue => {
  if (!Number.isInteger(hours) || hours < 0 || hours > 23 || !Number.isInteger(minutes) || minutes < 0 || minutes > 59 || !Number.isInteger(seconds) || seconds < 0 || seconds > 59) {
    throw new RangeError('Clock value is outside the 24-hour range')
  }

  return {
    hours: hours === 0 ? '—' : toSlavonicNumeral(hours),
    minutes: minutes === 0 ? '—' : toSlavonicNumeral(minutes),
    seconds: seconds === 0 ? '—' : toSlavonicNumeral(seconds),
  }
}
