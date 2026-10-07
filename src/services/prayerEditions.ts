import type { LiturgicalEditionSummary, LiturgicalWorkSummary } from '@/api/contracts'
export function prayerEdition(work: LiturgicalWorkSummary, language: string): LiturgicalEditionSummary | undefined {
  return work.editions.find((edition) => {
    if (language === 'cu') return edition.language === 'cu' && edition.orthography === 'traditional'
    if (language === 'cu-civil') return (edition.language === 'cu-civil' || edition.language === 'cu') && ['civil', 'civil-accented'].includes(edition.orthography)
    return edition.language === language
  })
}
