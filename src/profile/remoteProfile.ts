import { isAppConfiguration, type AppConfiguration } from './configuration'

export interface RemoteProfileData {
  profile_id: string
  schema_version: number
  revision: number
  configuration: AppConfiguration
  created_at: string
  updated_at: string
}

export interface RemoteProfileCredentials extends RemoteProfileData {
  secret: string
  recovery_code: string
}

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? 'https://bible-desktop.com/api').replace(/\/$/, '')

export const remoteProfileApi = {
  create(configuration: AppConfiguration): Promise<RemoteProfileCredentials> {
    return request('/v1/profiles', {
      method: 'POST',
      body: JSON.stringify({ schema_version: 1, configuration }),
    }, isRemoteProfileCredentials)
  },
  get(profileId: string, secret: string): Promise<RemoteProfileData> {
    return request(`/v1/profiles/${encodeURIComponent(profileId)}`, {
      headers: { 'X-Profile-Secret': secret },
    }, isRemoteProfileData)
  },
  update(profileId: string, secret: string, revision: number, configuration: AppConfiguration): Promise<RemoteProfileData> {
    return request(`/v1/profiles/${encodeURIComponent(profileId)}`, {
      method: 'PUT',
      headers: { 'X-Profile-Secret': secret },
      body: JSON.stringify({ schema_version: 1, revision, configuration }),
    }, isRemoteProfileData)
  },
  recover(recoveryCode: string): Promise<RemoteProfileCredentials> {
    return request('/v1/profiles/recover', {
      method: 'POST',
      body: JSON.stringify({ recovery_code: recoveryCode }),
    }, isRemoteProfileCredentials)
  },
  export(profileId: string, secret: string): Promise<unknown> {
    return request(`/v1/profiles/${encodeURIComponent(profileId)}/export`, {
      headers: { 'X-Profile-Secret': secret },
    }, () => true)
  },
  async delete(profileId: string, secret: string): Promise<void> {
    const response = await fetch(`${apiBaseUrl}/v1/profiles/${encodeURIComponent(profileId)}`, {
      method: 'DELETE',
      headers: { Accept: 'application/json', 'X-Profile-Secret': secret },
    })
    if (!response.ok && response.status !== 204) throw new Error(`API вернуло статус ${response.status}.`)
  },
}

async function request<T>(
  path: string,
  options: RequestInit,
  validate: (value: unknown) => value is T,
): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body) headers.set('Content-Type', 'application/json')
  const response = await fetch(`${apiBaseUrl}${path}`, { ...options, headers })
  const payload: unknown = await response.json().catch(() => undefined)
  if (!response.ok) {
    if (response.status === 409) throw new Error('Конфигурация изменилась на другом устройстве. Сначала восстановите свежую версию.')
    throw new Error(response.status === 401 ? 'Секрет профиля недействителен.' : `API вернуло статус ${response.status}.`)
  }
  const data = payload && typeof payload === 'object' && 'data' in payload ? payload.data : undefined
  if (!validate(data)) throw new Error('API вернуло ответ неизвестного формата.')
  return data
}

function isRemoteProfileData(value: unknown): value is RemoteProfileData {
  return typeof value === 'object' && value !== null
    && typeof (value as RemoteProfileData).profile_id === 'string'
    && typeof (value as RemoteProfileData).revision === 'number'
    && isAppConfiguration((value as RemoteProfileData).configuration)
}

function isRemoteProfileCredentials(value: unknown): value is RemoteProfileCredentials {
  return isRemoteProfileData(value)
    && typeof (value as RemoteProfileCredentials).secret === 'string'
    && typeof (value as RemoteProfileCredentials).recovery_code === 'string'
}
