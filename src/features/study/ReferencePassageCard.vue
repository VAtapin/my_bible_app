<script setup lang="ts">
import {computed,onMounted,ref,watch} from 'vue'
import type {ReferenceGroup} from '@/services/verseStudy'
import {referencePreviewText,referenceSourceLabel} from '@/services/referenceDisplay'
import {useI18n} from '@/i18n'
import {verseStudyMessages} from '@/i18n/verseStudy'
import {inlineReferenceMessages} from '@/i18n/inlineReferences'
import {readReferenceText} from '@/services/referenceReading'
import {backgroundReferences} from '@/services/backgroundReferences'
import {createChapterService} from '@/services/chapterService'
import {createIndexedDbChapterRepository} from '@/offline/indexedDbChapterRepository'
import {bibleApi} from '@/api'
import {verifiedReference} from '@/api/verseStudy'
const props=defineProps<{group:ReferenceGroup;code:string}>()
const emit=defineEmits<{open:[group:ReferenceGroup];copy:[group:ReferenceGroup,actual:Record<string,string>]}>()
const {language}=useI18n(),labels=computed(()=>verseStudyMessages[language.value]),text=computed(()=>inlineReferenceMessages[language.value])
const actual=ref<Record<string,string>>({}),service=createChapterService(bibleApi,createIndexedDbChapterRepository())
const confirmed=computed(()=>props.group.targets.length>0&&props.group.targets.every(target=>verifiedReference(target.versification)||Boolean(actual.value[target.osis_ref])))
onMounted(()=>watch([()=>props.group,()=>props.code],(_,__,cleanup)=>{
 actual.value={}
 cleanup(backgroundReferences.subscribe(()=>readReferenceText(props.group,props.code,service),value=>{actual.value=value}))
},{immediate:true}))
</script>
<template><article v-if="confirmed" class="reference-passage-card">
 <button class="passage-link" @click="emit('open',group)">{{group.label}}</button>
 <small v-if="referenceSourceLabel(group.source)">{{referenceSourceLabel(group.source)}}</small>
 <template v-for="target in group.targets" :key="target.osis_ref"><p v-if="actual[target.osis_ref]||referencePreviewText(target,'')"><sup>{{target.verse_number}}</sup> {{actual[target.osis_ref]||referencePreviewText(target,'')}}</p></template>
 <div><button @click="emit('open',group)">{{labels.open}}</button><button @click="emit('copy',group,actual)">{{text.copy}}</button></div>
</article></template>
<style scoped>
.reference-passage-card{padding:10px 0;border-bottom:1px solid var(--line)}.passage-link{display:block;text-align:left;font-weight:600;background:none;border:0;padding:4px 0;min-height:44px;color:var(--navy)}small{display:block}p{white-space:pre-wrap;overflow-wrap:anywhere}button{margin-right:6px}
</style>
