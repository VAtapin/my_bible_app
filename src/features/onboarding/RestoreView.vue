<script setup lang="ts">
import MobileShell from '@/components/MobileShell.vue'
import { ru } from '@/i18n/ru'
</script>

<template>
  <MobileShell :show-navigation="false" back-to="/">
    <section class="simple-page">
      <img class="simple-page-icon" src="/icons/bookmarks.png" alt="" />
      <p class="eyebrow dark-eyebrow">{{ ru.restore.eyebrow }}</p>
      <h1>{{ ru.restore.title }}</h1>
      <p>{{ ru.restore.description }}</p>
      <div class="info-panel">{{ ru.restore.notice }}</div>
    </section>
  </MobileShell>
</template>
