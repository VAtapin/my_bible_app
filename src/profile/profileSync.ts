import type { AppConfiguration } from './configuration'
import { remoteProfileApi } from './remoteProfile'

export interface RemoteProfileMeta {
  profileId: string
  revision: number
}

export interface ProfileSyncOperation {
  id: string
  profileId: string
  baseRevision: number
  configuration: AppConfiguration
  createdAt: string
}

const metaKey = 'bible-desktop:remote-profile:v1'
const queueKey = 'bible-desktop:profile-sync:v1'
const secretKey = 'bible-desktop:remote-secret:v1'

export function loadRemoteProfileMeta(): RemoteProfileMeta | undefined {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(metaKey) ?? 'null')
    return typeof value === 'object' && value !== null
      && typeof (value as RemoteProfileMeta).profileId === 'string'
      && typeof (value as RemoteProfileMeta).revision === 'number'
      ? value as RemoteProfileMeta
      : undefined
  } catch {
    return undefined
  }
}

export function saveRemoteProfileMeta(meta: RemoteProfileMeta): void {
  localStorage.setItem(metaKey, JSON.stringify(meta))
}

export function saveProfileSecret(secret: string): void {
  sessionStorage.setItem(secretKey, secret)
}

export function loadProfileSecret(): string | undefined {
  return sessionStorage.getItem(secretKey) ?? undefined
}

export function enqueueProfileSync(configuration: AppConfiguration): void {
  const meta = loadRemoteProfileMeta()
  if (!meta) return
  const operation: ProfileSyncOperation = {
    id: crypto.randomUUID(),
    profileId: meta.profileId,
    baseRevision: meta.revision,
    configuration,
    createdAt: new Date().toISOString(),
  }
  localStorage.setItem(queueKey, JSON.stringify([operation]))
}

export function loadProfileSyncQueue(): ProfileSyncOperation[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(queueKey) ?? '[]')
    return Array.isArray(value) ? value.filter(isSyncOperation) : []
  } catch {
    return []
  }
}

export async function flushProfileSyncQueue(): Promise<'synced' | 'empty' | 'needs-secret'> {
  const operation = loadProfileSyncQueue()[0]
  if (!operation) return 'empty'
  const secret = loadProfileSecret()
  if (!secret) return 'needs-secret'
  const remote = await remoteProfileApi.update(
    operation.profileId,
    secret,
    operation.baseRevision,
    operation.configuration,
  )
  saveRemoteProfileMeta({ profileId: remote.profile_id, revision: remote.revision })
  localStorage.removeItem(queueKey)
  return 'synced'
}

export function clearRemoteProfileState(): void {
  localStorage.removeItem(metaKey)
  localStorage.removeItem(queueKey)
  sessionStorage.removeItem(secretKey)
}

export function personalRestoreLink(profileId: string, secret: string): string {
  const url = new URL('/restore', window.location.origin)
  url.hash = new URLSearchParams({ profile: profileId, secret }).toString()
  return url.toString()
}

export function parsePersonalRestoreLink(value: string): { profileId: string; secret: string } | undefined {
  try {
    const url = new URL(value, window.location.origin)
    const params = new URLSearchParams(url.hash.replace(/^#/, ''))
    const profileId = params.get('profile')
    const secret = params.get('secret')
    return profileId && secret ? { profileId, secret } : undefined
  } catch {
    return undefined
  }
}

function isSyncOperation(value: unknown): value is ProfileSyncOperation {
  return typeof value === 'object' && value !== null
    && typeof (value as ProfileSyncOperation).id === 'string'
    && typeof (value as ProfileSyncOperation).profileId === 'string'
    && typeof (value as ProfileSyncOperation).baseRevision === 'number'
    && typeof (value as ProfileSyncOperation).configuration === 'object'
}
