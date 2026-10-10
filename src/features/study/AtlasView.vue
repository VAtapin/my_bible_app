<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import DictionariesView from './DictionariesView.vue'
const route=useRoute(),context=computed(()=>Boolean(route.query.verses||route.query.q)&&!route.query.module)
</script>
<template><DictionariesView atlas-only :initial-map="context"/></template>
