<script setup lang="ts">
import{computed,ref,watch,onMounted}from'vue'
import{temporaryWindowMessages}from'@/i18n/temporaryWindow'
import type{BibleChapter}from'@/api/contracts'
import type{ChapterService}from'@/services/chapterService'
import type{ReferenceGroup}from'@/services/verseStudy'
import{resolvedVerseChapter}from'@/services/verseLocations'
import{useI18n}from'@/i18n'
import{verseStudyMessages}from'@/i18n/verseStudy'
import ContinuousReading from'@/features/prototype/ContinuousReading.vue'
const props=defineProps<{group:ReferenceGroup;code:string;service:ChapterService;windowsAvailable?:boolean}>(),emit=defineEmits<{close:[];assign:[chapter:BibleChapter,verse:number,id:0|1]}>()
const{language,messages}=useI18n(),labels=computed(()=>verseStudyMessages[language.value])
const windowLabels=computed(()=>temporaryWindowMessages[language.value]),targetWindow=ref('separate')
function assign(){const value=chapter.value,verse=value?.verses.find(v=>v.osis_ref===props.group.targets[0]?.osis_ref);if(value&&verse&&targetWindow.value!=='separate')emit('assign',value,verse.number,Number(targetWindow.value) as 0|1)}
const chapter=ref<BibleChapter>(),error=ref(false),retry=ref(0),empty=new Set<string>()
const modal=ref<HTMLDialogElement>()
onMounted(()=>modal.value?.showModal())
watch([()=>props.group,()=>props.code,retry],async(_,__,cleanup)=>{let stale=false;cleanup(()=>stale=true);chapter.value=undefined;error.value=false;try{
  const target=props.group.targets[0]!,value=await resolvedVerseChapter(props.code,target.osis_ref,props.service);if(!stale)chapter.value=value
}catch{if(!stale)error.value=true}},{immediate:true})
</script>
<template><dialog ref="modal" class="temporary-overlay" :aria-label="labels.temporary" @cancel.prevent="emit('close')"><section><header><h3>{{group.label}} · {{labels.temporary}}</h3><button @click="emit('close')">{{labels.back}}</button></header><label v-if="windowsAvailable">{{windowLabels.window}} <select v-model="targetWindow"><option value="separate">{{windowLabels.separate}}</option><option value="0">{{windowLabels.window}} 1</option><option value="1">{{windowLabels.window}} 2</option></select> <button :disabled="!chapter||targetWindow==='separate'" @click="assign">{{labels.temporary}}</button></label><p v-if="error" role="alert">{{labels.missing}} <button @click="retry++">{{labels.retry}}</button></p><p v-else-if="!chapter" role="status">{{messages.loading}}</p><ContinuousReading v-else :key="chapter.translation.code+chapter.book.slug+chapter.chapter.number" :initial="chapter" :initial-verse="chapter.verses.find(v=>v.osis_ref===group.targets[0]!.osis_ref)?.number" :service="service" :bookmarks="empty" read-only /></section></dialog></template>
<style scoped>.temporary-overlay{position:fixed;inset:0;border:0;padding:0;background:var(--white);max-width:none;max-height:none;width:100vw;height:100dvh;margin:0}.temporary-overlay::backdrop{background:#0008}.temporary-overlay>section{display:flex;flex-direction:column;background:var(--white);width:100%;height:100%;overflow:hidden;padding:8px 10px;box-sizing:border-box}.temporary-overlay :deep(.continuous-scroll){flex:1;height:auto;min-height:0}.temporary-overlay header{display:flex;justify-content:space-between;align-items:center;gap:8px}.temporary-overlay h3{font-size:16px}</style>
