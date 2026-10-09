import { afterEach, describe, expect, it } from 'vitest'
import { execFileSync, spawnSync } from 'node:child_process'
import { chmodSync, copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, unlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const bash = process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : '/bin/bash'
const roots: string[] = []
const shellPath = (path: string) => path.replaceAll('\\', '/').replace(/^([a-z]):/i, (_, drive: string) => `/${drive.toLowerCase()}`)
afterEach(() => {
  for (const root of roots.splice(0)) {
    if (!resolve(root).startsWith(resolve(tmpdir()) + '/')) {
      // Windows uses a backslash separator in resolved paths.
      if (!resolve(root).startsWith(resolve(tmpdir()) + '\\')) throw new Error('Unexpected temporary path')
    }
    rmSync(root, { recursive: true, force: true })
  }
})

/** Run the real Bash script and tar locally; Git/npm are isolated command fixtures. */
describe('production deploy with separate Plesk users', () => {
  function fixture(azbuka = false) {
    const root = mkdtempSync(join(tmpdir(), 'bible-deploy-test-')); roots.push(root)
    const bin = join(root, 'bin'); mkdirSync(bin)
    mkdirSync(join(root, 'scripts')); copyFileSync(resolve('scripts/deploy-production.sh'), join(root, 'scripts/deploy-production.sh'))
    for (const [name, content] of Object.entries({
      node: '#!/usr/bin/env bash\necho "${TEST_NODE_MAJOR:-22}"\n',
      git: '#!/usr/bin/env bash\nprintf "%s\\n" "git $*" >> deploy.calls\nexit "${TEST_GIT_EXIT:-0}"\n',
      npm: '#!/usr/bin/env bash\nprintf "%s\\n" "npm $*" >> deploy.calls\n',
    })) { writeFileSync(join(bin, name), content); chmodSync(join(bin, name), 0o755) }
    mkdirSync(join(root, 'dist/app-icons'), { recursive: true })
    for (const file of ['index.html', 'sw.js', ...['bookmarks', 'calendar', 'library', 'prayers', 'setup'].map(icon => `app-icons/${icon}.png`)]) writeFileSync(join(root, 'dist', file), 'public fixture')
    writeFileSync(join(root, '.env'), 'private fixture outside public build')
    if (azbuka) {
      mkdirSync(join(root, 'azbuka-web/dist'), { recursive: true })
      for (const file of ['index.html', 'sw.js']) writeFileSync(join(root, 'azbuka-web/dist', file), `azbuka ${file}`)
    }
    const run = (args: string[] = [], env: Record<string, string> = {}) => spawnSync(bash,
      ['-c', 'export PATH="$1:$PATH"; bash "$2" "${@:3}"', 'deploy-test', shellPath(bin), shellPath(join(root, 'scripts/deploy-production.sh')), ...args],
      { cwd: root, env: { ...process.env, ...env }, encoding: 'utf8' })
    return { root, run }
  }
  it('updates Bible App without requiring any standalone Azbuka directory', () => {
    const { root, run } = fixture()
    const result = run()
    expect(result.status, result.stderr).toBe(0)
    expect(readFileSync(join(root, 'deploy.calls'), 'utf8').trim().split('\n')).toEqual(['git pull --ff-only', 'npm ci', 'npm run build'])
    expect(existsSync(join(root, 'dist/azbuka-release.tar.gz'))).toBe(false)
    expect(result.stdout).not.toContain('Azbuka archive')
  })
  it('creates only a public archive when standalone publication is explicitly requested', () => {
    const { root, run } = fixture(true)
    const result = run(['--with-azbuka'])
    expect(result.status, result.stderr).toBe(0)
    const archive = shellPath(join(root, 'dist/azbuka-release.tar.gz'))
    const contents = execFileSync(bash, ['-c', 'tar -tzf "$1"', 'archive-test', archive], { encoding: 'utf8' })
    expect(contents).toContain('./index.html'); expect(contents).toContain('./sw.js')
    expect(contents).not.toContain('.env'); expect(contents).not.toContain('scripts/')
    expect(result.stdout).toContain('Import it separately')
  })
  it('stops on a rejected Git update before installing or building', () => {
    const { root, run } = fixture(true)
    expect(run(['--with-azbuka'], { TEST_GIT_EXIT: '1' }).status).toBe(1)
    expect(readFileSync(join(root, 'deploy.calls'), 'utf8').trim()).toBe('git pull --ff-only')
    expect(existsSync(join(root, 'dist/azbuka-release.tar.gz'))).toBe(false)
  })
  it('rejects a missing main artifact', () => {
    const { root, run } = fixture(); unlinkSync(join(root, 'dist/sw.js'))
    expect(run().status).not.toBe(0)
  })
  it('requires the standalone build only for its explicit archive option', () => {
    const { run } = fixture()
    expect(run(['--with-azbuka']).status).not.toBe(0)
  })
  it('rejects unknown options before Git runs', () => {
    const { root, run } = fixture()
    expect(run(['--copy-azbuka']).status).toBe(2)
    expect(existsSync(join(root, 'deploy.calls'))).toBe(false)
  })
})
