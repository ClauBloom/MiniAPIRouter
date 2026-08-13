import type { AxiosInstance } from 'axios'
export interface UpstreamInput { name: string; provider: string; protocol?: string; api_key: string; base_url: string; model_mapping: Record<string, string>; priority?: number; max_concurrent?: number; qps_limit?: number; timeout_ms?: number; retry_count?: number }
export interface Upstream { id: number; name: string; provider: string; protocol: string; base_url: string; api_key_masked: string; status: number; health_status: string }
export interface HealthCheckResult { id: number; status: string; detail: string }
export function createUpstreamApi(http: AxiosInstance) {
  return {
    async create(input: UpstreamInput) { return (await http.post<Upstream>('/api/v1/tenant/api-keys', input)).data },
    async list() { return (await http.get<{ list: Upstream[]; total: number; page: number; page_size: number }>('/api/v1/tenant/api-keys')).data },
    async healthCheck(id: number) { return (await http.post<HealthCheckResult>(`/api/v1/tenant/api-keys/${id}/health-check`)).data },
  }
}
