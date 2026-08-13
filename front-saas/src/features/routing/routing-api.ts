import type { AxiosInstance } from 'axios'
export interface SimulationInput { model?: string | undefined; intent?: string | undefined; complexity?: number | undefined; agent_type?: string | undefined }
export interface SimulationResult { matched: boolean; matched_rule_name?: string; selected_model?: string | null; fallback_order: string[]; evaluated_intent?: string; trace: Array<{ id: string; detail: string; status: string }> }
export interface IntentItem { id: number; intent_name: string; label?: string; model_weights: Record<string, number> }
export interface IntentPage { list: IntentItem[]; total: number; page: number; page_size: number }
export function createRoutingApi(http: AxiosInstance) {
  return {
    async simulate(input: SimulationInput) { return (await http.post<SimulationResult>('/api/v1/tenant/route-rules/simulate', input)).data },
    async validateRule(input: unknown) { return (await http.post<{ valid: boolean; summary: string }>('/api/v1/tenant/route-rules/validate', input)).data },
    async listIntents() { return (await http.get<IntentPage>('/api/v1/tenant/intents')).data },
    async listModels() { return (await http.get<Array<{ id: number; display_name: string; upstream_name: string }>>('/api/v1/tenant/api-keys/models')).data },
  }
}
