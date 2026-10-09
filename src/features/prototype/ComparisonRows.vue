<script setup lang="ts">
import {computed,nextTick,ref,watch}from'vue'
import type{BibleChapter}from'@/api/contracts'
import{compareVerses}from'@/services/bibleComparison'
import{useI18n}from'@/i18n'
const props=defineProps<{primary:BibleChapter;secondary:BibleChapter;selectedVerse?:number}>()
const emit=defineEmits<{select:[number:number,chapter:BibleChapter]}>()
const{messages:text}=useI18n(),root=ref<HTMLElement>()
const rows=computed(()=>compareVerses(props.primary,props.secondary))
watch(()=>props.selectedVerse,async()=>{await nextTick();root.value?.querySelector(`[data-primary-verse="${props.selectedVerse}"]`)?.scrollIntoView({block:'center'})},{immediate:true})
function font(chapter:BibleChapter){return chapter.translation.language.code==='cu'?'Ponomar, serif':chapter.translation.language.code==='cu-civil'?'"Monomakh Unicode", serif':'Georgia, serif'}
</script>
<template><div ref="root" class="interleaved">
  <div v-for="row in rows" :key="row.reference" class="parallel-row" :data-primary-verse="row.primary?.number" :class="{'selected-verse':row.primary?.number===selectedVerse}">
    <div v-for="(value,index) in [row.primary,row.secondary]" :key="index" class="parallel-verse" :style="{fontFamily:font(index===0?primary:secondary)}">
      <small>{{(index===0?primary:secondary).translation.name}}</small>
      <button v-if="value" class="verse-text" @click="emit('select',value.number,index===0?primary:secondary)"><span class="verse-number">{{value.number}}</span>{{value.plain_text||text.parallel.missing}}</button>
      <p v-else>{{text.parallel.missing}}</p>
    </div>
  </div>
</div></template>
<style scoped>.parallel-row{border-bottom:1px solid var(--line)}.parallel-verse{padding:10px 12px;font-size:var(--reading-size,19px);line-height:1.55;overflow-wrap:anywhere}.parallel-verse p{margin:0}.parallel-verse small{display:block;font:12px sans-serif;margin-bottom:5px}</style>
