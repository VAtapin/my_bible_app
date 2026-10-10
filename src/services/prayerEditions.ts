import type { LiturgicalEditionSummary, LiturgicalWorkSummary, PrayerDetail } from '@/api/contracts'

/** Local presentation metadata is cached beside the original, unchanged API response. */
export type PresentedPrayer = PrayerDetail & { text_edition?: { language: string; orthography: string } }

/** Use only the edition explicitly linked to the exact legacy source, never its title or wording. */
export function linkedPrayerEdition(id: number, works: LiturgicalWorkSummary[], baseUrl: string): PresentedPrayer['text_edition'] {
  const source = `${baseUrl.replace(/\/$/, '')}/prayers/${id}`
  const editions = works.filter(work => work.source_url === source).flatMap(work => work.editions)
  const distinct = new Map(editions.map(edition => [`${edition.language}:${edition.orthography}`, edition]))
  if (distinct.size !== 1) return undefined // Ambiguous metadata cannot determine this text's edition.
  const edition = [...distinct.values()][0]!
  return { language: edition.language, orthography: edition.orthography }
}

export function prayerTextPresentation(prayer: Pick<PresentedPrayer, 'language_code' | 'text_edition'>) {
  const language = prayer.text_edition?.language ?? prayer.language_code
  const orthography = prayer.text_edition?.orthography
  return {
    language,
    traditional: language === 'cu' && (!orthography || orthography === 'traditional'),
    civil: language === 'cu-civil' || (language === 'cu' && ['civil', 'civil-accented'].includes(orthography ?? '')),
  }
}

export function prayerLanguageLabel(language: string, labels: { russian: string; german: string; churchSlavonic: string; churchSlavonicCivil: string }): string {
  const names: Record<string, string> = { ru: labels.russian, de: labels.german, uk: 'Українська', en: 'English', cu: labels.churchSlavonic, 'cu-civil': labels.churchSlavonicCivil }
  return names[language] ?? language.toUpperCase()
}

export function prayerEdition(work: LiturgicalWorkSummary, language: string): LiturgicalEditionSummary | undefined {
  return work.editions.find((edition) => {
    if (language === 'cu') return edition.language === 'cu' && edition.orthography === 'traditional'
    if (language === 'cu-civil') return (edition.language === 'cu-civil' || edition.language === 'cu') && ['civil', 'civil-accented'].includes(edition.orthography)
    return edition.language === language
  })
}
