import { expect, it } from 'vitest'
import { renderSafeMarkdown } from './safe-markdown'

it('escapes raw HTML and blocks javascript URLs', () => {
  expect(renderSafeMarkdown('<script>alert(1)</script>')).not.toContain('<script>')
  expect(renderSafeMarkdown('[x](javascript:alert(1))')).not.toContain('javascript:')
  expect(renderSafeMarkdown('**bold**')).toContain('<strong>bold</strong>')
})
