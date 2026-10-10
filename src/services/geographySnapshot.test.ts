import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
// The reproducible generator is shared with the checked-in offline assets.
// @ts-expect-error plain JavaScript build utility
import { geographicPlace, firstJourney } from '../../scripts/build-bible-geo.mjs'

describe('offline geographic snapshot', () => {
  it('ships identical licensed geography in browser and Android', () => {
    const web = readFileSync('public/data/bible-geo.json', 'utf8')
    expect(readFileSync('mobile/androidApp/src/main/assets/data/bible-geo.json', 'utf8')).toBe(web)
    const data = JSON.parse(web)
    expect(data.places).toHaveLength(1342)
    expect(data.sources.find((s: {name:string}) => s.name.startsWith('OpenBible')).license).toBe('CC BY 4.0')
    expect(data.referenceSystem).toContain('English Protestant')
    expect(firstJourney(data.places)).toEqual(data.routes[0])
    expect(data.places.some((p: {locations:{score:number|null}[]}) => p.locations.some(l => l.score !== null && l.score < 0))).toBe(true)
  })
  it('preserves signed source estimates and rejects invented or invalid coordinates', () => {
    const row = { id:'a', friendly_id:'Source name', verses:[{osis:'Acts.13.1'}], modern_associations:{x:{score:-37}}, identifications:[{resolutions:[{modern_basis_id:'x',lonlat:'35,32',lonlat_type:'representative point'}]}] }
    expect(geographicPlace(row).locations[0]).toMatchObject({lon:35,lat:32,score:-37,type:'representative point'})
    expect(geographicPlace({...row,identifications:[]} ).locations).toEqual([])
    expect(() => geographicPlace({...row,identifications:[{resolutions:[{modern_basis_id:'x',lonlat:'35,92'}]}]})).toThrow()
  })
})
