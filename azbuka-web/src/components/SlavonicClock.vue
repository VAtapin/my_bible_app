<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { toSlavonicClockValue } from '@/domain/slavonicNumerals'
import { useI18n } from '@/i18n'

const { t } = useI18n()
const now = ref(new Date())
let timer: number | null = null

const slavonicTime = computed(() => toSlavonicClockValue(now.value.getHours(), now.value.getMinutes(), now.value.getSeconds()))
const familiarTime = computed(() => [now.value.getHours(), now.value.getMinutes(), now.value.getSeconds()].map((part) => String(part).padStart(2, '0')).join(':'))

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
      <h2>{{ t('clockNow') }}</h2>
      <p>{{ t('clockIntro') }}</p>
    </div>
    <div class="clock-face">
      <div class="clock-slavonic" :aria-label="`${slavonicTime.hours} ${slavonicTime.minutes} ${slavonicTime.seconds}`">
        <span>{{ slavonicTime.hours }}</span><b>:</b><span>{{ slavonicTime.minutes }}</span><b>:</b><span :key="slavonicTime.seconds" class="clock-seconds">{{ slavonicTime.seconds }}</span>
      </div>
      <div class="clock-familiar"><strong>{{ familiarTime }}</strong><span>{{ t('clockReference') }}</span></div>
      <small>{{ t('clockZeroNote') }}</small>
    </div>
  </section>
</template>
