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
import { recordSanitizedError } from '@/diagnostics/productDiagnostics'
import { formatMessage, useI18n } from '@/i18n'

const router = useRouter()
const profile = useProfileStore()
const { messages: text } = useI18n()
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
    message.value = text.value.restoreFlow.invalidLink
    return
  }
  await run(async () => {
    const remote = await remoteProfileApi.get(parsed.profileId, parsed.secret)
    applyRemote(remote, parsed.secret)
    message.value = text.value.restoreFlow.restoredLink
  })
}

async function restoreFromCode(): Promise<void> {
  if (!recoveryCode.value.trim()) return
  await run(async () => {
    const remote = await remoteProfileApi.recover(recoveryCode.value.trim())
    applyRemote(remote, remote.secret)
    recoveryCode.value = remote.recovery_code
    message.value = formatMessage(text.value.restoreFlow.restoredCode, { code: remote.recovery_code })
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
    recordSanitizedError('profile_restore')
    message.value = error instanceof Error ? error.message : text.value.restoreFlow.failed
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <MobileShell :show-navigation="false" back-to="/">
    <section class="simple-page restore-page">
      <img class="simple-page-icon" src="/app-icons/bookmarks.png" alt="" />
      <p class="eyebrow dark-eyebrow">{{ text.restoreFlow.eyebrow }}</p>
      <h1>{{ text.restoreFlow.title }}</h1>
      <p>{{ text.restoreFlow.intro }}</p>

      <section class="restore-card">
        <h2>{{ text.restoreFlow.linkTitle }}</h2>
        <label><span>{{ text.restoreFlow.pasteLink }}</span><input v-model.trim="personalLink" type="url" autocomplete="off" /></label>
        <button class="primary-action" type="button" :disabled="busy || !personalLink" @click="restoreFromLink">{{ text.restoreFlow.openProfile }}</button>
      </section>

      <div class="restore-divider"><span>{{ text.restoreFlow.or }}</span></div>

      <section class="restore-card">
        <h2>{{ text.restoreFlow.codeTitle }}</h2>
        <label><span>{{ text.restoreFlow.codeLabel }}</span><input v-model.trim="recoveryCode" type="text" autocomplete="one-time-code" placeholder="XXXX-XXXX-XXXX-XXXX" /></label>
        <button class="primary-action" type="button" :disabled="busy || !recoveryCode" @click="restoreFromCode">{{ text.restoreFlow.recover }}</button>
      </section>

      <p v-if="message" class="status" role="status">{{ message }}</p>
    </section>
  </MobileShell>
</template>
