import type { AxiosInstance } from 'axios'
export interface ProxyKey { api_key?: string; tenant_code?: string; created_at?: string; api_key_masked?: string }
export function createProxyKeyApi(http: AxiosInstance) {
  return {
    async generate(idempotencyKey?: string) {
      const headers: Record<string, string> = {}
      if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
      return (await http.post<ProxyKey>('/api/v1/tenant/proxy-keys', undefined, { headers })).data
    },
    async list() { return (await http.get<ProxyKey[]>('/api/v1/tenant/proxy-keys')).data },
  }
}
