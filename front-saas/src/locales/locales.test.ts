import { describe, expect, it } from 'vitest'
import { enUS } from './en-US'
import { zhCN } from './zh-CN'

function flatten(value: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(value).flatMap(([key, item]) => {
    const path = prefix ? `${prefix}.${key}` : key
    return item && typeof item === 'object' ? flatten(item as Record<string, unknown>, path) : [path]
  })
}
describe('locale dictionaries', () => {
  it('provide identical semantic keys', () => {
    expect(flatten(enUS).sort()).toEqual(flatten(zhCN).sort())
  })
})
