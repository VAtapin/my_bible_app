export const productMetricIds = [
  'app_opened',
  'constructor_opened',
  'constructor_completed',
  'profile_created',
  'offline_download_completed',
  'notification_schedule_saved',
] as const

export const diagnosticErrorIds = [
  'unexpected',
  'offline_download',
  'profile_restore',
  'profile_sync',
  'notification_schedule',
] as const

export type ProductMetricId = typeof productMetricIds[number]
export type DiagnosticErrorId = typeof diagnosticErrorIds[number]

interface Counter {
  count: number
  lastAt: string
}

export interface ProductDiagnostics {
  version: 1
  metrics: Partial<Record<ProductMetricId, Counter>>
  errors: Partial<Record<DiagnosticErrorId, Counter>>
}

const storageKey = 'bible-desktop:product-diagnostics:v1'

export function recordProductMetric(id: ProductMetricId, now = new Date()): void {
  update('metrics', id, now)
}

export function recordSanitizedError(id: DiagnosticErrorId, now = new Date()): void {
  update('errors', id, now)
}

export function loadProductDiagnostics(): ProductDiagnostics {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(storageKey) ?? 'null')
    if (!isDiagnostics(value)) return emptyDiagnostics()
    return value
  } catch {
    return emptyDiagnostics()
  }
}

export function clearProductDiagnostics(): void {
  localStorage.removeItem(storageKey)
}

export function installGlobalErrorDiagnostics(): void {
  window.addEventListener('error', () => recordSanitizedError('unexpected'))
  window.addEventListener('unhandledrejection', () => recordSanitizedError('unexpected'))
}

function update(section: 'metrics' | 'errors', id: ProductMetricId | DiagnosticErrorId, now: Date): void {
  const current = loadProductDiagnostics()
  const counter = current[section][id as never] as Counter | undefined
  current[section][id as never] = {
    count: (counter?.count ?? 0) + 1,
    lastAt: now.toISOString(),
  } as never
  localStorage.setItem(storageKey, JSON.stringify(current))
}

function emptyDiagnostics(): ProductDiagnostics {
  return { version: 1, metrics: {}, errors: {} }
}

function isDiagnostics(value: unknown): value is ProductDiagnostics {
  return typeof value === 'object' && value !== null
    && (value as ProductDiagnostics).version === 1
    && isCounterRecord((value as ProductDiagnostics).metrics, productMetricIds)
    && isCounterRecord((value as ProductDiagnostics).errors, diagnosticErrorIds)
}

function isCounterRecord(value: unknown, allowed: readonly string[]): boolean {
  if (typeof value !== 'object' || value === null) return false
  return Object.entries(value).every(([key, counter]) => allowed.includes(key)
    && typeof counter === 'object'
    && counter !== null
    && Number.isInteger((counter as Counter).count)
    && (counter as Counter).count > 0
    && typeof (counter as Counter).lastAt === 'string')
}
