const bookSlugs: Record<string, string> = {
  Gen: 'genesis', Exod: 'exodus', Lev: 'leviticus', Num: 'numbers', Deut: 'deuteronomy',
  Josh: 'joshua', Judg: 'judges', Ruth: 'ruth', '1Sam': '1samuel', '2Sam': '2samuel',
  '1Kgs': '1kings', '2Kgs': '2kings', '1Chr': '1chron', '2Chr': '2chron',
  Ezra: 'ezra', Neh: 'nehemiah', Esth: 'esther', Job: 'job', Ps: 'psalms', Prov: 'proverbs',
  Eccl: 'ecclesia', Song: 'songs', Isa: 'isaiah', Jer: 'jeremiah', Lam: 'lamentations',
  Ezek: 'ezekiel', Dan: 'daniel', Hos: 'hosea', Joel: 'joel', Amos: 'amos', Obad: 'obadiah',
  Jonah: 'jonah', Mic: 'micah', Nah: 'nahum', Hab: 'habakkuk', Zeph: 'zephaniah', Hag: 'haggai',
  Zech: 'zechariah', Mal: 'malachi', Matt: 'matthew', Mark: 'mark', Luke: 'luke', John: 'john',
  Acts: 'acts', Jas: 'james', '1Pet': '1peter', '2Pet': '2peter', '1John': '1john',
  '2John': '2john', '3John': '3john', Jude: 'jude', Rom: 'romans', '1Cor': '1corinthians',
  '2Cor': '2corinthians', Gal: 'galatians', Eph: 'ephesians', Phil: 'philippians', Col: 'colossians',
  '1Thess': '1thessalonians', '2Thess': '2thessalonians', '1Tim': '1timothy',
  '2Tim': '2timothy', Titus: 'titus', Phlm: 'philemon', Heb: 'hebrews', Rev: 'revelation',
}

export function readerTarget(book: string, chapter: number): { book: string; chapter: string } | undefined {
  const slug = bookSlugs[book]
  return slug ? { book: slug, chapter: String(chapter) } : undefined
}
