export interface Tenant { id:number; tenant_code:string; tenant_name:string; plan:string; quota_limit:number; quota_used:number; max_rps:number; status:number; expires_at?:string|null; created_at?:string }
export interface PageResult<T> { list:T[]; total:number; page:number; page_size:number }
export interface QuotaAdjustment { quota_limit:number; reason:string }
export interface TenantOnboarding { tenant_code:string; tenant_name:string; plan:string; quota_limit:number; max_rps:number; admin_username:string; admin_password:string }
