<script setup lang="ts">
import { computed, ref } from 'vue'
import { SLAVONIC_NUMERAL_GROUPS, decomposeSlavonicNumeral, toSlavonicNumeral } from '@/domain/slavonicNumerals'
import { useI18n } from '@/i18n'

const { t } = useI18n()
const selectedNumber = ref(12)
const examples = [1, 5, 10, 12, 19, 21, 42, 99, 100, 742, 999]
const validNumber = computed(() => Number.isInteger(selectedNumber.value) && selectedNumber.value >= 1 && selectedNumber.value <= 999)
const numeral = computed(() => validNumber.value ? toSlavonicNumeral(selectedNumber.value) : '—')
const parts = computed(() => validNumber.value ? decomposeSlavonicNumeral(selectedNumber.value) : [])
</script>

<template>
  <main class="page numbers-page">
    <header class="page-header">
      <p class="eyebrow">{{ t('numbers') }}</p>
      <h1>{{ t('numbersTitle') }}</h1>
      <p>{{ t('numbersIntro') }}</p>
    </header>

    <section class="number-builder card">
      <div class="number-input-block">
        <label for="number-input">{{ t('enterNumber') }}</label>
        <input id="number-input" v-model.number="selectedNumber" type="number" inputmode="numeric" min="1" max="999" />
        <div class="number-examples">
          <button v-for="value in examples" :key="value" :class="{ active: selectedNumber === value }" @click="selectedNumber = value">{{ value }}</button>
        </div>
      </div>
      <div class="number-result" aria-live="polite">
        <p>{{ t('slavonicResult') }}</p>
        <strong>{{ numeral }}</strong>
        <span v-if="!validNumber">{{ t('invalidNumber') }}</span>
      </div>
      <div v-if="parts.length" class="number-steps">
        <p class="eyebrow">{{ t('howBuilt') }}</p>
        <div class="step-equation">
          <template v-for="(part, index) in parts" :key="`${part.value}-${index}`">
            <span class="number-part"><b>{{ part.glyph }}</b><small>{{ part.value }}</small></span>
            <i v-if="index < parts.length - 1">+</i>
          </template>
          <i>=</i><span class="number-part result"><b>{{ numeral }}</b><small>{{ selectedNumber }}</small></span>
        </div>
        <p>{{ selectedNumber >= 11 && selectedNumber <= 19 ? t('secondDecadeRule') : t('descendingRule') }}</p>
      </div>
    </section>

    <section class="number-rules">
      <article class="card"><span>1</span><h2>{{ t('numeralRuleOne') }}</h2><p>{{ t('numeralRuleOneText') }}</p></article>
      <article class="card"><span>2</span><h2>{{ t('numeralRuleTwo') }}</h2><p>{{ t('numeralRuleTwoText') }}</p></article>
      <article class="card"><span>3</span><h2>{{ t('numeralRuleThree') }}</h2><p>{{ t('numeralRuleThreeText') }}</p></article>
    </section>

    <section class="numeral-tables">
      <article v-for="group in (['units', 'tens', 'hundreds'] as const)" :key="group" class="card numeral-group">
        <h2>{{ t(group) }}</h2>
        <div><span v-for="part in SLAVONIC_NUMERAL_GROUPS[group]" :key="part.value"><b>{{ part.glyph }}҃</b><small>{{ part.value }}</small></span></div>
      </article>
    </section>
  </main>
</template>
