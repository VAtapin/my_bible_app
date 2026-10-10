import { describe,expect,it,vi } from 'vitest'
const fixture=vi.hoisted(()=>({pack:{manifest:{id:'Maps',kind:'dictionary',version:'package-version'},metadata:{source_archive_sha256:'source-hash',content_version:'repaired'}}}))
vi.mock('./studyPackageLookup',()=>({installedStudyPackage:async()=>fixture.pack,studyPackageRows:vi.fn(),studyPackageArticle:vi.fn()}))
vi.mock('./studyPackages',()=>({installedStudyPackages:async()=>[fixture.pack],studyPackageFile:vi.fn(),packageTable:vi.fn()}))
import {installedDictionaryArticle,installedDictionaryImage,installedDictionaryModules} from './installedDictionaries'
describe('repaired dictionary revisions',()=>{
 it('publishes the content revision separately from the unchanged archive hash',async()=>{
  expect((await installedDictionaryModules())[0]?.content_version).toBe('repaired')
 })
 it('bypasses stale installed article and image before opening their files',async()=>{
  expect(await installedDictionaryArticle('Maps','article','new-repair')).toBeUndefined()
  expect(await installedDictionaryImage('Maps',578,'new-repair')).toBeUndefined()
 })
})
