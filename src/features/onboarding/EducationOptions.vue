<script setup lang="ts">
import { useI18n } from '@/i18n'
import type { EducationPluginId } from '@/profile/configuration'
import { learningApps } from '@/features/education/learningApps'
const pluginIds = defineModel<EducationPluginId[]>('pluginIds', { required: true })
const showClock = defineModel<boolean>('showClock', { required: true })
const showProgress = defineModel<boolean>('showProgress', { required: true })
const { messages: text } = useI18n()
function toggle(id: EducationPluginId): void {
  pluginIds.value = pluginIds.value.includes(id) ? pluginIds.value.filter((value) => value !== id) : [...pluginIds.value, id]
}
</script>
<template>
  <div class="option-group">
    <h3>{{ text.education.title }}</h3>
    <label v-for="app in learningApps" :key="app.id" class="toggle-row">
      <span><strong>{{ text.education.apps[app.id].title }}</strong><small>{{ text.education.apps[app.id].description }}</small></span>
      <input type="checkbox" :checked="pluginIds.includes(app.id)" @change="toggle(app.id)" />
    </label>
    <template v-if="pluginIds.includes('azbuka')">
      <label class="toggle-row"><span>{{ text.education.showClock }}</span><input v-model="showClock" type="checkbox" /></label>
      <label class="toggle-row"><span>{{ text.education.showProgress }}</span><input v-model="showProgress" type="checkbox" /></label>
    </template>
  </div>
</template>
