import type { AxiosInstance } from 'axios'
export interface SystemHealth { status: string; database: string; redis: string }
export interface SystemConfig { log_retention_days: string; default_retry_count: string; default_upstream_timeout_ms: string }
export function createSystemApi(http: AxiosInstance) {
  return {
    async health() { return (await http.get<SystemHealth>('/api/v1/admin/system/health')).data },
    async metrics() { return (await http.get<Record<string, unknown>>('/api/v1/admin/system/metrics')).data },
    async config() { return (await http.get<Record<string, string>>('/api/v1/admin/system/config')).data },
    async updateConfig(input: Record<string, string | number>) { return (await http.put<Record<string, string>>('/api/v1/admin/system/config', input)).data },
  }
}
