/** Use the user's actual selection; never silently substitute the entire verse or cut words. */
export function dictionaryLookupQuery(value: unknown): string | undefined {
  if(typeof value!=='string')return undefined
  const query=value.trim()
  return query.length>0&&query.length<=120&&!/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/u.test(query)?query:undefined
}
export function dictionaryLookupLink(value: unknown) {
  const q=dictionaryLookupQuery(value)
  return q?{path:'/dictionaries',query:{q}}:undefined
}
