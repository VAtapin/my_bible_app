<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { createCalendarMediaCache } from '@/offline/calendarMedia'
import { bundledCalendarAssetUrl } from '@/services/calendarAssets'
const props = defineProps<{ src: string; alt: string }>()
const emit = defineEmits<{ error: [] }>()
const local = ref<string>()
const bundled = computed(() => bundledCalendarAssetUrl(props.src))
let generation = 0
watch(() => props.src, async (src) => {
  const current = ++generation
  if (local.value) URL.revokeObjectURL(local.value)
  local.value = undefined
  try {
    const blob = await createCalendarMediaCache().read(src)
    if (current === generation && blob) local.value = URL.createObjectURL(blob)
  } catch { /* The remote image remains available when local storage is unavailable. */ }
}, { immediate: true })
onUnmounted(() => { generation++; if (local.value) URL.revokeObjectURL(local.value) })
</script>
<template><img :src="bundled || local || src" :alt="alt" @error="emit('error')" /></template>
