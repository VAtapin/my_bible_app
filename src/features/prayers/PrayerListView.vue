<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'
import { bibleApi } from '@/api'
import type { LiturgicalWorkSummary,PrayerCatalog,PrayerSummary } from '@/api/contracts'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import {createIndexedDbDailyContentRepository} from '@/offline/indexedDbDailyContentRepository'
import {createDailyContentService} from '@/services/dailyContentService'
import {useProfileStore} from '@/stores/profileStore'
import {prayerEdition,prayerLanguageLabel} from '@/services/prayerEditions'
import {prayerExcerpt} from '@/services/prayerContent'
import {prayerLanguages,prayerInGroup,prayerCardTarget,uniqueWorkCards} from '@/services/prayerCatalog'
import {prayerCatalogMessages} from '@/i18n/prayerCatalog'

const service=createDailyContentService(bibleApi,createIndexedDbDailyContentRepository())
const profile=useProfileStore(),{language,messages:text}=useI18n()
const labels=computed(()=>prayerCatalogMessages[language.value])
const catalog=ref<PrayerCatalog>(),works=ref<LiturgicalWorkSummary[]>([]),message=ref('')
const selectedLanguage=ref(''),selectedGroup=ref(''),query=ref('')
const availableLanguages=computed(()=>[...new Set([...(catalog.value?.data.flatMap(prayerLanguages)??[]),...works.value.flatMap(work=>work.available_languages)])])
const languageChoices=computed(()=>[...new Set([...availableLanguages.value,...(catalog.value?.external_sources?.map(source=>source.language)??[]),...(selectedLanguage.value?[selectedLanguage.value]:[])])])
const groups=computed(()=>Object.entries(catalog.value?.groups??{}))
const externalSources=computed(()=>catalog.value?.external_sources?.filter(source=>!selectedLanguage.value||source.language===selectedLanguage.value)??[])
const displayPrayers=computed(()=>catalog.value?.data.filter(item=>
  (!selectedLanguage.value||prayerLanguages(item).includes(selectedLanguage.value))&&prayerInGroup(item,selectedGroup.value)&&matchesSettings(item)&&
  `${item.title} ${item.short_title??''}`.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase()))??[])
const displayWorks=computed(()=>works.value.filter(work=>(!selectedLanguage.value||work.available_languages.includes(selectedLanguage.value))&&
  `${work.title} ${work.usage_titles?.join(' ')??''}`.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase())))
function matchesSettings(item:PrayerSummary){
  const settings=profile.configuration?.prayers
  if(!settings||settings.prayerBook)return true
  return settings.morning&&item.category==='morning'||settings.evening&&['evening','evening_rule'].includes(item.category)
}
function groupLabel(key:string,fallback:string){return labels.value.groups[key as keyof typeof labels.value.groups]??fallback}
function workLanguage(work:LiturgicalWorkSummary){return selectedLanguage.value||work.available_languages[0]||''}
function workTarget(work:LiturgicalWorkSummary){const actual=workLanguage(work),edition=prayerEdition(work,actual);return{path:`/liturgical/${work.slug}/${actual}`,query:edition?{edition:edition.code}:undefined}}
onMounted(async()=>{
  const settings=profile.load()?.prayers
  const preferred=settings?.languageCodes??[language.value]
  // The legacy RU request is a compatibility catalogue, not a promise of translated RU editions.
  selectedLanguage.value=preferred.length===1&&preferred[0]!=='ru'?preferred[0]!:''
  message.value=text.value.prayers.loading
  try{
    const result=await service.openPrayerCatalog();catalog.value=result.data
    message.value=result.offline?text.value.prayers.offline:''
    const collections=[result.data.catalog_version!==2&&(settings?.prayerBook||settings?.morning||settings?.evening)?'prayers':null,settings?.akathists?'akathists':null,settings?.canons?'canons':null,settings?.horologion?'horologion':null].filter((value):value is string=>value!==null)
    works.value=uniqueWorkCards((await Promise.all(collections.map(collection=>bibleApi.getLiturgicalWorks(collection)))).flat()).filter(work=>
      !work.collections.includes('prayers')||settings?.prayerBook||(settings?.morning&&/утрен|morgen/i.test(work.title))||(settings?.evening&&/сон грядущим|вечер|abend|nacht/i.test(work.title)))
  }catch{message.value=text.value.prayers.unavailable}
})
</script>
<template>
  <MobileShell>
    <section class="reader-heading"><span class="card-icon"><img src="/app-icons/prayers.png" alt="" /></span><span><p class="eyebrow dark-eyebrow">{{text.prayers.eyebrow}}</p><h1>{{text.prayers.title}}</h1></span></section>
    <div class="prayer-filters"><label>{{labels.language}}<select v-model="selectedLanguage"><option value="">{{labels.all}}</option><option v-for="code in languageChoices" :key="code" :value="code">{{prayerLanguageLabel(code,text.setup)}}</option></select></label><label v-if="groups.length">{{labels.group}}<select v-model="selectedGroup"><option value="">{{labels.all}}</option><option v-for="[key,title] in groups" :key="key" :value="key">{{groupLabel(key,title)}}</option></select></label><label>{{labels.search}}<input v-model="query" type="search" /></label></div>
    <p v-if="message" class="status" role="status">{{message}}</p>
    <p v-if="catalog&&!displayPrayers.length&&!displayWorks.length" class="status">{{text.prayers.empty}}</p>
    <section class="content-catalog">
      <RouterLink v-for="prayer in displayPrayers" :key="prayer.canonical_slug??`prayer-${prayer.id}`" class="content-card" :to="prayerCardTarget(prayer,selectedLanguage)">
        <span class="module-icon"><img src="/app-icons/prayers.png" alt="" /></span><span><em>{{prayer.group?groupLabel(prayer.group,catalog?.groups?.[prayer.group]??prayer.group):text.prayers.prayerBook}}</em><strong>{{prayer.title}}</strong><small>{{prayerLanguages(prayer).map(code=>prayerLanguageLabel(code,text.setup)).join(' · ')}}</small><small v-if="prayer.excerpt">{{prayerExcerpt(prayer.excerpt)}}</small></span><span aria-hidden="true">→</span>
      </RouterLink>
      <RouterLink v-for="work in displayWorks" :key="work.slug" class="content-card" :to="workTarget(work)"><span class="module-icon"><img src="/app-icons/prayers.png" alt="" /></span><span><strong>{{work.title}}</strong><small>{{work.available_languages.map(code=>prayerLanguageLabel(code,text.setup)).join(' · ')}}</small></span><span aria-hidden="true">→</span></RouterLink>
    </section>
    <section v-if="externalSources.length" class="prayer-external"><h2>{{labels.external}}</h2><p>{{labels.externalOnly}}</p><p v-for="source in externalSources" :key="source.url"><a :href="source.url" target="_blank" rel="noopener noreferrer">{{source.title}}</a></p></section>
  </MobileShell>
</template>
<style scoped>.prayer-filters{display:flex;flex-wrap:wrap;gap:12px;margin-bottom:16px}.prayer-filters label{display:flex;flex-direction:column;gap:4px;flex:1;min-width:180px}.prayer-filters select,.prayer-filters input{min-height:42px}.prayer-external{padding:16px;border:1px solid var(--line);border-radius:12px;margin-top:20px}</style>
