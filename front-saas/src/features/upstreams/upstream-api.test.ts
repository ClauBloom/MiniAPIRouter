import type { AxiosAdapter } from 'axios'
import { expect, it } from 'vitest'
import { createHttpClient } from '@/shared/api/http'
import { createUpstreamApi } from './upstream-api'

it('creates an upstream without echoing the raw key', async () => {
  let body: string | undefined
  const adapter: AxiosAdapter = async config => { body = String(config.data); return { data: { code: 0, message: 'success', data: { id: 7, api_key_masked: 'sk-***abc' } }, status: 200, statusText: 'OK', headers: {}, config } }
  const api = createUpstreamApi(createHttpClient({ getAccessToken: () => 'jwt', refresh: async () => null, clear: () => {} }, { getTenantOverride: () => null, getLocale: () => 'zh-CN' }, adapter))
  const created = await api.create({ name: 'Primary', provider: 'openai', api_key: 'sk-secret', base_url: 'https://api.example.com', model_mapping: { 'qwen-max': 'qwen-max-0125' } })
  expect(created.api_key_masked).toBe('sk-***abc')
  expect(body).toContain('sk-secret')
  expect(created).not.toHaveProperty('api_key')
})

it('triggers a health check on the dedicated endpoint', async () => {
  const calls: string[] = []
  const adapter: AxiosAdapter = async config => { calls.push(`${config.method} ${config.url}`); return { data: { code: 0, message: 'success', data: { id: 7, status: 'healthy', detail: 'HTTP 200' } }, status: 200, statusText: 'OK', headers: {}, config } }
  const api = createUpstreamApi(createHttpClient({ getAccessToken: () => 'jwt', refresh: async () => null, clear: () => {} }, { getTenantOverride: () => null, getLocale: () => 'zh-CN' }, adapter))
  await api.healthCheck(7)
  expect(calls).toEqual(['post /api/v1/tenant/api-keys/7/health-check'])
})
