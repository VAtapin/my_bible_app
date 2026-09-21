const units: Record<number, string> = { 1: 'а', 2: 'в', 3: 'г', 4: 'д', 5: 'є', 6: 'ѕ', 7: 'з', 8: 'и', 9: 'ѳ' }
const tens: Record<number, string> = { 10: 'і', 20: 'к', 30: 'л', 40: 'м', 50: 'н', 60: 'ѯ', 70: 'о', 80: 'п', 90: 'ч' }
const hundreds: Record<number, string> = { 100: 'р', 200: 'с', 300: 'т', 400: 'ꙋ', 500: 'ф', 600: 'х', 700: 'ѱ', 800: 'ѡ', 900: 'ц' }
const titlo = '҃'

export const toSlavonicNumeral = (value: number): string => {
  if (!Number.isInteger(value) || value < 1 || value > 999) {
    throw new RangeError('Church Slavonic numeral must be an integer from 1 to 999')
  }

  const parts: string[] = []
  let remainder = value

  const hundred = Math.floor(remainder / 100) * 100
  if (hundred) {
    parts.push(hundreds[hundred]!)
    remainder %= 100
  }

  if (remainder >= 11 && remainder <= 19) {
    parts.push(units[remainder - 10]!, tens[10]!)
  } else {
    const ten = Math.floor(remainder / 10) * 10
    if (ten) parts.push(tens[ten]!)
    const unit = remainder % 10
    if (unit) parts.push(units[unit]!)
  }

  return `${parts.join('')}${titlo}`
}

export interface SlavonicClockValue {
  hours: string
  minutes: string
}

export const toSlavonicClockValue = (hours: number, minutes: number): SlavonicClockValue => {
  if (!Number.isInteger(hours) || hours < 0 || hours > 23 || !Number.isInteger(minutes) || minutes < 0 || minutes > 59) {
    throw new RangeError('Clock value is outside the 24-hour range')
  }

  return {
    hours: hours === 0 ? '—' : toSlavonicNumeral(hours),
    minutes: minutes === 0 ? '—' : toSlavonicNumeral(minutes),
  }
}
