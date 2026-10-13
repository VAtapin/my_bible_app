/** Exact backend wire digest: UTF-8 byte order, retained duplicates and explicit nulls. */
export type ReferenceDigestEdge=[string,string|null,string|null,number|null]
export async function referenceTextDigest(text:string){return [...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text)))].map(value=>value.toString(16).padStart(2,'0')).join('')}
export function referenceGroupJson(source:string,osis:string,edges:ReferenceDigestEdge[]){
 const encoder=new TextEncoder(),serialized=edges.map(edge=>({edge,bytes:encoder.encode(JSON.stringify(edge))}))
 serialized.sort((a,b)=>{for(let i=0;i<Math.min(a.bytes.length,b.bytes.length);i++){const difference=a.bytes[i]!-b.bytes[i]!;if(difference)return difference}return a.bytes.length-b.bytes.length})
 return JSON.stringify([source,osis,serialized.map(row=>row.edge)])
}
export const referenceGroupDigest=(source:string,osis:string,edges:ReferenceDigestEdge[])=>referenceTextDigest(referenceGroupJson(source,osis,edges))
