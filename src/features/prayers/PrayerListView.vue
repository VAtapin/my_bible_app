<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'
import { bibleApi } from '@/api'
import type { LiturgicalWorkSummary } from '@/api/contracts'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import {createIndexedDbDailyContentRepository} from '@/offline/indexedDbDailyContentRepository'
import {createDailyContentService} from '@/services/dailyContentService'
import {useProfileStore} from '@/stores/profileStore'
import {prayerLanguageLabel} from '@/services/prayerEditions'
import {preferredPrayerLanguage,prayerTopics,prayerTopic,prayerWorkCards,type PrayerWorkCard} from '@/services/prayerCatalog'
import {prayerCatalogMessages} from '@/i18n/prayerCatalog'

const service=createDailyContentService(bibleApi,createIndexedDbDailyContentRepository())
const profile=useProfileStore(),{language,messages:text}=useI18n()
const labels=computed(()=>prayerCatalogMessages[language.value])
const works=ref<LiturgicalWorkSummary[]>([]),message=ref('')
const selectedLanguage=ref(''),selectedGroup=ref(''),query=ref(''),selectedTopic=ref('')
const availableLanguages=computed(()=>[...new Set(works.value.flatMap(work=>work.available_languages))])
const languageChoices=computed(()=>[...new Set([...availableLanguages.value,...(selectedLanguage.value?[selectedLanguage.value]:[])])])
const groups=computed(()=>[...new Set(works.value.flatMap(work=>work.prayer_groups??[]))])
const displayWorks=computed(()=>prayerWorkCards(works.value,selectedLanguage.value).filter(({work,edition})=>(!selectedGroup.value||work.prayer_groups?.includes(selectedGroup.value))&&(!selectedTopic.value||prayerTopic(work.slug,'',work.collections,work.prayer_groups)===selectedTopic.value)&&
  `${work.title} ${work.usage_titles?.join(' ')??''} ${edition?.title??''}`.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase())))
function groupLabel(key:string,fallback:string){return labels.value.groups[key as keyof typeof labels.value.groups]??fallback}
function workTarget(card:PrayerWorkCard){return{path:`/liturgical/${card.work.slug}/${card.language}`,query:card.edition?{edition:card.edition.code}:undefined}}
onMounted(async()=>{
  const settings=profile.load()?.prayers
  const preferred=settings?.languageCodes??[language.value]
  selectedLanguage.value=preferredPrayerLanguage(language.value,preferred)
  message.value=text.value.prayers.loading
  try{
    works.value=await service.openLiturgicalWorks('prayers')
    message.value=''
  }catch{message.value=text.value.prayers.unavailable}
})
</script>
<template>
  <MobileShell>
    <section class="reader-heading"><span class="card-icon"><img src="/app-icons/prayers.png" alt="" /></span><span><p class="eyebrow dark-eyebrow">{{text.prayers.eyebrow}}</p><h1>{{text.prayers.title}}</h1></span></section>
    <div class="prayer-filters"><label>{{labels.language}}<select v-model="selectedLanguage"><option value="">{{labels.all}}</option><option v-for="code in languageChoices" :key="code" :value="code">{{prayerLanguageLabel(code,text.setup)}}</option></select></label><label v-if="groups.length">{{labels.group}}<select v-model="selectedGroup"><option value="">{{labels.all}}</option><option v-for="group in groups" :key="group" :value="group">{{groupLabel(group,group)}}</option></select></label><label>{{labels.topic}}<select v-model="selectedTopic"><option value="">{{labels.all}}</option><option v-for="topic in prayerTopics" :key="topic" :value="topic">{{labels.topics[topic]}}</option></select></label><label>{{labels.search}}<input v-model="query" type="search" /></label></div>
    <p v-if="message" class="status" role="status">{{message}}</p>
    <p v-if="works.length&&!displayWorks.length" class="status">{{text.prayers.empty}}</p>
    <section class="content-catalog">
      <RouterLink v-for="card in displayWorks" :key="`${card.work.slug}:${card.edition?.code??card.language}`" class="content-card" :to="workTarget(card)"><span class="module-icon"><img src="/app-icons/prayers.png" alt="" /></span><span><strong>{{card.work.title}}</strong><small>{{prayerLanguageLabel(card.language,text.setup)}}<template v-if="card.edition&&card.work.editions.filter(item=>item.language===card.language).length>1"> · {{card.edition.title}}</template></small></span><span aria-hidden="true">→</span></RouterLink>
    </section>
  </MobileShell>
</template>
<style scoped>.prayer-filters{display:flex;flex-wrap:wrap;gap:12px;margin-bottom:16px}.prayer-filters label{display:flex;flex-direction:column;gap:4px;flex:1;min-width:180px}.prayer-filters select,.prayer-filters input{min-height:42px}</style>
