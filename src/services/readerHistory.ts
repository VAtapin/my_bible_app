import type { KeyValueStorage } from '@/profile/profileRepository'
export interface ReaderHistoryPlace { code: string; book: string; chapter: number; verse: number; offset: number }
export interface ReaderHistoryState { entries: ReaderHistoryPlace[]; cursor: number }
const same = (a: ReaderHistoryPlace, b: ReaderHistoryPlace) => a.code === b.code && a.book === b.book && a.chapter === b.chapter && a.verse === b.verse && a.offset === b.offset
export function validHistoryPlace(value: unknown): value is ReaderHistoryPlace {
  if (!value || typeof value !== 'object') return false
  const p = value as ReaderHistoryPlace
  return typeof p.code === 'string' && !!p.code && typeof p.book === 'string' && !!p.book && Number.isInteger(p.chapter) && p.chapter > 0 && Number.isInteger(p.verse) && p.verse >= 0 && Number.isFinite(p.offset) && p.offset >= 0
}
/** Each reader/window owns a key. Scrolling observes a place without adding entries. */
export class ReaderNavigationHistory {
  state: ReaderHistoryState = { entries: [], cursor: -1 }
  current?: ReaderHistoryPlace
  constructor(private storage: KeyValueStorage, private key: string) {
    try {
      const s = JSON.parse(storage.getItem(key) || 'null') as ReaderHistoryState | null
      if (s && Array.isArray(s.entries) && s.entries.length <= 200 && s.entries.every(validHistoryPlace) && Number.isInteger(s.cursor) && s.cursor >= 0 && s.cursor < s.entries.length) this.state = s
    } catch { /* A malformed local history cannot prevent reading. */ }
    this.current = this.state.entries[this.state.cursor]
  }
  private save() { this.storage.setItem(this.key, JSON.stringify(this.state)) }
  observe(place: ReaderHistoryPlace) {
    if (!validHistoryPlace(place)) return
    this.current = { ...place }
    if (!this.state.entries.length) { this.state = { entries: [{ ...place }], cursor: 0 }; this.save() }
  }
  navigate(target: ReaderHistoryPlace) {
    if (!validHistoryPlace(target)) return
    const entries = this.state.entries.slice(0, this.state.cursor + 1)
    if (this.current && (!entries.length || !same(entries[entries.length - 1]!, this.current))) entries.push({ ...this.current })
    if (!entries.length || !same(entries[entries.length - 1]!, target)) entries.push({ ...target })
    this.state = { entries: entries.slice(-200), cursor: Math.min(entries.length, 200) - 1 }
    this.current = { ...target }; this.save()
  }
  /** Returning first restores the entry before scrolling, then the preceding transition. */
  back(): ReaderHistoryPlace | undefined {
    const anchor = this.state.entries[this.state.cursor]
    if (anchor && this.current && !same(anchor, this.current)) {
      this.state.entries.splice(this.state.cursor + 1, 0, { ...this.current })
      if (this.state.entries.length > 200) {
        if(this.state.cursor===0)this.state.entries.pop()
        else {this.state.entries.shift();this.state.cursor--}
      }
      this.current = { ...anchor }; this.save(); return this.current
    }
    return this.select(this.state.cursor - 1)
  }
  forward() { return this.select(this.state.cursor + 1) }
  select(index: number): ReaderHistoryPlace | undefined {
    if (!Number.isInteger(index) || index < 0 || index >= this.state.entries.length) return undefined
    this.state.cursor = index; this.current = { ...this.state.entries[index]! }; this.save(); return this.current
  }
  get canBack() { const anchor = this.state.entries[this.state.cursor]; return this.state.cursor > 0 || !!(anchor && this.current && !same(anchor, this.current)) }
  get canForward() { return this.state.cursor + 1 < this.state.entries.length }
}
