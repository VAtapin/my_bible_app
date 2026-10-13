import type { LocationQuery } from 'vue-router'

export function dictionaryBrowse(query: LocationQuery) {
  const catalog = query.view === 'catalog' ||
    (query.view !== 'search' && !query.q && !query.mode)
  const section = Number(query.letter)
  return {
    catalog,
    q: catalog ? '' : typeof query.q === 'string' ? query.q.slice(0, 120) : '',
    mode: !catalog && query.mode === 'meaning' ? 'meaning' as const : 'word' as const,
    letter: Number.isInteger(section) && section >= 1 && section <= 36 ? section : undefined,
  }
}

export function dictionaryCatalogQuery(letter?: number) {
  return { view: 'catalog', ...(letter ? { letter: String(letter) } : {}) }
}
