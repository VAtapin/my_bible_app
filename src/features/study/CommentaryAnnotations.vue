<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import type { CommentaryAnnotations,CommentaryAnnotationLink } from '@/api/commentaryAnnotations'
import {commentaryExternalUrl,commentaryScriptureReference} from '@/services/commentaryAnnotations'
import {commentaryAnnotationMessages} from '@/i18n/commentaryAnnotations'
import {useI18n} from '@/i18n'
import {prayerExcerpt} from '@/services/prayerContent'
import {resolveStudyReference} from '@/services/studyReference'
import {createChapterService} from '@/services/chapterService'
import {createIndexedDbChapterRepository} from '@/offline/indexedDbChapterRepository'
import {bibleApi} from '@/api'
import TemporaryPassage from './TemporaryPassage.vue'
import type {ReferenceGroup} from '@/services/verseStudy'
import {loadCommentaryImage} from '@/services/commentaryMedia'
import AtlasImage from './AtlasImage.vue'
const props=defineProps<{annotations?:CommentaryAnnotations|null;translationCode?:string;source:string;moduleCode?:string}>(),{language,messages}=useI18n(),text=computed(()=>commentaryAnnotationMessages[language.value]),temporary=ref<ReferenceGroup>(),code=ref(''),failed=ref(false),service=createChapterService(bibleApi,createIndexedDbChapterRepository())
const host=(link:CommentaryAnnotationLink)=>{const value=commentaryExternalUrl(link);return value?new URL(value).hostname:''}
async function open(link:CommentaryAnnotationLink){const ref=commentaryScriptureReference(link);if(!ref)return;failed.value=false;try{const value=await resolveStudyReference(ref,service,props.source,undefined,props.translationCode);code.value=value.code;temporary.value=value.group}catch{failed.value=true}}
const images=ref<Record<number,string>>({}),loadingImages=ref(false)
watch([()=>props.annotations,()=>props.moduleCode],async(_,__,cleanup)=>{let stale=false;cleanup(()=>stale=true);for(const url of Object.values(images.value))URL.revokeObjectURL(url);images.value={};loadingImages.value=false;if(!props.moduleCode)return;loadingImages.value=true;for(const [index,media] of (props.annotations?.media??[]).entries()){if(media.status!=='resolved')continue;try{const file=await loadCommentaryImage(props.moduleCode,media);if(stale)return;images.value={...images.value,[index]:URL.createObjectURL(file)}}catch{}}if(!stale)loadingImages.value=false},{immediate:true})
onBeforeUnmount(()=>{for(const url of Object.values(images.value))URL.revokeObjectURL(url)})
</script>
<template><section v-if="annotations&&(annotations.links.length||annotations.media.length)" class="annotations"><h4 v-if="annotations.links.length">{{text.links}}</h4><div v-for="(link,index) in annotations.links" :key="index"><button v-if="commentaryScriptureReference(link)" @click="open(link)">{{prayerExcerpt(link.label)||link.href}}</button><a v-else-if="commentaryExternalUrl(link)" :href="commentaryExternalUrl(link)" target="_blank" rel="noopener noreferrer">{{prayerExcerpt(link.label)||link.href}} · {{host(link)}}</a><p v-else-if="link.label">{{prayerExcerpt(link.label)}}</p></div><h4 v-if="annotations.media.length">{{text.media}}</h4><template v-for="(media,index) in annotations.media" :key="index"><AtlasImage v-if="images[index]" :url="images[index]!" :title="media.alt??media.src??source" installed/><p v-else-if="media.status==='resolved'">{{loadingImages?messages.loading:text.mediaError}}</p><p v-else-if="media.alt">{{prayerExcerpt(media.alt)}}</p></template><p v-if="failed" role="alert">{{text.error}}</p><TemporaryPassage v-if="temporary" :group="temporary" :code="code" :service="service" @close="temporary=undefined"/></section></template>
<style scoped>.annotations{font:14px sans-serif;margin:12px 0}.annotations p{overflow-wrap:anywhere}.annotations button,.annotations a{margin:4px 0;display:block}</style>
