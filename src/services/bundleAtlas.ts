import {projectGeo,type GeoData} from './geography'
export const bundleAtlasCache='bible-desktop:bundle-atlas-v1'
export const bundleAtlasUrl=()=>`${import.meta.env.BASE_URL}data/bible-geo.json`
export function validateBundleAtlas(data:unknown):data is GeoData{
 if(!data||typeof data!=='object')return false
 const geo=data as GeoData
 if(geo.schema!==1||!Array.isArray(geo.places)||!Array.isArray(geo.land)||!Array.isArray(geo.sources))return false
 try{for(const place of geo.places){if(typeof place.name!=='string'||!Array.isArray(place.verses)||!Array.isArray(place.locations))return false;for(const location of place.locations)projectGeo(location.lon,location.lat)}for(const ring of geo.land)for(const point of ring)projectGeo(point[0]!,point[1]!)}catch{return false}
 return true
}
export async function cachedBundleAtlas(storage:CacheStorage=caches):Promise<Response|undefined>{
 const response=await(await storage.open(bundleAtlasCache)).match(bundleAtlasUrl())
 if(!response?.ok)return undefined
 try{return validateBundleAtlas(await response.clone().json())?response:undefined}catch{return undefined}
}
export async function ensureBundleAtlas(signal:AbortSignal,fetcher:typeof fetch=fetch,storage:CacheStorage=caches,force=false){
 if(!force&&await cachedBundleAtlas(storage))return
 const response=await fetcher(bundleAtlasUrl(),{signal,credentials:'omit'})
 if(!response.ok||!validateBundleAtlas(await response.clone().json()))throw Error('Atlas unavailable')
 if(signal.aborted)throw new DOMException('Aborted','AbortError')
 await(await storage.open(bundleAtlasCache)).put(bundleAtlasUrl(),response)
 if(!await cachedBundleAtlas(storage))throw Error('Atlas cache unavailable')
}
