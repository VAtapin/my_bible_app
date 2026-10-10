<script setup lang="ts">
import {computed}from'vue'
import {useI18n}from'@/i18n'
import {referenceNumberingMessages}from'@/i18n/referenceNumbering'
import {referenceVersification,verifiedReference,type StudyReferenceTarget}from'@/api/verseStudy'
const props=defineProps<{targets:StudyReferenceTarget[]}>(),{language}=useI18n(),text=computed(()=>referenceNumberingMessages[language.value])
const statuses=computed(()=>[...new Map(props.targets.map(target=>{const value=referenceVersification(target.versification);return[JSON.stringify(value),value]})).values()])
</script>
<template><div class="reference-numbering"><p v-for="status in statuses" :key="JSON.stringify(status)"><strong>{{verifiedReference(status)?text.verified:text[status.status==='verified'?'unknown':status.status]}}</strong> · {{text.source}}: {{status.source_profile??'—'}} · {{text.edition}}: {{status.edition_profile??'—'}}<template v-if="status.map_version"> · {{text.version}}: {{status.map_version}}</template><span v-if="!verifiedReference(status)"> {{text.warning}}</span></p></div></template>
<style scoped>.reference-numbering{font-size:13px;line-height:1.5;padding:6px 0}.reference-numbering p{margin:0}.reference-numbering span{display:block}</style>
