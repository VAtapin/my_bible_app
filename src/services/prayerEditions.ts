import type { LiturgicalEditionSummary, LiturgicalWorkSummary } from '@/api/contracts'

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
