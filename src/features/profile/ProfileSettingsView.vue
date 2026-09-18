<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { remoteProfileApi } from '@/profile/remoteProfile'
import {
  clearRemoteProfileState,
  flushProfileSyncQueue,
  loadProfileSecret,
  loadProfileSyncQueue,
  loadRemoteProfileMeta,
  personalRestoreLink,
  saveProfileSecret,
  saveRemoteProfileMeta,
} from '@/profile/profileSync'
import { useProfileStore } from '@/stores/profileStore'
import { recordProductMetric, recordSanitizedError } from '@/diagnostics/productDiagnostics'
import { formatMessage, useI18n } from '@/i18n'

const profile = useProfileStore()
const router = useRouter()
const { messages: text } = useI18n()
const remoteMeta = ref(loadRemoteProfileMeta())
const secret = ref(loadProfileSecret())
const recoveryCode = ref('')
const message = ref('')
const busy = ref(false)
const restoreLink = computed(() => remoteMeta.value && secret.value
  ? personalRestoreLink(remoteMeta.value.profileId, secret.value)
  : '')

onMounted(() => { profile.load() })

async function createRemote(): Promise<void> {
  if (!profile.configuration) return
  await run(async () => {
    const created = await remoteProfileApi.create(profile.configuration!)
    remoteMeta.value = { profileId: created.profile_id, revision: created.revision }
    secret.value = created.secret
    recoveryCode.value = created.recovery_code
    recordProductMetric('profile_created')
    saveRemoteProfileMeta(remoteMeta.value)
    saveProfileSecret(created.secret)
    message.value = text.value.profile.created
  })
}

async function synchronize(): Promise<void> {
  await run(async () => {
    const result = await flushProfileSyncQueue()
    remoteMeta.value = loadRemoteProfileMeta()
    message.value = result === 'synced'
      ? text.value.profile.synced
      : result === 'needs-secret'
        ? text.value.profile.credentialsNeeded
        : text.value.profile.noChanges
  })
}

async function copy(value: string, label: string): Promise<void> {
  await navigator.clipboard.writeText(value)
  message.value = formatMessage(text.value.profile.copied, { label })
}

async function exportProfile(): Promise<void> {
  if (!remoteMeta.value || !secret.value) return
  await run(async () => {
    const data = await remoteProfileApi.export(remoteMeta.value!.profileId, secret.value!)
    const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `bible-desktop-profile-${remoteMeta.value!.profileId}.json`
    anchor.click()
    URL.revokeObjectURL(url)
    message.value = text.value.profile.exportReady
  })
}

async function deleteRemote(): Promise<void> {
  if (!remoteMeta.value || !secret.value || !window.confirm(text.value.profile.deleteRemoteConfirm)) return
  await run(async () => {
    await remoteProfileApi.delete(remoteMeta.value!.profileId, secret.value!)
    clearRemoteProfileState()
    remoteMeta.value = undefined
    secret.value = undefined
    recoveryCode.value = ''
    message.value = text.value.profile.remoteDeleted
  })
}

function deleteLocal(): void {
  if (!window.confirm(text.value.profile.deleteLocalConfirm)) return
  profile.remove()
  clearRemoteProfileState()
  void router.push('/')
}

async function run(action: () => Promise<void>): Promise<void> {
  busy.value = true
  message.value = ''
  try {
    await action()
  } catch (error) {
    recordSanitizedError('profile_sync')
    message.value = error instanceof Error ? error.message : text.value.profile.failed
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <MobileShell back-to="/setup/manual?edit=1">
    <section class="simple-page profile-page">
      <p class="eyebrow dark-eyebrow">{{ text.profile.eyebrow }}</p>
      <h1>{{ text.profile.title }}</h1>
      <p>{{ text.profile.intro }}</p>

      <section v-if="!remoteMeta" class="profile-panel">
        <h2>{{ text.profile.createTitle }}</h2>
        <p>{{ text.profile.createIntro }}</p>
        <button class="primary-action" type="button" :disabled="busy || !profile.configuration" @click="createRemote">{{ text.profile.create }}</button>
      </section>

      <template v-else>
        <section class="profile-panel">
          <h2>{{ text.profile.syncTitle }}</h2>
          <dl class="profile-facts">
            <div><dt>{{ text.profile.profileId }}</dt><dd>{{ remoteMeta.profileId }}</dd></div>
            <div><dt>{{ text.profile.revision }}</dt><dd>{{ remoteMeta.revision }}</dd></div>
            <div><dt>{{ text.profile.queue }}</dt><dd>{{ loadProfileSyncQueue().length }}</dd></div>
          </dl>
          <button class="primary-action" type="button" :disabled="busy" @click="synchronize">{{ text.profile.sync }}</button>
        </section>

        <section v-if="restoreLink" class="profile-panel secret-panel">
          <h2>{{ text.profile.linkTitle }}</h2>
          <p>{{ text.profile.linkIntro }}</p>
          <code>{{ restoreLink }}</code>
          <button type="button" @click="copy(restoreLink, text.profile.linkTitle)">{{ text.profile.copyLink }}</button>
        </section>

        <section v-if="recoveryCode" class="profile-panel secret-panel">
          <h2>{{ text.profile.recoveryTitle }}</h2>
          <code>{{ recoveryCode }}</code>
          <p>{{ text.profile.recoveryIntro }}</p>
          <button type="button" @click="copy(recoveryCode, text.profile.recoveryTitle)">{{ text.profile.copyCode }}</button>
        </section>

        <section v-if="!secret" class="info-panel">{{ text.profile.secretNeeded }}</section>

        <section class="profile-actions">
          <button type="button" :disabled="busy || !secret" @click="exportProfile">{{ text.profile.export }}</button>
          <button class="danger-text" type="button" :disabled="busy || !secret" @click="deleteRemote">{{ text.profile.deleteRemote }}</button>
        </section>
      </template>

      <section class="profile-panel account-panel">
        <h2>{{ text.profile.accountTitle }}</h2>
        <p>{{ text.profile.accountIntro }}</p>
      </section>

      <RouterLink class="text-action" to="/privacy">{{ text.profile.privacy }}</RouterLink>

      <button class="text-action danger-text" type="button" @click="deleteLocal">{{ text.profile.deleteLocal }}</button>
      <p v-if="message" class="status" role="status">{{ message }}</p>
    </section>
  </MobileShell>
</template>
