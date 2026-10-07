import type { CalendarDay, CalendarIcon } from '@/api/contracts'

/** The day API already orders events by the calendar engine's liturgical precedence. */
export function rankedCalendarIcons(day: CalendarDay): CalendarIcon[] {
  if (day.source === 'kalendar-api') return (day.icons ?? []).filter((icon) => icon.image_url)
  const priority = (icon: CalendarIcon) => {
    const index = day.events.findIndex((event) => icon.calendar_record_ids?.some((id) => event.id === id || event.id === `${id}:${day.date}`))
    return index < 0 ? Number.MAX_SAFE_INTEGER : index
  }
  return (day.icons ?? []).filter((icon) => icon.image_url).slice().sort((left, right) => priority(left) - priority(right))
}
