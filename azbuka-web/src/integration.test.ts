import { afterEach, describe, expect, it } from 'vitest'
import { azbukaIcon, azbukaPath, configureAzbukaIntegration, isAzbukaEmbedded } from './integration'

afterEach(() => configureAzbukaIntegration(''))
describe('shared Azbuka navigation', () => {
  it('keeps standalone routes and assets on the standalone origin', () => {
    configureAzbukaIntegration('')
    expect(isAzbukaEmbedded()).toBe(false)
    expect(azbukaPath('/')).toBe('/')
    expect(azbukaPath('/alphabet/az')).toBe('/alphabet/az')
    expect(azbukaIcon()).toBe('/icon.svg')
  })
  it('keeps all embedded routes within Biblia App', () => {
    configureAzbukaIntegration('/education/azbuka/')
    expect(isAzbukaEmbedded()).toBe(true)
    for (const path of ['/', '/alphabet/az', '/numbers', '/practice/session', '/profile']) {
      expect(azbukaPath(path)).toBe(`/education/azbuka${path === '/' ? '' : path}`)
    }
    expect(azbukaIcon()).toBe('/brand/app-icon-512.png')
  })
})
