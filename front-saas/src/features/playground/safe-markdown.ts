function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export function renderSafeMarkdown(markdown: string): string {
  let out = escapeHtml(markdown)
  out = out.replace(/`([^`]+)`/g, '<code>$1</code>')
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  out = out.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_match, text: string, url: string) => {
    const scheme = /^([a-z][a-z0-9+.-]*):/i.exec(url)
    if (scheme && !/^https?:$/i.test(scheme[1] ?? '')) return text
    return `<a href="${url}" rel="noopener noreferrer">${text}</a>`
  })
  return out
}
