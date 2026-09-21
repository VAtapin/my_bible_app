<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { toSlavonicClockValue } from '@/domain/slavonicNumerals'
import { useI18n } from '@/i18n'

const { locale, t } = useI18n()
const now = ref(new Date())
let timer: number | null = null

const slavonicTime = computed(() => toSlavonicClockValue(now.value.getHours(), now.value.getMinutes()))
const familiarTime = computed(() => `${String(now.value.getHours()).padStart(2, '0')}:${String(now.value.getMinutes()).padStart(2, '0')}`)
const dateLabel = computed(() => new Intl.DateTimeFormat(locale.value === 'de' ? 'de-DE' : 'ru-RU', {
  weekday: 'long', day: 'numeric', month: 'long'
}).format(now.value))

onMounted(() => {
  timer = window.setInterval(() => { now.value = new Date() }, 1000)
})

onBeforeUnmount(() => {
  if (timer !== null) window.clearInterval(timer)
})
</script>

<template>
  <section class="slavonic-clock card" :aria-label="t('slavonicClock')">
    <div class="clock-copy">
      <p class="eyebrow">{{ t('slavonicClock') }}</p>
      <h2>{{ dateLabel }}</h2>
      <p>{{ t('clockIntro') }}</p>
    </div>
    <div class="clock-face">
      <div class="clock-slavonic" :aria-label="`${slavonicTime.hours} ${slavonicTime.minutes}`">
        <span>{{ slavonicTime.hours }}</span><b>:</b><span>{{ slavonicTime.minutes }}</span>
      </div>
      <div class="clock-familiar"><strong>{{ familiarTime }}</strong><span>{{ t('clockReference') }}</span></div>
      <small>{{ t('clockZeroNote') }}</small>
    </div>
  </section>
</template>
