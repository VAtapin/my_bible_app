<script setup lang="ts">
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'

const { messages: text } = useI18n()
</script>

<template>
  <MobileShell :show-navigation="false" back-to="/">
    <article class="simple-page legal-page">
      <p class="eyebrow dark-eyebrow">{{ text.privacy.eyebrow }}</p>
      <h1>{{ text.privacy.title }}</h1>
      <p>{{ text.privacy.updated }}</p>

      <section>
        <h2>{{ text.privacy.dataTitle }}</h2>
        <p>{{ text.privacy.dataText }}</p>
      </section>

      <section>
        <h2>{{ text.privacy.profileTitle }}</h2>
        <p>{{ text.privacy.profileText }}</p>
        <p>{{ text.privacy.localText }}</p>
      </section>

      <section>
        <h2>{{ text.privacy.notificationTitle }}</h2>
        <p>{{ text.privacy.notificationText }}</p>
      </section>

      <section>
        <h2>{{ text.privacy.controlTitle }}</h2>
        <p>{{ text.privacy.controlText }}</p>
      </section>

      <section>
        <h2>{{ text.privacy.thirdTitle }}</h2>
        <p>{{ text.privacy.thirdText }}</p>
      </section>

      <section>
        <h2>{{ text.privacy.contactTitle }}</h2>
        <p>{{ text.privacy.contactBefore }} <a href="https://bible-desktop.com/pages/contacts" target="_blank" rel="noopener noreferrer">{{ text.privacy.contactLink }}</a>.</p>
      </section>
    </article>
  </MobileShell>
</template>
