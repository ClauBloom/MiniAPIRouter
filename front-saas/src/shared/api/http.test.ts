import type { AxiosAdapter, AxiosResponse } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { createHttpClient } from './http'

function response(config: Parameters<AxiosAdapter>[0], status: number, data: unknown): AxiosResponse {
  return { config, status, statusText: String(status), headers: {}, data }
}

describe('management HTTP client', () => {
  it('shares one refresh between concurrent 401 responses', async () => {
    let accessToken = 'expired'
    const attempts = new Map<string, number>()
    const adapter: AxiosAdapter = async (config) => {
      const url = config.url ?? ''
      attempts.set(url, (attempts.get(url) ?? 0) + 1)
      if (config.headers.get('Authorization') === 'Bearer expired') {
        return Promise.reject({ config, response: response(config, 401, { error_code: 'UNAUTHORIZED' }), isAxiosError: true })
      }
      return response(config, 200, { code: 0, message: 'success', data: { url } })
    }
    const refresh = vi.fn(async () => { accessToken = 'fresh'; return 'fresh' })
    const client = createHttpClient({ getAccessToken: () => accessToken, refresh, clear: vi.fn() },
      { getTenantOverride: () => null, getLocale: () => 'en-US' }, adapter)

    const [one, two] = await Promise.all([client.get('/one'), client.get('/two')])

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(one.data).toEqual({ url: '/one' })
    expect(two.data).toEqual({ url: '/two' })
    expect(attempts.get('/one')).toBe(2)
  })

  it('injects locale and authorized tenant override', async () => {
    let captured: Record<string, string> = {}
    const adapter: AxiosAdapter = async (config) => {
      captured = config.headers.toJSON() as Record<string, string>
      return response(config, 200, { code: 0, message: 'success', data: {} })
    }
    const client = createHttpClient({ getAccessToken: () => 'jwt', refresh: vi.fn(), clear: vi.fn() },
      { getTenantOverride: () => 42, getLocale: () => 'zh-CN' }, adapter)

    await client.get('/api/v1/tenant/logs')

    expect(captured.Authorization).toBe('Bearer jwt')
    expect(captured['X-Tenant-Id']).toBe('42')
    expect(captured['Accept-Language']).toBe('zh-CN')
  })
})
