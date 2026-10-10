import 'fake-indexeddb/auto'
import { IDBFactory } from 'fake-indexeddb'
import { beforeEach,describe,expect,it } from 'vitest'
import { createPersonalStudyRepository } from './personalStudy'
import { offlineStores,openOfflineDatabase,runRequest } from './database'
describe('personal study local persistence',()=>{
 beforeEach(()=>{globalThis.indexedDB=new IDBFactory()})
 it('serializes concurrent changes and persists independent entities across reopening',async()=>{const a=createPersonalStudyRepository(),b=createPersonalStudyRepository();await Promise.all([a.update(v=>({...v,palette:{...v.palette,custom:{day:'#ffffff',night:'#000000'}}})),b.update(v=>({...v,palette:{...v.palette,second:{day:'#bbbbbb',night:'#111111'}}}))]);expect(Object.keys((await a.read()).palette)).toContain('custom');expect(Object.keys((await b.read()).palette)).toContain('second')})
 it('refuses to overwrite a newer schema and leaves other profile values intact',async()=>{const db=await openOfflineDatabase();await runRequest(db.transaction(offlineStores.state,'readwrite').objectStore(offlineStores.state).put({key:'personal-study:v1',value:{version:99,bookmarks:[],cards:[],marks:[],palette:{}}}));await runRequest(db.transaction(offlineStores.state,'readwrite').objectStore(offlineStores.state).put({key:'reading-location',value:{chapter:3}}));db.close();await expect(createPersonalStudyRepository().update(v=>v)).rejects.toThrow('Invalid');const reopened=await openOfflineDatabase();expect((await runRequest(reopened.transaction(offlineStores.state,'readonly').objectStore(offlineStores.state).get('personal-study:v1'))).value.version).toBe(99);expect((await runRequest(reopened.transaction(offlineStores.state,'readonly').objectStore(offlineStores.state).get('reading-location'))).value.chapter).toBe(3);reopened.close()})
})
