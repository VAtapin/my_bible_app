const pad = (value: number): string => String(value).padStart(2, '0')

export const localDateKey = (date = new Date()): string =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`

export const previousLocalDateKey = (date = new Date()): string => {
  const previous = new Date(date)
  previous.setDate(previous.getDate() - 1)
  return localDateKey(previous)
}
