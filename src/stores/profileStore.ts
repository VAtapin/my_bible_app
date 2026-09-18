import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AppConfiguration } from '@/profile/configuration'
import { createLocalProfileRepository } from '@/profile/profileRepository'
import { enqueueProfileSync } from '@/profile/profileSync'
import { setInterfaceLanguage } from '@/i18n'

export const useProfileStore = defineStore('profile', () => {
  const configuration = ref<AppConfiguration>()
  const loaded = ref(false)

  function repository() {
    return createLocalProfileRepository(window.localStorage)
  }

  function load(): AppConfiguration | undefined {
    configuration.value = repository().load()
    if (configuration.value) setInterfaceLanguage(configuration.value.interfaceLanguage)
    loaded.value = true
    return configuration.value
  }

  function save(value: AppConfiguration): void {
    repository().save(value)
    setInterfaceLanguage(value.interfaceLanguage)
    enqueueProfileSync(value)
    configuration.value = value
    loaded.value = true
  }

  function remove(): void {
    repository().remove()
    configuration.value = undefined
    loaded.value = true
  }

  return { configuration, loaded, load, save, remove }
})
