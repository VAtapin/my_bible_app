import type {PrayerSummary,LiturgicalWorkSummary} from '@/api/contracts'

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
export function prayerInGroup(item:PrayerSummary,group:string):boolean{return !group||(item.groups??(item.group?[item.group]:[])).includes(group)}
export function prayerCardTarget(item:PrayerSummary,language:string){
  const selected=language||item.language_code
  if(!prayerLanguages(item).includes(selected))throw new Error('Prayer edition unavailable')
  return item.canonical_slug?{path:`/liturgical/${item.canonical_slug}/${selected}`}:{path:`/prayers/${item.id}`,query:language?{language}:undefined}
}
export function uniqueWorkCards(items:LiturgicalWorkSummary[]):LiturgicalWorkSummary[]{return[...new Map(items.map(item=>[item.slug,item])).values()]}

export function isCompletePrayer(item:Pick<PrayerSummary,'canonical_slug'|'completeness'|'content_revision'>):boolean{return Boolean(item.canonical_slug&&item.completeness==='complete'&&item.content_revision)}
