<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from '@/i18n'
import {sourceInfo} from '@/services/sourceInfo'
import {sourceInfoMessages} from '@/i18n/sourceInfo'
const props=defineProps<{metadata:unknown;version?:string;capabilities?:string[]}>(),{language}=useI18n()
const text=computed(()=>sourceInfoMessages[language.value]),info=computed(()=>sourceInfo(props.metadata,props.version))
const fields=['name','shortName','language','author','edition','source','version','updated'] as const
</script>
<template><details class="source-card"><summary>{{text.title}}</summary><dl><template v-for="field in fields" :key="field"><dt>{{text[field]}}</dt><dd>{{info[field]||text.unknown}}</dd></template><dt>{{text.capabilities}}</dt><dd>{{(capabilities??info.capabilities).join(' · ')||text.unknown}}</dd></dl></details></template>
<style scoped>.source-card{padding:12px;border:1px solid var(--muted);border-radius:10px;background:var(--white)}.source-card summary{font-weight:700;cursor:pointer}.source-card dl{display:grid;grid-template-columns:minmax(110px,1fr) 2fr;gap:8px}.source-card dt{font-weight:600}.source-card dd{margin:0;overflow-wrap:anywhere}</style>
