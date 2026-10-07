<script setup lang="ts">
import { computed } from 'vue'
import { prayerBlocks } from '@/services/prayerContent'
const props = defineProps<{ content: string }>()
const blocks = computed(() => prayerBlocks(props.content))
</script>
<template>
  <div class="prayer-content">
    <component :is="block.heading ? 'h2' : 'p'" v-for="(block, index) in blocks" :key="index">
      <span v-for="(segment, part) in block.segments" :key="part" :class="{ 'prayer-emphasis': segment.emphasis, 'prayer-strong': segment.strong }">{{ segment.text }}</span>
    </component>
  </div>
</template>
