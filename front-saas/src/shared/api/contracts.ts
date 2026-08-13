export type Role = 'super_admin' | 'tenant_admin' | 'user'
export interface ApiEnvelope<T> { code: number; message: string; data: T; trace_id?: string }
export interface FieldProblem { field: string; reason: string }
export interface ApiProblem { code: number; message: string; error_code: string; trace_id?: string; details?: FieldProblem[] }
export interface CurrentUser { id: number; username: string; nickname: string | null; role: Role; tenant_id: number; tenant_name: string; permissions: string[] }
export interface AuthSession { access_token: string; expires_in: number; user: CurrentUser }
export interface LoginInput { username: string; password: string; tenant_code?: string }
