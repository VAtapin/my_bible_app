<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref} from 'vue'
import {useI18n} from '@/i18n'
import {offlineBundlesMessages} from '@/i18n/offlineBundles'
import {createOfflineBundleCoordinator,savedOfflineBundles,saveOfflineBundle,forgetOfflineBundle,bundleSize,type BundleMember,type OfflineBundle,type BundleProgress} from '@/services/offlineBundles'
import {prayerExcerpt as readingText} from '@/services/prayerContent'
const props=defineProps<{disabled?:boolean}>(),emit=defineEmits<{changed:[];busyChange:[busy:boolean]}>(),{language}=useI18n(),text=computed(()=>offlineBundlesMessages[language.value]),service=createOfflineBundleCoordinator()
const members=ref<BundleMember[]>([]),bundles=ref<OfflineBundle[]>([]),selected=ref<string[]>([]),name=ref(''),bible=ref(''),strong=ref(''),query=ref(''),page=ref(0),busy=ref(false),failed=ref(false),progress=ref<BundleProgress>(),checking=ref(false),unavailable=ref(false),publishedStudyEmpty=ref(false)
let controller:AbortController|undefined
const locked=computed(()=>busy.value||checking.value||props.disabled)
const chosen=computed(()=>selected.value.map(id=>members.value.find(m=>m.id===id)).filter((m):m is BundleMember=>!!m)),size=computed(()=>bundleSize(chosen.value))
const neededZipBytes=computed(()=>chosen.value.filter(m=>m.pack&&(!m.ready||m.update)).reduce((sum,m)=>sum+(m.bytes??0),0))
const filtered=computed(()=>members.value.filter(m=>`${m.name} ${m.id}`.toLocaleLowerCase().includes(query.value.toLocaleLowerCase()))),visible=computed(()=>filtered.value.slice(page.value*30,(page.value+1)*30))
async function refresh(){checking.value=true;try{members.value=await service.catalog();unavailable.value=service.unavailable;publishedStudyEmpty.value=service.publishedStudyEmpty;bundles.value=await savedOfflineBundles()}catch{failed.value=true}finally{checking.value=false}}
function recipe(atlas:boolean){if(!bible.value||!atlas&&!strong.value)return;selected.value=[bible.value,atlas?'atlas:openbible-v1':strong.value]}
async function save(){if(locked.value||!name.value.trim()||!selected.value.length)return;try{await saveOfflineBundle({id:crypto.randomUUID(),name:name.value,items:selected.value});name.value='';bundles.value=await savedOfflineBundles()}catch{failed.value=true}}
async function run(update=false){if(locked.value||!selected.value.length)return;busy.value=true;emit('busyChange',true);failed.value=false;controller=new AbortController();try{await service.run(selected.value,p=>progress.value=p,controller.signal,update)}catch{if(!controller.signal.aborted)failed.value=true}finally{await refresh();busy.value=false;emit('busyChange',false);emit('changed')}}
async function remove(){if(locked.value||!window.confirm(text.value.confirm))return;busy.value=true;emit('busyChange',true);try{await service.remove(selected.value);await refresh();emit('changed')}catch{failed.value=true}finally{busy.value=false;emit('busyChange',false)}}
async function forget(id:string){try{await forgetOfflineBundle(id);bundles.value=await savedOfflineBundles()}catch{failed.value=true}}
onMounted(refresh);onBeforeUnmount(()=>controller?.abort())
</script>
<template><section class="offline-bundles"><h2>{{text.title}}</h2><p>{{text.intro}}</p><p v-if="unavailable">{{text.unavailable}}</p><p v-if="publishedStudyEmpty">{{text.emptyStudy}}</p><button :disabled="checking||locked" @click="refresh">{{text.check}}</button>
<label>{{text.bible}}<select v-model="bible" :disabled="locked"><option value="">—</option><option v-for="m in members.filter(m=>m.kind==='bible')" :key="m.id" :value="m.id">{{readingText(m.name)}}</option></select></label>
<label>{{text.strong}}<select v-model="strong" :disabled="locked"><option value="">—</option><option v-for="m in members.filter(m=>m.kind==='strong')" :key="m.id" :value="m.id">{{readingText(m.name)}}</option></select></label>
<button :disabled="locked||!bible" @click="recipe(true)">{{text.atlas}}</button><button :disabled="locked||!bible||!strong" @click="recipe(false)">{{text.lexicon}}</button>
<label>{{text.search}}<input v-model="query" @input="page=0"></label><fieldset :disabled="locked"><label v-for="m in visible" :key="m.id" class="bundle-member"><input v-model="selected" type="checkbox" :value="m.id">{{readingText(m.name)}} · {{text.kinds[m.kind]}} · {{text.states[m.state]}} {{m.update?text.update:''}}<small v-if="m.bytes!==null">{{(m.bytes/1048576).toFixed(1)}} MB</small></label></fieldset>
<nav><button :disabled="!page" @click="page--">←</button>{{page*30+visible.length}} / {{filtered.length}}<button :disabled="(page+1)*30>=filtered.length" @click="page++">→</button></nav>
<p>{{text.needed}} {{(neededZipBytes/1048576).toFixed(1)}} MB</p><p>{{text.known}}: {{(size.known/1048576).toFixed(1)}} MB</p><p v-if="size.estimated">{{text.estimated}}: {{(size.estimated/1048576).toFixed(1)}} MB</p><p v-if="size.unknown">{{text.unknown}}: {{chosen.filter(m=>m.bytes===null&&!m.estimate).map(m=>readingText(m.name)).join(', ')}}</p>
<p v-if="selected.length">{{chosen.length===selected.length&&chosen.length&&chosen.every(m=>m.ready)?text.ready:text.partial}} · {{chosen.filter(m=>m.ready).length}} / {{selected.length}}</p><p v-else>{{text.empty}}</p>
<label>{{text.name}}<input v-model="name" maxlength="80" :disabled="locked"></label><button :disabled="locked||!name.trim()||!selected.length" @click="save">{{text.save}}</button>
<article v-for="bundle in bundles" :key="bundle.id"><button :disabled="locked" @click="selected=[...bundle.items]">{{bundle.name}}</button><span>{{bundle.items.filter(id=>members.some(m=>m.id===id&&m.ready)).length}} / {{bundle.items.length}}</span><button :disabled="locked" @click="forget(bundle.id)">{{text.forget}}</button></article>
<button :disabled="locked||!selected.length" @click="run()">{{text.download}}</button><button :disabled="locked||!selected.length" @click="run(true)">{{text.update}}</button><button :disabled="locked||!selected.length" @click="remove">{{text.remove}}</button><button v-if="busy" @click="controller?.abort()">{{text.cancel}}</button>
<div v-if="progress" role="status"><p>{{members.find(m=>m.id===progress?.member)?.name}} {{progress.chapter?.bookName}} {{progress.chapter?.chapter}} · {{progress.completed}} / {{progress.total}}</p><progress :value="progress.completed+progress.fraction" :max="progress.total"/></div><p v-if="failed" role="alert">{{text.failed}}</p></section></template>
<style scoped>
.offline-bundles{padding:14px;border:1px solid var(--line);border-radius:14px;margin:16px 0;background:var(--white)}
.offline-bundles label{display:block;margin:8px 0}
.offline-bundles fieldset{border:1px solid var(--line);border-radius:10px;margin:12px 0;padding:8px;min-width:0}
.offline-bundles .bundle-member{display:flex;align-items:center;gap:8px;min-height:44px;overflow-wrap:anywhere}
.offline-bundles .bundle-member input[type=checkbox]{width:20px;height:20px;min-height:20px;flex:0 0 20px;margin:0;accent-color:var(--brand-blue)}
.offline-bundles button{margin:4px;min-height:44px;padding:8px 12px;border:1px solid var(--line);border-radius:8px;color:var(--brand-blue);background:var(--brand-surface);white-space:normal}
.offline-bundles button:disabled{cursor:default}
.offline-bundles nav{display:flex;align-items:center;justify-content:space-between}
.offline-bundles select{width:100%;max-width:100%;min-height:44px;border:1px solid var(--line);border-radius:8px;background:var(--white);color:var(--text);padding:8px}
.offline-bundles article{padding:8px 0;border-top:1px solid var(--line);overflow-wrap:anywhere}
.offline-bundles progress{width:100%}
</style>
