<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CalendarIcon } from '@/api/contracts'
import { useI18n } from '@/i18n'
import OfflineImage from './OfflineImage.vue'
import { createIconSwipe } from './iconSwipe'
const props = defineProps<{ icon: CalendarIcon }>()
const { messages: text } = useI18n()
const dialog = ref<HTMLDialogElement>()
const index = ref(0)
const images = computed(() => [...new Set([props.icon.image_url, ...(props.icon.images?.map((image) => image.url) ?? [])].filter((url): url is string => Boolean(url)))])
function move(direction: number): void {
  if (images.value.length > 1) index.value = (index.value + direction + images.value.length) % images.value.length
}
const swipe = createIconSwipe(move)
</script>
<template>
  <button v-if="icon.image_url" type="button" class="day-icon-button" :aria-label="`${text.calendar.openIcon}: ${icon.title}`" @click="dialog?.showModal()">
    <OfflineImage :src="icon.image_url" :alt="icon.title" />
  </button>
  <dialog ref="dialog" class="icon-dialog" :aria-label="icon.title" @close="swipe.cancel" @cancel="swipe.cancel" @click="($event.target === dialog) && dialog?.close()">
    <div class="icon-viewer">
      <header><strong>{{ icon.title }}</strong><button type="button" :aria-label="text.calendar.close" @click="dialog?.close()">×</button></header>
      <OfflineImage v-if="images[index]" class="icon-full-image" :src="images[index]" :alt="icon.title" :draggable="false"
        @pointerdown="images.length > 1 && swipe.start($event)" @pointerup="swipe.end" @pointercancel="swipe.cancel" @lostpointercapture="swipe.cancel" />
      <div v-if="images.length > 1" class="icon-image-navigation"><button type="button" :aria-label="text.calendar.previous" @click="move(-1)">‹</button><small>{{ index + 1 }} / {{ images.length }}</small><button type="button" :aria-label="text.calendar.next" @click="move(1)">›</button></div>
      <small v-for="date in icon.dates" :key="date.label">{{ date.label }}</small>
      <small v-if="icon.credit">{{ icon.credit }}</small>
      <p v-if="icon.description" class="icon-description">{{ icon.description }}</p>
    </div>
  </dialog>
</template>
