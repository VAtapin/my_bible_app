import {createSSRApp} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {describe,it,expect} from 'vitest'
import SourceCard from './SourceCard.vue'

describe('actual source card fields',()=>{
 it.each([null,{}, {name:' ',language:{name:' ',code:''},author:{title:'object'},edition:null,source:'',capabilities:[' ']}])('renders no empty card for %j',async metadata=>{
   const html=await renderToString(createSSRApp(SourceCard,{metadata,version:' ',capabilities:[]}))
   expect(html).not.toContain('<details');expect(html).not.toContain('<dt')
 })
 it('renders only actual label/value pairs without placeholder author or edition',async()=>{
   const html=await renderToString(createSSRApp(SourceCard,{metadata:{name:'Actual source',language:{name:'Русский'},author:null,edition:' ',source_url:''},expanded:true}))
   expect(html).toContain('Actual source');expect(html).toContain('Русский')
   expect(html.match(/<dt\b/g)).toHaveLength(2);expect(html.match(/<dd\b/g)).toHaveLength(2)
   expect(html).not.toContain('Не указано источником');expect(html).not.toContain('Автор /');expect(html).not.toContain('Издание')
 })
})
