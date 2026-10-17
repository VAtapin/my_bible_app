import { readFileSync } from 'node:fs'
import { expect, it } from 'vitest'

it('builds only the main app in the default Plesk deployment', () => {
  const scripts = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8')).scripts as Record<string, string>
  const deploy = readFileSync(new URL('../scripts/deploy-production.sh', import.meta.url), 'utf8')
  const defaultPath = deploy.split('if $publish_azbuka; then')[0]

  expect(scripts['build:app']).toContain('vite build')
  expect(scripts['build:app']).not.toContain('build:azbuka')
  expect(defaultPath).toContain('npm run build:app')
  expect(defaultPath).not.toContain('npm run build:azbuka')
  expect(deploy).toMatch(/if \$publish_azbuka; then\s+npm run build:azbuka/)
})
