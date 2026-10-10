import type { PrayerCatalog, PrayerSummary } from './contracts'

const groups = ['short', 'rules', 'occasions', 'initial']
const record = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const strings = (value: unknown): value is string[] => Array.isArray(value) && value.every(item => typeof item === 'string' && Boolean(item.trim()))
const hash = (value: unknown) => typeof value === 'string' && /^[a-f0-9]{64}$/u.test(value)

export function isPrayerMetadata(value: Record<string, unknown>, required = false): boolean {
  const hasMetadata = required || value.canonical_slug !== undefined || value.liturgical_work_id !== undefined
  if (!hasMetadata) return true
  return typeof value.canonical_slug === 'string' && Boolean(value.canonical_slug.trim())
    && Number.isSafeInteger(value.liturgical_work_id) && Number(value.liturgical_work_id) > 0
    && typeof value.group === 'string' && groups.includes(value.group)
    && strings(value.groups) && value.groups.includes(value.group) && value.groups.every(group => groups.includes(group))
    && strings(value.available_languages) && value.available_languages.includes(String(value.language_code))
    && value.completeness === 'complete' && value.review_status === 'source-verified'
    && hash(value.content_revision) && typeof value.catalog_visible === 'boolean'
}

function isExternalSource(value: unknown): boolean {
  if (!record(value) || typeof value.language !== 'string' || typeof value.title !== 'string' || typeof value.url !== 'string'
    || value.availability !== 'external-only' || value.offline_available !== false) return false
  try {
    const url = new URL(value.url)
    return url.protocol === 'https:' && Boolean(url.hostname) && !url.username && !url.password
  } catch { return false }
}

export function isPrayerCatalog(value: unknown, validateItem: (value: unknown) => value is PrayerSummary): value is PrayerCatalog {
  if (!record(value) || !Array.isArray(value.data) || !value.data.every(validateItem)) return false
  if (value.catalog_version === undefined) return value.groups === undefined && value.external_sources === undefined
  return value.catalog_version === 2 && record(value.groups)
    && Object.entries(value.groups).every(([key, title]) => groups.includes(key) && typeof title === 'string' && Boolean(title.trim()))
    && groups.every(group => Object.hasOwn(value.groups as object, group))
    && Array.isArray(value.external_sources) && value.external_sources.every(isExternalSource)
    && value.data.every(item => record(item) && isPrayerMetadata(item, true))
}
