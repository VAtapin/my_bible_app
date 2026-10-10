<script setup lang="ts">
import { computed,ref,watch } from 'vue'
import { installedDictionaryModules } from '@/services/installedDictionaries'
import { searchInstalledDictionaries } from '@/services/dictionarySearch'
import type { DictionaryModule,DictionaryPage } from '@/api/dictionaries'
import { useI18n } from '@/i18n'
import { dictionaryMessages } from '@/i18n/dictionaries'
import { prayerExcerpt } from '@/services/prayerContent'
import { searchSegments } from '@/services/verseSearch'
const props=defineProps<{initialQuery?:string}>(),{language,messages}=useI18n(),text=computed(()=>dictionaryMessages[language.value]),modules=ref<DictionaryModule[]>([]),chosen=ref<string[]>([]),query=ref(props.initialQuery??''),submitted=ref(''),offset=ref(0),page=ref<DictionaryPage>(),busy=ref(false),failed=ref(false)
void installedDictionaryModules().then(value=>{modules.value=value.filter(m=>m.entries_count>0);chosen.value=modules.value.map(m=>m.code);if(query.value.trim()&&modules.value.length)submitted.value=query.value})
watch(chosen,()=>offset.value=0,{deep:true})
watch([submitted,offset,chosen],async(_,__,cleanup)=>{let stale=false;cleanup(()=>stale=true);if(!submitted.value.trim()||!chosen.value.length){page.value=undefined;return}busy.value=true;failed.value=false;try{const value=await searchInstalledDictionaries(submitted.value,chosen.value,offset.value);if(!stale)page.value=value}catch{if(!stale)failed.value=true}finally{if(!stale)busy.value=false}},{deep:true})
</script>
<template><section v-if="modules.length"><h2>{{ text.installedSearch }}</h2><details><summary>{{ text.sources }} · {{ chosen.length }}/{{ modules.length }}</summary><button @click="chosen=modules.map(m=>m.code)">{{ text.all }}</button><label v-for="module in modules" :key="module.code"><input v-model="chosen" type="checkbox" :value="module.code">{{ prayerExcerpt(module.name) }}</label></details><form @submit.prevent="offset=0;submitted=query"><input v-model="query" :aria-label="text.search"><button :disabled="busy||!query.trim()||!chosen.length">{{ text.search }}</button></form><p v-if="busy" role="status">{{ messages.loading }}</p><p v-if="failed" role="alert">{{ text.error }}</p><RouterLink v-for="entry in page?.data" :key="`${entry.module_code}:${entry.key}`" class="module-card" :to="{path:'/dictionaries',query:{module:entry.module_code,entry:entry.key,q:submitted}}"><span v-for="(part,index) in searchSegments(prayerExcerpt(entry.topic),submitted,'partial')" :key="index"><mark v-if="part.match">{{ part.text }}</mark><template v-else>{{ part.text }}</template></span><small> · {{ prayerExcerpt(entry.module_name??'') }}</small></RouterLink><nav v-if="page"><button :disabled="busy||!offset" @click="offset=Math.max(0,offset-50)">{{ text.back }}</button><span>{{ offset+page.data.length }}/{{ page.total }}</span><button :disabled="busy||offset+page.data.length>=page.total" @click="offset+=50">{{ text.more }}</button></nav></section></template>
<style scoped>label,.module-card{display:block;padding:8px}.module-card{margin:8px 0}details{max-height:280px;overflow:auto}nav,form{display:flex;gap:8px;margin:12px 0}nav{justify-content:space-between}</style>
