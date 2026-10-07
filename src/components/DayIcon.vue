<script setup lang="ts">
import { ref } from 'vue'
import type { CalendarIcon } from '@/api/contracts'
import { useI18n } from '@/i18n'
defineProps<{ icon: CalendarIcon }>()
const { messages: text } = useI18n()
const dialog = ref<HTMLDialogElement>()
const failed = ref(false)
</script>
<template>
  <button v-if="!failed && icon.image_url" type="button" class="day-icon-button" :aria-label="`${text.calendar.openIcon}: ${icon.title}`" @click="dialog?.showModal()">
    <img :src="icon.image_url" :alt="icon.title" @error="failed = true" />
  </button>
  <dialog ref="dialog" class="icon-dialog" :aria-label="icon.title" @click="($event.target === dialog) && dialog?.close()">
    <div class="icon-viewer">
      <header><strong>{{ icon.title }}</strong><button type="button" :aria-label="text.calendar.close" @click="dialog?.close()">×</button></header>
      <img v-if="icon.image_url" :src="icon.image_url" :alt="icon.title" />
      <small v-if="icon.credit">{{ icon.credit }}</small>
    </div>
  </dialog>
</template>
