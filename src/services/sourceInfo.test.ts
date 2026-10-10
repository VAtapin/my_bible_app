import {describe,expect,it} from 'vitest'
import {sourceInfo} from './sourceInfo'
import {readerHelpMessages} from '@/i18n/readerHelp'
import {readFileSync} from 'node:fs'
describe('published source metadata and synchronized help',()=>{
 it('does not invent an author, update date, edition or electronic source from a module code',()=>{const info=sourceInfo({code:'A-2026-RU',name:'Published',language_code:'ru',source_archive_sha256:'123'});expect(info.name).toBe('Published');expect(info.language).toBe('ru');expect(info.author).toBeUndefined();expect(info.updated).toBeUndefined();expect(info.edition).toBeUndefined();expect(info.source).toBeUndefined();expect(info.version).toBeUndefined()})
 it('displays source typography as safe plain metadata and does not retain executable text',()=>{expect(sourceInfo({name:'<i>A &amp; B</i>',author:'<script>bad()</script>'}).name).toBe('A & B');expect(sourceInfo({author:'<script>bad()</script>'}).author).toBeUndefined()})
 it('keeps source text and actual version while rejecting metadata with wrong types',()=>{expect(sourceInfo({name:' Бог — Бог ',language:{name:'Русский'},author:123,updated_at:null,capabilities:['H/G',null,42]},'verified-SHA')).toEqual({name:' Бог — Бог ',shortName:undefined,language:'Русский',author:undefined,edition:undefined,source:undefined,version:'verified-SHA',updated:undefined,capabilities:['H/G']})})
 it('ships the same localized gesture explanations and all reader topics on both platforms',()=>{const native=JSON.parse(readFileSync(new URL('../../mobile/androidApp/src/main/assets/reader-help.json',import.meta.url),'utf8'));expect(native).toEqual(readerHelpMessages);for(const help of Object.values(readerHelpMessages)){expect(help.sections).toHaveLength(8);expect(help.sections.every(section=>section.length===2&&section[0]!.trim().length>0&&section[1]!.trim().length>40)).toBe(true);expect(help.horizontal).toContain('↔');expect(help.vertical).toContain('↕')}})
})
