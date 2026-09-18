export function contentText(html: string): string {
  if (typeof DOMParser !== 'undefined') {
    return new DOMParser().parseFromString(html, 'text/html').body.textContent?.trim() ?? ''
  }
  return html.replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, '').trim()
}
