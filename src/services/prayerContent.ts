export interface PrayerSegment { text: string; emphasis: boolean; strong: boolean }
export interface PrayerBlock { heading: boolean; segments: PrayerSegment[] }

/** Repair detached combining marks without changing the text's spelling or edition. */
export function normalizePrayerText(value: string): string {
  return value.replace(/(\p{L}\p{M}*)[ \t\u00a0]+(?=\p{M})/gu, '$1')
}

function decodeText(value: string): string {
  const entities: Record<string, string> = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ', ndash: '–', mdash: '—', laquo: '«', raquo: '»', hellip: '…' }
  return normalizePrayerText(value.replace(/&(#x[\da-f]+|#\d+|\w+);/gi, (original, name: string) => {
    if (!name.startsWith('#')) return entities[name.toLowerCase()] ?? original
    const code = name[1]?.toLowerCase() === 'x' ? Number.parseInt(name.slice(2), 16) : Number(name.slice(1))
    return code > 0 && code <= 0x10ffff && !(code >= 0xd800 && code <= 0xdfff) ? String.fromCodePoint(code) : '�'
  }))
}

/** Only text and typographic structure reach Vue interpolation; never source HTML or attributes. */
export function prayerBlocks(value: string): PrayerBlock[] {
  const blocks: PrayerBlock[] = []
  let current: PrayerBlock = { heading: false, segments: [] }
  const inline: string[] = []
  const suppressed: string[] = []
  const flush = () => {
    if (current.segments.some((segment) => segment.text.trim())) blocks.push(current)
    current = { heading: false, segments: [] }
  }
  const add = (text: string) => {
    if (!text) return
    const previous = current.segments.at(-1)
    const emphasis = inline.some((tag) => ['em', 'i'].includes(tag))
    const strong = inline.some((tag) => ['strong', 'b'].includes(tag))
    if (previous && previous.emphasis === emphasis && previous.strong === strong) previous.text += text
    else current.segments.push({ text, emphasis, strong })
  }
  for (const token of value.match(/<!--[\s\S]*?(?:-->|$)|<[^>]*>|[^<]+|</g) ?? []) {
    if (token.startsWith('<!--')) continue
    const tag = /^<\s*(\/?)\s*([a-z][\w:-]*)/i.exec(token)
    if (!tag) {
      if (!suppressed.length) {
        const parts = decodeText(token).split(/\r?\n\s*\r?\n/)
        parts.forEach((part, index) => { if (index) flush(); add(part) })
      }
      continue
    }
    const name = tag[2]!.toLowerCase()
    const closing = Boolean(tag[1])
    if (['script', 'style', 'iframe', 'object', 'svg', 'math', 'noscript', 'button', 'form'].includes(name)) {
      if (closing) { const index = suppressed.lastIndexOf(name); if (index >= 0) suppressed.splice(index) }
      else if (!token.endsWith('/>')) suppressed.push(name)
      continue
    }
    if (suppressed.length) continue
    if (['p', 'div', 'section', 'li', 'h1', 'h2', 'h3', 'h4', 'blockquote'].includes(name)) {
      flush()
      current.heading = !closing && /^h[1-4]$/.test(name)
    } else if (name === 'br') add('\n')
    else if (['em', 'i', 'strong', 'b'].includes(name)) {
      if (!closing) inline.push(name)
      else { const index = inline.lastIndexOf(name); if (index >= 0) inline.splice(index, 1) }
    }
  }
  flush()
  return blocks
}

export function prayerExcerpt(value: string): string {
  return prayerBlocks(value).map((block) => block.segments.map((segment) => segment.text).join('')).join(' ').replace(/\s+/g, ' ').trim().slice(0, 120)
}
