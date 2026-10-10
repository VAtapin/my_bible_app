import { installedDictionaryModules } from './installedDictionaries'
import { installedStudyPackage,studyPackageRows } from './studyPackageLookup'
import type { DictionaryPage } from '@/api/dictionaries'
export async function searchInstalledDictionaries(query:string,codes:string[],offset=0,limit=50):Promise<DictionaryPage> {
 if(!query.trim()||offset<0||limit<1||limit>100)throw new Error('Invalid dictionary search')
 const modules=(await installedDictionaryModules()).filter(m=>!codes.length||codes.includes(m.code)).sort((a,b)=>a.name.localeCompare(b.name)||a.code.localeCompare(b.code)),data:DictionaryPage['data']=[];let total=0,remaining=offset
 for(const module of modules){const pack=await installedStudyPackage(module.code);if(!pack)continue;const page=await studyPackageRows(pack,'entries.jsonl',{query,offset:remaining,limit:Math.max(0,limit-data.length)});total+=page.total
  if(remaining>=page.total){remaining-=page.total;continue}remaining=0
  for(const row of page.rows){if(typeof row.api_id!=='number')throw new Error('Package API alias unavailable');data.push({id:row.api_id,key:String(row.id),topic:String(row.topic),module_code:module.code,module_name:module.name})}
 }
 return{data,total}
}
