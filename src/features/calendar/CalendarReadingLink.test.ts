import {readFileSync} from 'node:fs'
import {describe,expect,it} from 'vitest'

const calendar=readFileSync(new URL('./CalendarView.vue',import.meta.url),'utf8')
const readings=calendar.slice(calendar.indexOf('v-for="reading in day.readings"'),calendar.indexOf('<CalendarServiceTexts'))
const android=readFileSync(new URL('../../../mobile/androidApp/src/main/kotlin/com/bibledesktop/myapp/ui/daily/CalendarOverview.kt',import.meta.url),'utf8')

describe('calendar readings open by tapping their text',()=>{
 it('makes the reading title the sole keyboard-accessible opening control',()=>{
  expect(readings.match(/<button\b/g)).toHaveLength(1)
  expect(readings).toContain('type="button" class="calendar-reading-text" @click="selectedReading=reading"')
  expect(readings).toContain('<strong>{{ calendarReadingTitle(reading,language) }}</strong></button>')
  expect(readings).not.toContain('text.calendar.open')
 })
 it('keeps Android reading titles clickable without a separate opening label',()=>{
  const row=android.split('value.readings.forEach')[1]!.split('CalendarService(')[0]!
  expect(row).toContain('onClick={reading=item}')
  expect(row).toContain('Text(calendarReadingTitle(item,language))')
  expect(row).not.toContain('R.string.calendar_open')
 })
})
