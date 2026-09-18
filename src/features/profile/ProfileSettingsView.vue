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

const profile = useProfileStore()
const router = useRouter()
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
    message.value = 'Профиль создан. Сохраните ссылку и новый код восстановления.'
  })
}

async function synchronize(): Promise<void> {
  await run(async () => {
    const result = await flushProfileSyncQueue()
    remoteMeta.value = loadRemoteProfileMeta()
    message.value = result === 'synced'
      ? 'Локальные изменения синхронизированы.'
      : result === 'needs-secret'
        ? 'Откройте персональную ссылку или используйте код восстановления.'
        : 'Нет изменений для отправки.'
  })
}

async function copy(value: string, label: string): Promise<void> {
  await navigator.clipboard.writeText(value)
  message.value = `${label} скопирован.`
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
    message.value = 'Экспорт подготовлен без секретов восстановления.'
  })
}

async function deleteRemote(): Promise<void> {
  if (!remoteMeta.value || !secret.value || !window.confirm('Удалить серверный профиль? Локальная настройка останется на этом устройстве.')) return
  await run(async () => {
    await remoteProfileApi.delete(remoteMeta.value!.profileId, secret.value!)
    clearRemoteProfileState()
    remoteMeta.value = undefined
    secret.value = undefined
    recoveryCode.value = ''
    message.value = 'Серверный профиль удалён. Локальная настройка сохранена.'
  })
}

function deleteLocal(): void {
  if (!window.confirm('Удалить локальный профиль с этого устройства? Загруженные тексты и закладки останутся.')) return
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
    message.value = error instanceof Error ? error.message : 'Операция не выполнена.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <MobileShell back-to="/setup/manual?edit=1">
    <section class="simple-page profile-page">
      <p class="eyebrow dark-eyebrow">Перенос и восстановление</p>
      <h1>Мой профиль</h1>
      <p>Профиль можно использовать без аккаунта. Секрет хранится только до закрытия текущей сессии.</p>

      <section v-if="!remoteMeta" class="profile-panel">
        <h2>Создать персональную ссылку</h2>
        <p>Сервер сохранит конфигурацию, но не тексты чтения и не закладки.</p>
        <button class="primary-action" type="button" :disabled="busy || !profile.configuration" @click="createRemote">Создать защищённый профиль</button>
      </section>

      <template v-else>
        <section class="profile-panel">
          <h2>Синхронизация</h2>
          <dl class="profile-facts">
            <div><dt>ID профиля</dt><dd>{{ remoteMeta.profileId }}</dd></div>
            <div><dt>Ревизия</dt><dd>{{ remoteMeta.revision }}</dd></div>
            <div><dt>Очередь</dt><dd>{{ loadProfileSyncQueue().length }}</dd></div>
          </dl>
          <button class="primary-action" type="button" :disabled="busy" @click="synchronize">Синхронизировать сейчас</button>
        </section>

        <section v-if="restoreLink" class="profile-panel secret-panel">
          <h2>Персональная ссылка</h2>
          <p>Секрет находится после символа # и не отправляется веб-серверу при открытии ссылки.</p>
          <code>{{ restoreLink }}</code>
          <button type="button" @click="copy(restoreLink, 'Ссылка')">Скопировать ссылку</button>
        </section>

        <section v-if="recoveryCode" class="profile-panel secret-panel">
          <h2>Новый код восстановления</h2>
          <code>{{ recoveryCode }}</code>
          <p>Код показывается только сейчас и заменяется после восстановления.</p>
          <button type="button" @click="copy(recoveryCode, 'Код')">Скопировать код</button>
        </section>

        <section v-if="!secret" class="info-panel">Для операций с сервером откройте персональную ссылку или восстановите профиль по коду.</section>

        <section class="profile-actions">
          <button type="button" :disabled="busy || !secret" @click="exportProfile">Экспорт JSON</button>
          <button class="danger-text" type="button" :disabled="busy || !secret" @click="deleteRemote">Удалить с сервера</button>
        </section>
      </template>

      <section class="profile-panel account-panel">
        <h2>Аккаунт — необязательно</h2>
        <p>Привязка к аккаунту появится после выбора общей схемы авторизации Bible Desktop. Профиль и восстановление работают без неё.</p>
      </section>

      <RouterLink class="text-action" to="/privacy">Политика конфиденциальности</RouterLink>

      <button class="text-action danger-text" type="button" @click="deleteLocal">Удалить локальный профиль</button>
      <p v-if="message" class="status" role="status">{{ message }}</p>
    </section>
  </MobileShell>
</template>
