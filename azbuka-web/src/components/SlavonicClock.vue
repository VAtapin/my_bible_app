<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { toSlavonicClockValue } from '../domain/slavonicNumerals'
import { useI18n } from '../i18n'

const { t } = useI18n()
withDefaults(defineProps<{ compact?: boolean; label?: string; showLabel?: boolean }>(), { compact: false, label: undefined, showLabel: true })
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
  <section class="slavonic-clock card" :class="{ 'compact-clock': compact }" :aria-label="label ?? t('slavonicClock')">
    <div v-if="showLabel" class="clock-copy">
      <p class="eyebrow">{{ label ?? t('slavonicClock') }}</p>
    </div>
    <div class="clock-face">
      <div class="clock-slavonic" :aria-label="`${slavonicTime.hours} ${slavonicTime.minutes} ${slavonicTime.seconds}`">
        <span>{{ slavonicTime.hours }}</span><b>:</b><span>{{ slavonicTime.minutes }}</span><b>:</b><span :key="slavonicTime.seconds" class="clock-seconds">{{ slavonicTime.seconds }}</span>
      </div>
      <div class="clock-familiar"><strong>{{ familiarTime }}</strong></div>
    </div>
  </section>
</template>

<style scoped>
@font-face { font-family: Ponomar; src: url('../../public/fonts/Ponomar-Regular.ttf') format('truetype'); font-display: swap; }
.compact-clock { display: flex; flex-direction: column; gap: 3px; border: 0; padding: 0; margin: 0; background: none; box-shadow: none; min-width: 0; }
.compact-clock .clock-copy p { margin: 0; font-size: 9px; letter-spacing: .04em; color: var(--muted); }
.compact-clock .clock-face { text-align: center; }
.compact-clock .clock-slavonic { display: grid; grid-template-columns: 1fr auto 1fr auto 1fr; gap: 2px; font-family: Ponomar, Georgia, serif; font-size: 29px; line-height: 1.1; color: var(--ink); }
.compact-clock .clock-slavonic span { min-width: 1.6em; }
.compact-clock .clock-slavonic b { font-family: Georgia, serif; font-size: 20px; font-weight: 400; color: var(--brand-gold); }
.compact-clock .clock-seconds { color: #3c8a70; }
.compact-clock .clock-familiar { font-size: 11px; color: var(--muted); font-variant-numeric: tabular-nums; }
</style>
