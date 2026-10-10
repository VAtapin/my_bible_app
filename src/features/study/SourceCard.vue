<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from '@/i18n'
import {sourceInfoRows} from '@/services/sourceInfo'
import {sourceInfoMessages} from '@/i18n/sourceInfo'
const props=defineProps<{metadata:unknown;version?:string;capabilities?:string[];expanded?:boolean}>(),{language}=useI18n()
const text=computed(()=>sourceInfoMessages[language.value]),rows=computed(()=>sourceInfoRows(props.metadata,props.version,props.capabilities))
</script>
<template><details v-if="rows.length" class="source-card" :open="expanded"><summary>{{text.title}}</summary><dl><template v-for="row in rows" :key="row.field"><dt>{{text[row.field]}}</dt><dd>{{row.value}}</dd></template></dl></details></template>
<style scoped>.source-card{padding:12px;border:1px solid var(--muted);border-radius:10px;background:var(--white)}.source-card summary{font-weight:700;cursor:pointer}.source-card dl{display:grid;grid-template-columns:minmax(110px,1fr) 2fr;gap:8px}.source-card dt{font-weight:600}.source-card dd{margin:0;overflow-wrap:anywhere}</style>
