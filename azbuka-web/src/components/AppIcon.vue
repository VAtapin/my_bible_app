<script setup lang="ts">
defineProps<{ name: 'home' | 'alphabet' | 'numbers' | 'practice' | 'profile' | 'arrow' | 'check' }>()
</script>

<template>
  <svg class="app-icon" viewBox="0 0 24 24" aria-hidden="true">
    <path v-if="name === 'home'" d="m3 11 9-8 9 8v9a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1v-9Z" />
    <path v-else-if="name === 'alphabet'" d="M4 5.5c3.3-1.2 6-.6 8 1.5 2-2.1 4.7-2.7 8-1.5v13c-3.3-.9-6-.4-8 1.5-2-1.9-4.7-2.4-8-1.5v-13Zm8 1.5v13" />
    <path v-else-if="name === 'numbers'" d="M4 6h16M4 12h16M4 18h16M8 4v4m7 2v4m-4 2v4" />
    <path v-else-if="name === 'practice'" d="M5 4h14v16H5V4Zm3 4h8M8 12h5m-5 4h3M16.5 14.5l1 1 2-2" />
    <path v-else-if="name === 'profile'" d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9c.4-4.2 3-6.5 7-6.5s6.6 2.3 7 6.5" />
    <path v-else-if="name === 'arrow'" d="m14.5 5-7 7 7 7" />
    <path v-else d="m5 12 4 4L19 6" />
  </svg>
</template>
