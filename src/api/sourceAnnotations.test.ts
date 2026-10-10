import{describe,it,expect,vi}from'vitest'
import{createBibleApi}from'./client'
import{createChapterService}from'@/services/chapterService'
import{readWebChapter}from'@/services/webBibleLibrary'
import type{ChapterRepository}from'@/offline/chapterRepository'
const source={kind:'mybible',sha256:'a'.repeat(64)}
const chapter=(offset=0)=>({translation:{code:'X',name:'X',short_name:null,language:{code:'en',name:'English'}},book:{slug:'genesis',name:'Genesis',short_name:null,chapters_count:1},chapter:{number:1,verses_count:1},verses:[{id:1,number:1,osis_ref:'Gen.1.1',text:'😀<h>Heading</h>',plain_text:'😀',has_strong_markup:false,markup_format:'mybible',annotations:{status:'available',paragraph_before:false,paragraph_breaks:[],line_breaks:[],headings:[{id:'heading',text:'Heading',offset_utf16:offset,source}],footnotes:[],added_words:[],emphasis:[],red_letters:[],quotations:[],strong_tokens:[],source,features:{headings:'present',footnotes:'absent',added_words:'absent',paragraphs:'absent'}}}]})
describe('annotations opt-in API',()=>{
 it('requests retained annotations and accepts object provenance',async()=>{const fetcher=vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({data:chapter(2)}))),api=createBibleApi({baseUrl:'https://example.test/api',fetcher});const data=await api.getChapter('X','genesis',1);expect(data.verses[0]?.annotations?.headings[0]?.source.sha256).toBe(source.sha256);expect(String(fetcher.mock.calls[0]?.[0])).toContain('?annotations=1')})
 it('rejects malformed offsets without replacing saved data or pretending offline',async()=>{const api=createBibleApi({baseUrl:'https://example.test/api',fetcher:vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({data:chapter(1)})))});const put=vi.fn(),get=vi.fn(),service=createChapterService(api,{put,get}as unknown as ChapterRepository);await expect(readWebChapter(service,'X','genesis',1)).rejects.toMatchObject({kind:'invalid-response'});expect(put).not.toHaveBeenCalled();expect(get).not.toHaveBeenCalled()})
})
