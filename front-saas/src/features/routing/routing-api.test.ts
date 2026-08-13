import type { AxiosAdapter } from 'axios'
import { expect, it } from 'vitest'
import { createHttpClient } from '@/shared/api/http'
import { createRoutingApi } from './routing-api'

it('posts simulation without touching a generation endpoint', async () => {
  const calls: string[] = []
  const adapter: AxiosAdapter = async config => { calls.push(`${config.method} ${config.url}`); return { data: { code: 0, message: 'success', data: { selected_model: 'qwen-max', trace: [] } }, status: 200, statusText: 'OK', headers: {}, config } }
  const api = createRoutingApi(createHttpClient({ getAccessToken: () => 'jwt', refresh: async () => null, clear: () => {} }, { getTenantOverride: () => null, getLocale: () => 'zh-CN' }, adapter))
  await api.simulate({ model: '*', intent: 'coding_review', complexity: 90 })
  expect(calls).toEqual(['post /api/v1/tenant/route-rules/simulate'])
})

it('lists tenant intents with snake_case contract', async () => {
  const adapter: AxiosAdapter = async config => ({ data: { code: 0, message: 'success', data: { list: [{ id: 1, intent_name: 'x', model_weights: {} }], total: 1, page: 1, page_size: 20 } }, status: 200, statusText: 'OK', headers: {}, config })
  const api = createRoutingApi(createHttpClient({ getAccessToken: () => 'jwt', refresh: async () => null, clear: () => {} }, { getTenantOverride: () => null, getLocale: () => 'zh-CN' }, adapter))
  const result = await api.listIntents()
  expect(result.list[0].intent_name).toBe('x')
})
