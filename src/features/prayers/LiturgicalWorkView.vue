<script setup lang="ts">
import { computed, watch, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { bibleApi } from '@/api'
import {ApiError} from '@/api/client'
import type { LiturgicalWorkVersion,LiturgicalWorkSummary } from '@/api/contracts'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import { normalizePrayerText } from '@/services/prayerContent'
import { prayerLanguageLabel, prayerEdition } from '@/services/prayerEditions'
import {createDailyContentService} from '@/services/dailyContentService'
import {createIndexedDbDailyContentRepository} from '@/offline/indexedDbDailyContentRepository'
import PrayerContent from '@/components/PrayerContent.vue'
import {prayerCatalogMessages} from '@/i18n/prayerCatalog'

const route = useRoute(), router=useRouter()
const { language,messages: text } = useI18n()
const labels=computed(()=>prayerCatalogMessages[language.value])
const service=createDailyContentService(bibleApi,createIndexedDbDailyContentRepository())
const work = ref<LiturgicalWorkVersion>()
const metadata=ref<LiturgicalWorkSummary>()
const selectedSection=ref(''),sectionWorks=ref<LiturgicalWorkSummary[]>([])
watch([()=>work.value?.edition,selectedSection],async(_,__,cleanup)=>{
  let stale=false;cleanup(()=>stale=true);sectionWorks.value=[]
  const section=work.value?.bookSections?.find(item=>item.id===selectedSection.value)
  if(!section||!bibleApi.getLiturgicalWork)return
  const values=await Promise.allSettled(section.works.map(item=>bibleApi.getLiturgicalWork!(item.slug)))
  if(!stale)sectionWorks.value=values.flatMap(result=>result.status==='fulfilled'?[result.value]:[])
})
function unitTarget(unit:LiturgicalWorkSummary){
  const actual=work.value!.language
  const edition=unit.editions.find(item=>item.language===actual&&item.code===work.value!.edition)??prayerEdition(unit,actual)
  return {path:`/liturgical/${unit.slug}/${actual}`,query:edition?{edition:edition.code}:undefined}
}
const message = ref('')
const isPrayer=computed(()=>metadata.value?.collections.includes('prayers')??false)
const editionChoices=computed(()=>metadata.value?.editions.filter(item=>item.language===work.value?.language)??[])
function chooseEdition(event:Event){router.replace({path:route.path,query:{...route.query,edition:(event.target as HTMLSelectElement).value}})}
function pageLabel(pages:number[]){return pages.length>1&&pages.every((page,index)=>index===0||page===pages[index-1]!+1)?`${pages[0]}–${pages.at(-1)}`:pages.join(', ')}
function jump(id:string){document.getElementById(`block-${id}`)?.scrollIntoView({block:'start'})}
const visibleBlocks = computed(() => work.value?.blocks.filter((block) => block.text.trim()).map((block) => ({ ...block, text: normalizePrayerText(block.text) })) ?? [])

watch([()=>route.params.slug,()=>route.params.language,()=>route.query.edition],async(_,__,cleanup) => {
  let stale=false;cleanup(()=>stale=true);work.value=undefined;metadata.value=undefined;selectedSection.value=''
  const slug = String(route.params.slug ?? '')
  const workLanguage = String(route.params.language ?? '')
  if (!slug || !workLanguage) {
    message.value = text.value.prayers.notFound
    return
  }
  message.value = text.value.prayers.loadingPrayer
  try {
    const result=await service.openLiturgicalVersion(slug,workLanguage,typeof route.query.edition==='string'?route.query.edition:undefined)
    if(stale)return
    work.value=result.data;metadata.value=result.metadata
    message.value=result.offline?text.value.prayers.openedOffline:text.value.prayers.savedOffline
  } catch (error) {
    if(stale)return
    message.value=error instanceof ApiError&&error.status===404?text.value.prayers.notFound:text.value.prayers.openFailed
  }
},{immediate:true})
</script>

<template>
  <MobileShell back-to="/prayers" reading>
    <article v-if="work" class="prayer-reading liturgical-reading" :class="{ 'traditional-prayer': work.orthography === 'traditional' && work.language === 'cu', 'civil-prayer': work.language.startsWith('cu') && ['civil', 'civil-accented'].includes(work.orthography) }" :lang="work.language.startsWith('cu') ? 'cu' : work.language">
      <p class="eyebrow dark-eyebrow">{{ prayerLanguageLabel(work.language, text.setup) }}</p>
      <h1>{{ work.title }}</h1>
      <label v-if="!isPrayer&&editionChoices.length>1" class="edition-picker">{{labels.edition}}<select :value="work.edition" @change="chooseEdition"><option v-for="edition in editionChoices" :key="edition.code" :value="edition.code">{{edition.title}}</option></select></label>
      <details v-if="work.bookSections?.length" class="book-contents"><summary>{{labels.contents}}</summary><ol><li v-for="section in work.bookSections" :key="section.id"><button v-if="section.startBlockId" type="button" @click="jump(section.startBlockId)">{{section.title}}</button><button v-else type="button" @click="selectedSection=selectedSection===section.id?'':section.id">{{section.title}}</button><small v-if="section.printed_pages.length"> · {{labels.pages}} {{pageLabel(section.printed_pages)}}</small><ul v-if="selectedSection===section.id"><li v-for="unit in sectionWorks.filter(item=>item.available_languages.includes(work!.language))" :key="unit.slug"><RouterLink :to="unitTarget(unit)">{{unit.title}}</RouterLink></li></ul></li></ol></details>
      <details v-if="!isPrayer&&work.sourceEditionNotes?.length"><summary>{{labels.edition}}</summary><p v-for="note in work.sourceEditionNotes" :key="note" class="rubric">{{note}}</p></details>
      <p v-if="!isPrayer&&work.completeness==='complete'">{{labels.complete}}</p>
      <section v-if="!isPrayer&&metadata?.intro" class="prayer-intro"><h2>{{labels.intro}}</h2><PrayerContent :content="metadata.intro" /></section>
      <template v-for="block in visibleBlocks" :key="block.id">
        <h2 v-if="block.kind === 'heading'" :id="`block-${block.id}`" class="prayer-text-heading">{{ block.text }}</h2>
        <p v-else :id="`block-${block.id}`" :class="{ rubric: block.kind === 'rubric' }">{{ block.text }}</p>
      </template>
      <p class="status" role="status">{{message}}</p>
    </article>
    <p v-else class="status" role="status">{{ message }}</p>
  </MobileShell>
</template>

<style scoped>.edition-picker{display:flex;flex-wrap:wrap;align-items:center;gap:8px}.edition-picker select{max-width:100%;min-width:0}.book-contents{margin:12px 0}.book-contents li{margin:6px 0}.book-contents button{padding:4px 0;min-height:0;height:auto;font-size:14px;line-height:1.4;border:0;background:transparent;color:var(--primary);text-align:left}.book-contents small{font-size:12px}.prayer-text-heading{scroll-margin-top:12px}</style>
