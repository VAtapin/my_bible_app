import { describe, expect, it } from 'vitest'
import type { BibleChapter, BibleVerse } from '@/api/contracts'
import { collectPassage, passagePoint, formatPassage, createWordMark, markMatches, eraseWordMarks, wordSegments,updateWordNote,emptyPersonalStudy } from './personalStudy'
const verse = (number:number,text=`Text ${number}`):BibleVerse=>({id:number,number,osis_ref:`John.1.${number}`,text,plain_text:text,has_strong_markup:false})
const chapter=(number=1,verses=[verse(1),verse(2),verse(3)]):BibleChapter=>({translation:{code:'RST',name:'Russian',short_name:null,language:{code:'ru',name:'Russian'}},book:{slug:'john',name:'John',short_name:null,chapters_count:3},chapter:{number,verses_count:verses.length},verses:verses.map(v=>({...v,osis_ref:`John.${number}.${v.number}`}))})
describe('personal study passages and durable word anchors',()=>{
  it('collects a cross-chapter passage in textual order and formats every option',async()=>{const range=await collectPassage(chapter(),{chapter:1,verse:3},{chapter:2,verse:2},async n=>chapter(n));expect(range.verses.map(v=>v.osis)).toEqual(['John.1.3','John.2.1','John.2.2']);expect(formatPassage(range,{reference:true,translation:true,numbers:true})).toBe('1:3 Text 3\n2:1 Text 1\n2:2 Text 2\n\nJohn 1:3–2:2\n\nRussian');expect(formatPassage(range,{reference:false,translation:false,numbers:false})).toBe('Text 3\nText 1\nText 2')})
  it('rejects missing interior verses instead of silently sharing a shortened passage',async()=>{await expect(collectPassage(chapter(1,[verse(1),verse(3)]),{chapter:1,verse:1},{chapter:1,verse:3},async()=>chapter())).rejects.toThrow('Incomplete');await expect(collectPassage(chapter(),{chapter:1,verse:3},{chapter:2,verse:4},async()=>chapter(2))).rejects.toThrow('Missing boundary')})
  it('rejects unavailable text and the wrong edition from a chapter loader',async()=>{await expect(collectPassage(chapter(1,[verse(1,'')]),{chapter:1,verse:1},{chapter:1,verse:1},async()=>chapter())).rejects.toThrow('Incomplete');await expect(collectPassage(chapter(),{chapter:1,verse:3},{chapter:2,verse:1},async()=>({...chapter(2),translation:{...chapter().translation,code:'DE'}}))).rejects.toThrow('Wrong chapter')})
  it('rejects reversed bounds, negative positions and non-numeric input',async()=>{expect(()=>passagePoint('0:2')).toThrow();expect(()=>passagePoint('1:0')).toThrow();expect(()=>passagePoint('1:2x')).toThrow();await expect(collectPassage(chapter(),{chapter:2,verse:1},{chapter:1,verse:2},async()=>chapter())).rejects.toThrow('Invalid')})
  it('anchors a repeated word by exact UTF-16 offset, source and edition',()=>{const v=verse(1,'Бог и Бог 👋');const mark=createWordMark('RST',v,6,9,'yellow',false,'Second word');expect(mark.quote).toBe('Бог');expect(markMatches(mark,'RST',v)).toBe(true);expect(markMatches(mark,'DE',v)).toBe(false);expect(markMatches(mark,'RST',{...v,osis_ref:'John.2.1'})).toBe(false);expect(markMatches(mark,'RST',{...v,plain_text:'Бог и Бог! 👋'})).toBe(false)})
  it('does not reattach stale marks even when the quote still exists at the same offset',()=>{const v=verse(1,'Бог любит'),m=createWordMark('RST',v,0,3,'blue',true);expect(wordSegments('Бог любил','RST',v.osis_ref,[m]).every(s=>s.marks.length===0)).toBe(true)})
  it('erases only the selected middle part and preserves comments on both sides',()=>{const v=verse(1,'abcdef'),m=createWordMark('RST',v,0,6,'green',true,'note');const result=eraseWordMarks([m],'RST',v,2,4);expect(result.map(x=>[x.start,x.end,x.quote,x.note])).toEqual([[0,2,'ab','note'],[4,6,'ef','note']]);expect(new Set(result.map(x=>x.id)).size).toBe(2);expect(eraseWordMarks([m],'DE',v,2,4)).toEqual([m])})
  it('renders overlapping marks without duplicating or losing text',()=>{const v=verse(1,'abcdefgh'),a=createWordMark('RST',v,1,5,'yellow',false),b=createWordMark('RST',v,3,7,'blue',true);const segments=wordSegments(v.plain_text,'RST',v.osis_ref,[a,b]);expect(segments.map(s=>s.text).join('')).toBe(v.plain_text);expect(segments.find(s=>s.start===3)?.marks).toHaveLength(2)})
 it('edits only a fresh exact word note while retaining source anchor and concurrently updated style',()=>{const m=createWordMark('RST',verse(1,'Бог и Бог'),6,9,'blue',true,'original'),value={...emptyPersonalStudy(),marks:[{...m,color:'green'}]};const result=updateWordNote(value,m,'edited');expect(result.marks[0]).toEqual({...m,color:'green',note:'edited'});expect(()=>updateWordNote(result,m,'overwrite')).toThrow();expect(()=>updateWordNote({...value,marks:[{...m,start:0,end:3}]},m,'wrong anchor')).toThrow()})

})


describe('word selection whitespace boundaries',()=>{
 it('keeps original UTF16 anchors and rejects empty selection',async()=>{
  const {trimWordSelection}=await import('./personalStudy')
  const body=' \tБог 𐍈 λόγος\u00a0 '
  expect(trimWordSelection(body,body.length,0)).toEqual({start:2,end:body.length-2})
  expect(body.slice(trimWordSelection(body,0,body.length)!.start,trimWordSelection(body,0,body.length)!.end)).toBe('Бог 𐍈 λόγος')
  expect(trimWordSelection(body,0,2)).toBeUndefined()
  expect(trimWordSelection(body,0,0)).toBeUndefined()
  expect(trimWordSelection(body,-1,3)).toBeUndefined()
 })
 it('translates presets while preserving custom labels',async()=>{
  const {personalColorName}=await import('../i18n/personalStudy')
  expect(personalColorName('yellow','ru')).toBe('Жёлтый')
  expect(personalColorName('blue','de')).toBe('Blau')
  expect(personalColorName('pink','uk')).toBe('Рожевий')
  expect(personalColorName('green','en')).toBe('Green')
  expect(personalColorName('My notes','ru')).toBe('My notes')
 })
})
