import type { AxiosInstance } from 'axios'
export interface LogItem { id: number; trace_id: string; model: string; mapped_provider: string; status: string; total_tokens: number; latency_ms: number; created_at: string }
export interface LogPage { list: LogItem[]; total: number; page: number; page_size: number }
export interface RouteTrace { matched_rule_id?: number; intent?: string; status?: string; trace: Array<{ id: string; detail: string; status: string }> }
export interface UsageSummary { total_requests: number; total_tokens: number; avg_latency_ms: number; success_rate: number; model_distribution: Array<{ model: string; cnt: number; tokens: number }> }
export function createObservabilityApi(http: AxiosInstance) {
  return {
    async listLogs(params: Record<string, string | number> = {}) { return (await http.get<LogPage>('/api/v1/tenant/logs', { params })).data },
    async logRouteTrace(id: number) { return (await http.get<RouteTrace>(`/api/v1/tenant/logs/${id}/route-trace`)).data },
    async usageSummary() { return (await http.get<UsageSummary>('/api/v1/tenant/usage/summary')).data },
    async usageByModel() { return (await http.get<Array<{ model: string; cnt: number; tokens: number }>>('/api/v1/tenant/usage/by-model')).data },
    async usageTrend() { return (await http.get<Array<{ day: string; cnt: number; tokens: number }>>('/api/v1/tenant/usage/trend')).data },
  }
}
