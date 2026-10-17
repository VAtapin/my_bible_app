import type {PrayerSummary,LiturgicalWorkSummary,LiturgicalEditionSummary} from '@/api/contracts'

/** A work may serve several groups/languages, but still has one catalogue card. */
export function uniquePrayerCards(items:PrayerSummary[]):PrayerSummary[]{
  const unique=new Map<string,PrayerSummary>()
  for(const item of items){
    if(item.catalog_visible===false)continue
    const key=item.canonical_slug??`legacy:${item.id}`
    if(!unique.has(key))unique.set(key,item)
  }
  return[...unique.values()]
}
export function prayerLanguages(item:PrayerSummary):string[]{return item.available_languages??[item.language_code]}
export function preferredPrayerLanguage(interfaceLanguage:string,configured:string[]):string{
  return configured.includes(interfaceLanguage)?interfaceLanguage:configured[0]??interfaceLanguage
}
export interface PrayerWorkCard {work:LiturgicalWorkSummary;language:string;edition?:LiturgicalEditionSummary}
const prayerWorkCollections=new Set(['prayers','canons','akathists','horologion','horologion-laity','horologion-appendix'])
export function prayerCatalogWorks(works:LiturgicalWorkSummary[]):LiturgicalWorkSummary[]{
  return works.filter(work=>work.collections.some(collection=>prayerWorkCollections.has(collection))&&!work.collections.includes('prayer-books'))
}
/** Show published prayer and worship units, never their source-book containers. */
export function prayerWorkCards(works:LiturgicalWorkSummary[],selectedLanguage:string):PrayerWorkCard[]{
  return prayerCatalogWorks(works).flatMap(work=>{
    const language=selectedLanguage||work.available_languages[0]
    if(!language||!work.available_languages.includes(language))return[]
    const editions=work.editions.filter(edition=>edition.language===language)
    return (selectedLanguage?editions:editions.slice(0,1)).map(edition=>({work,language,edition}))
  })
}
export function prayerInGroup(item:PrayerSummary,group:string):boolean{return !group||(item.groups??(item.group?[item.group]:[])).includes(group)}
export function prayerCardTarget(item:PrayerSummary,language:string){
  const selected=language||item.language_code
  if(!prayerLanguages(item).includes(selected))throw new Error('Prayer edition unavailable')
  return item.canonical_slug?{path:`/liturgical/${item.canonical_slug}/${selected}`}:{path:`/prayers/${item.id}`,query:language?{language}:undefined}
}
export function uniqueWorkCards(items:LiturgicalWorkSummary[]):LiturgicalWorkSummary[]{return[...new Map(items.map(item=>[item.slug,item])).values()]}

export function isCompletePrayer(item:Pick<PrayerSummary,'canonical_slug'|'completeness'|'content_revision'>):boolean{return Boolean(item.canonical_slug&&item.completeness==='complete'&&item.content_revision)}

export const prayerTopics=['morning','evening','communion','meals','work','study','living','departed','initial','canons','akathists','hours','hymns','occasions'] as const
export function prayerTopic(slug:string,category='',collections:string[]=[],groups:string[]=[]):string{
  if(category==='morning'||slug.startsWith('prayer-morning-')||slug==='prayer-optina')return 'morning'
  if(['evening','evening_rule'].includes(category)||slug.startsWith('prayer-evening-')||slug==='prayer-before-sleep')return 'evening'
  if(category.startsWith('communion')||slug.includes('communion'))return 'communion'
  if(slug.includes('meal')||['before_meal','after_meal'].includes(category))return 'meals'
  if(slug.includes('study')||slug.includes('student')||slug.includes('lesson')||category==='study')return 'study'
  if(slug.includes('work'))return 'work'
  if(slug.includes('depart')||slug.includes('usopshih')||slug.includes('razluchenii'))return 'departed'
  if(slug==='prayer-living')return 'living'
  if(collections.includes('canons')||slug.startsWith('kanon-'))return 'canons'
  if(collections.includes('akathists')||slug.startsWith('akafist-'))return 'akathists'
  if(collections.some(c=>c.startsWith('horologion'))||slug==='paschal-hours')return 'hours'
  if(collections.includes('calendar-reference')||slug.startsWith('tropari-')||slug.startsWith('hymn-')||slug.startsWith('calendar-'))return 'hymns'
  if(groups.includes('initial')||['prayer-initial','prayer-trisagion','prayer-trinity','prayer-creed'].includes(slug))return 'initial'
  return 'occasions'
}

/** Resolve every explicitly indexed book unit, including units omitted from the public prayer list. */
export async function prayerSectionWorks(
  works:LiturgicalWorkSummary[],
  openBook:(work:LiturgicalWorkSummary)=>Promise<import('@/api/contracts').LiturgicalWorkVersion>,
  openWork:(slug:string)=>Promise<LiturgicalWorkSummary>,
):Promise<LiturgicalWorkSummary[]>{
  const result=new Map(works.filter(work=>!work.collections.includes('prayer-books')).map(work=>[work.slug,work]))
  for(const book of works.filter(work=>work.collections.includes('prayer-books'))){
    const version=await openBook(book)
    if(!version.bookSections?.length)throw new Error('Prayer section index unavailable')
    for(const section of version.bookSections)for(const unit of section.works){
      if(!result.has(unit.slug))result.set(unit.slug,await openWork(unit.slug))
    }
  }
  return [...result.values()]
}
