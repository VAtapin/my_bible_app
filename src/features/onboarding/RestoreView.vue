<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { remoteProfileApi, type RemoteProfileData } from '@/profile/remoteProfile'
import {
  clearRemoteProfileState,
  parsePersonalRestoreLink,
  saveProfileSecret,
  saveRemoteProfileMeta,
} from '@/profile/profileSync'
import { useProfileStore } from '@/stores/profileStore'

const router = useRouter()
const profile = useProfileStore()
const personalLink = ref('')
const recoveryCode = ref('')
const message = ref('')
const busy = ref(false)

onMounted(async () => {
  if (window.location.hash.includes('profile=')) {
    personalLink.value = window.location.href
    await restoreFromLink()
  }
})

async function restoreFromLink(): Promise<void> {
  const parsed = parsePersonalRestoreLink(personalLink.value)
  if (!parsed) {
    message.value = 'Персональная ссылка имеет неверный формат.'
    return
  }
  await run(async () => {
    const remote = await remoteProfileApi.get(parsed.profileId, parsed.secret)
    applyRemote(remote, parsed.secret)
    message.value = 'Профиль восстановлен по персональной ссылке.'
  })
}

async function restoreFromCode(): Promise<void> {
  if (!recoveryCode.value.trim()) return
  await run(async () => {
    const remote = await remoteProfileApi.recover(recoveryCode.value.trim())
    applyRemote(remote, remote.secret)
    recoveryCode.value = remote.recovery_code
    message.value = `Профиль восстановлен. Новый код: ${remote.recovery_code}`
  }, false)
}

function applyRemote(remote: RemoteProfileData, secret: string): void {
  clearRemoteProfileState()
  profile.save(remote.configuration)
  saveRemoteProfileMeta({ profileId: remote.profile_id, revision: remote.revision })
  saveProfileSecret(secret)
}

async function run(action: () => Promise<void>, navigate = true): Promise<void> {
  busy.value = true
  message.value = ''
  try {
    await action()
    if (navigate) window.setTimeout(() => { void router.push('/today') }, 700)
  } catch (error) {
    message.value = error instanceof Error ? error.message : 'Не удалось восстановить профиль.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <MobileShell :show-navigation="false" back-to="/">
    <section class="simple-page restore-page">
      <img class="simple-page-icon" src="/icons/bookmarks.png" alt="" />
      <p class="eyebrow dark-eyebrow">Восстановление</p>
      <h1>Вернуть моё приложение</h1>
      <p>Используйте персональную ссылку или короткий код. Восстановление заменит локальную конфигурацию этого устройства.</p>

      <section class="restore-card">
        <h2>Персональная ссылка</h2>
        <label><span>Вставьте ссылку</span><input v-model.trim="personalLink" type="url" autocomplete="off" /></label>
        <button class="primary-action" type="button" :disabled="busy || !personalLink" @click="restoreFromLink">Открыть профиль</button>
      </section>

      <div class="restore-divider"><span>или</span></div>

      <section class="restore-card">
        <h2>Код восстановления</h2>
        <label><span>Код из четырёх групп</span><input v-model.trim="recoveryCode" type="text" autocomplete="one-time-code" placeholder="XXXX-XXXX-XXXX-XXXX" /></label>
        <button class="primary-action" type="button" :disabled="busy || !recoveryCode" @click="restoreFromCode">Восстановить и заменить код</button>
      </section>

      <p v-if="message" class="status" role="status">{{ message }}</p>
    </section>
  </MobileShell>
</template>
