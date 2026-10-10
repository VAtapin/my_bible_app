export interface GeoLocation {id:string;name:string;lon:number;lat:number;score:number|null;type:string}
export interface GeoPlace {id:string;name:string;aliases?:string[];verses:string[];locations:GeoLocation[]}
export interface GeoSource {name:string;url:string;license:string;license_url?:string}
export interface GeoRoute {id:string;kind:string;source:string;stops:{place_id:string;osis:string}[]}
export interface GeoData {schema:number;referenceSystem:string;sources:GeoSource[];places:GeoPlace[];land:number[][][];routes?:GeoRoute[]}
export function projectGeo(lon:number,lat:number):[number,number]{
 if(!Number.isFinite(lon)||!Number.isFinite(lat)||Math.abs(lon)>180.001||Math.abs(lat)>90.001)throw Error('Invalid geographic coordinate')
 return[(lon+180)/360*1000,(90-lat)/180*500]
}
export function geographicPlaces(data:GeoData,query:string,references:string[]=[]):GeoPlace[]{
 const wanted=new Set(references),term=query.trim().toLocaleLowerCase()
 return data.places.filter(place=>(!wanted.size||place.verses.some(ref=>wanted.has(ref)))&&(!term||[place.name,...place.aliases??[],...place.locations.map(loc=>loc.name)].some(name=>name.toLocaleLowerCase().includes(term))))
}
export function geoLandPath(ring:number[][]):string{return ring.map(([lon,lat],i)=>`${i?'L':'M'}${projectGeo(lon!,lat!).join(',')}`).join(' ')+'Z'}
export function geoViewport(places:GeoPlace[]):{center:[number,number];scale:number}{
 const points=places.flatMap(place=>place.locations.map(loc=>projectGeo(loc.lon,loc.lat)))
 if(!points.length)return{center:[500,250],scale:1}
 const xs=points.map(p=>p[0]),ys=points.map(p=>p[1]),left=Math.min(...xs),right=Math.max(...xs),top=Math.min(...ys),bottom=Math.max(...ys)
 return{center:[(left+right)/2,(top+bottom)/2],scale:Math.min(32,Math.max(1,Math.min(800/Math.max(25,right-left),400/Math.max(12.5,bottom-top))))}
}
export function transformGeoViewport(center:[number,number],scale:number,bounds:{left:number;top:number;width:number;height:number},before:{x:number;y:number}[],after:{x:number;y:number}[]):{center:[number,number];scale:number}{
 const base=Math.min(bounds.width/1000,bounds.height/500)
 if(base<=0||!before.length||before.length!==after.length)return{center,scale}
 const midpoint=(points:{x:number;y:number}[])=>({x:points.reduce((sum,p)=>sum+p.x,0)/points.length,y:points.reduce((sum,p)=>sum+p.y,0)/points.length})
 const old=midpoint(before),next=midpoint(after),origin={x:bounds.left+bounds.width/2,y:bounds.top+bounds.height/2}
 const distance=(points:{x:number;y:number}[])=>points.length>1?Math.hypot(points[1]!.x-points[0]!.x,points[1]!.y-points[0]!.y):0,priorDistance=distance(before)
 const nextScale=Math.min(64,Math.max(1,scale*(priorDistance>0?distance(after)/priorDistance:1)))
 const anchor:[number,number]=[center[0]+(old.x-origin.x)/base/scale,center[1]+(old.y-origin.y)/base/scale]
 return{center:[anchor[0]-(next.x-origin.x)/base/nextScale,anchor[1]-(next.y-origin.y)/base/nextScale],scale:nextScale}
}
/** Journey lines describe attested stop order, not historical road geometry. */
export function geoRoutePoints(data:GeoData,route:GeoRoute,variants:Record<string,string>={}):[number,number][]{
 return route.stops.flatMap(stop=>{const place=data.places.find(p=>p.id===stop.place_id&&p.verses.includes(stop.osis));const location=place?.locations.find(loc=>loc.id===variants[place.id])??place?.locations[0];return location?[projectGeo(location.lon,location.lat)]:[]})
}
let loading:Promise<GeoData>|undefined
export function loadGeography():Promise<GeoData>{
 return loading??=(async()=>{const cached=typeof caches!=='undefined'?await(await import('./bundleAtlas')).cachedBundleAtlas():undefined;return cached??await fetch(`${import.meta.env.BASE_URL}data/bible-geo.json`)})().then(async response=>{if(!response.ok)throw Error(`Geographic dataset: ${response.status}`);const data=await response.json() as GeoData;if(data.schema!==1||!Array.isArray(data.places)||!Array.isArray(data.land)||!Array.isArray(data.sources))throw Error('Unsupported geographic dataset');data.places.forEach(place=>place.locations.forEach(loc=>projectGeo(loc.lon,loc.lat)));return data}).catch(error=>{loading=undefined;throw error})
}
