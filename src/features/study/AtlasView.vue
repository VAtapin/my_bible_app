<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import GeoAtlas from './GeoAtlas.vue'
const route=useRoute(),references=computed(()=>String(route.query.verses??'').split(',').filter(ref=>/^[A-Za-z0-9]+\.[1-9]\d*\.[1-9]\d*$/.test(ref)))
</script>
<template><MobileShell back-to="/more"><GeoAtlas :references="references" :initial-query="String(route.query.q??'').slice(0,120)"/></MobileShell></template>
