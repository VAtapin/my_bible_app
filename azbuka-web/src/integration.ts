let embeddedBase = ''

export function configureAzbukaIntegration(base: string): void {
  embeddedBase = base.replace(/\/$/, '')
}

export function isAzbukaEmbedded(): boolean {
  return embeddedBase !== ''
}

export function azbukaPath(path: string): string {
  return `${embeddedBase}${path === '/' ? '' : path}` || '/'
}

export function azbukaIcon(): string {
  return embeddedBase ? '/brand/app-icon-512.png' : '/icon.svg'
}
