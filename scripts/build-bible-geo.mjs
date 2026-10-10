import { readFile, writeFile, mkdir, copyFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const snapshots = {
 ancient: { sha256:'b8187aa4737e8517ccc090f765d2be11da4c548cd2a59d3cdcb62e952cb8c0f2', commit:'7eb18a5ee62f27b9b93bd6689ea272d76dd23b8f', url:'https://github.com/openbibleinfo/Bible-Geocoding-Data' },
 land: { sha256:'9e0729ee253ca7d7a5c4ae9395fb1902264c5377c52e224d13dd85010e2835d9', commit:'ca96624a56bd078437bca8184e78163e5039ad19', url:'https://github.com/nvkelso/natural-earth-vector' },
}
const plain = value => String(value??'').replace(/<[^>]*>/g,'').trim()
export function geographicPlace(row) {
 if(typeof row.id!=='string'||typeof row.friendly_id!=='string')throw Error('Missing published place identity')
 const locations=new Map()
 for(const identification of row.identifications??[]){for(const resolution of identification.resolutions??[]){
  if(typeof resolution.lonlat!=='string')continue
  const coordinates=resolution.lonlat.split(',').map(Number),[lon,lat]=coordinates
  if(coordinates.length!==2||!Number.isFinite(lon)||!Number.isFinite(lat)||Math.abs(lon)>180||Math.abs(lat)>90)throw Error('Invalid source coordinate')
  if(typeof resolution.modern_basis_id!=='string')continue
  const association=row.modern_associations?.[resolution.modern_basis_id]
  const score=association?.score??resolution.best_time_score??identification.score?.time_total??null
  if(score!==null&&!Number.isFinite(score))throw Error('Invalid published confidence score')
  const type=String(resolution.lonlat_type??resolution.type??'')
  const id=`${resolution.modern_basis_id}:${lon}:${lat}:${type}`
  locations.set(id,{id,name:plain(association?.name??resolution.description),lon,lat,score,type})
 }}
 const verses=[...new Set((row.verses??[]).map(v=>v.osis))]
 if(verses.some(v=>typeof v!=='string'||!/^[A-Za-z0-9]+\.[1-9]\d*\.[1-9]\d*$/.test(v)))throw Error('Invalid published verse reference')
 return{id:row.id,name:plain(row.friendly_id),aliases:Object.keys(row.translation_name_counts??{}).map(plain),verses,locations:[...locations.values()]}
}
export function landRings(geometry) {
 const rings=[]
 for(const feature of geometry.features??[]){const g=feature.geometry;if(g.type==='Polygon')rings.push(...g.coordinates);else if(g.type==='MultiPolygon')for(const polygon of g.coordinates)rings.push(...polygon);else throw Error('Unexpected land geometry')}
 if(rings.some(r=>r.length<4||r.some(point=>point.length<2||!Number.isFinite(point[0])||!Number.isFinite(point[1])||Math.abs(point[0])>180.001||Math.abs(point[1])>90.001)))throw Error('Invalid land shape')
 return rings.map(r=>r.map(([lon,lat])=>[Number(lon.toFixed(5)),Number(lat.toFixed(5))]))
}
const itinerary=[['ae41ab4','Acts.13.1'],['a6d306d','Acts.13.4'],['afa863b','Acts.13.5'],['a314765','Acts.13.6'],['aff04b8','Acts.13.13'],['a6c704a','Acts.13.14'],['ae425aa','Acts.14.1'],['af0719d','Acts.14.6'],['aa401a9','Acts.14.20'],['af0719d','Acts.14.21'],['ae425aa','Acts.14.21'],['a6c704a','Acts.14.21'],['aff04b8','Acts.14.25'],['ac744c1','Acts.14.25'],['ae41ab4','Acts.14.26']]
export function firstJourney(places) {
 for(const[id,verse]of itinerary)if(!places.some(p=>p.id===id&&p.verses.includes(verse)&&p.locations.length))throw Error('Unverified itinerary stop')
 return{id:'paul-first',kind:'schematic',source:'Acts 13–14',stops:itinerary.map(([place_id,osis])=>({place_id,osis}))}
}
async function build(directory) {
 const ancient=await readFile(resolve(directory,'ancient.jsonl')),land=await readFile(resolve(directory,'land.geojson'))
 for(const[key,bytes]of Object.entries({ancient,land}))if(createHash('sha256').update(bytes).digest('hex')!==snapshots[key].sha256)throw Error('Source snapshot checksum changed; review before updating')
 const places=ancient.toString('utf8').trim().split('\n').map(line=>geographicPlace(JSON.parse(line)))
 const data={schema:1,referenceSystem:'English Protestant Bible (OpenBible.info)',sources:[{name:'OpenBible.info Bible Geocoding',url:snapshots.ancient.url,license:'CC BY 4.0',license_url:'https://creativecommons.org/licenses/by/4.0/',commit:snapshots.ancient.commit,sha256:snapshots.ancient.sha256},{name:'Natural Earth 1:110m land',url:snapshots.land.url,license:'Public domain',license_url:'https://www.naturalearthdata.com/about/terms-of-use/',commit:snapshots.land.commit,sha256:snapshots.land.sha256},{name:'OpenStreetMap contributors (source geography)',url:'https://www.openstreetmap.org/copyright',license:'ODbL 1.0',license_url:'https://opendatacommons.org/licenses/odbl/1-0/'}],places,land:landRings(JSON.parse(land.toString('utf8'))),routes:[firstJourney(places)]}
 for(const root of ['public','mobile/androidApp/src/main/assets']){
  await mkdir(`${root}/data`,{recursive:true});await mkdir(`${root}/licenses`,{recursive:true});await writeFile(`${root}/data/bible-geo.json`,JSON.stringify(data));await copyFile(resolve(directory,'OpenBible-CC-BY-4.0.txt'),`${root}/licenses/OpenBible-CC-BY-4.0.txt`)
 }
 console.log(`Built verified offline geography: ${places.length} places; ${data.land.length} land rings; ${itinerary.length} attested journey stops.`)
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))await build(process.argv[2]??'.task-geo')
