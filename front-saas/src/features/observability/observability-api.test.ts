import type { AxiosAdapter } from 'axios'
import { expect, it } from 'vitest'
import { createHttpClient } from '@/shared/api/http'
import { createObservabilityApi } from './observability-api'

it('fetches log route trace from the dedicated endpoint', async () => {
  const calls: string[] = []
  const adapter: AxiosAdapter = async config => { calls.push(`${config.method} ${config.url}`); return { data: { code: 0, message: 'success', data: { trace: [{ id: 'request', detail: 'x', status: 'complete' }] } }, status: 200, statusText: 'OK', headers: {}, config } }
  const api = createObservabilityApi(createHttpClient({ getAccessToken: () => 'jwt', refresh: async () => null, clear: () => {} }, { getTenantOverride: () => null, getLocale: () => 'zh-CN' }, adapter))
  await api.logRouteTrace(7)
  expect(calls).toEqual(['get /api/v1/tenant/logs/7/route-trace'])
})

it('fetches usage by model with snake_case contract', async () => {
  const adapter: AxiosAdapter = async config => ({ data: { code: 0, message: 'success', data: [{ model: 'qwen-max', cnt: 9, tokens: 300 }] }, status: 200, statusText: 'OK', headers: {}, config })
  const api = createObservabilityApi(createHttpClient({ getAccessToken: () => 'jwt', refresh: async () => null, clear: () => {} }, { getTenantOverride: () => null, getLocale: () => 'zh-CN' }, adapter))
  const result = await api.usageByModel()
  expect(result[0].model).toBe('qwen-max')
})
