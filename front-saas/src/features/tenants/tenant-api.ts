import type { AxiosInstance } from 'axios'
import type { PageResult, QuotaAdjustment, Tenant, TenantOnboarding } from './contracts'
export function createTenantApi(http:AxiosInstance){return{
  async list(){return (await http.get<PageResult<Tenant>>('/api/v1/admin/tenants')).data},
  async create(input:TenantOnboarding){const {admin_username,admin_password,...tenant}=input;const created=(await http.post<Tenant>('/api/v1/admin/tenants',tenant)).data;await http.post('/api/v1/admin/users',{tenant_id:created.id,username:admin_username,password:admin_password,role:'tenant_admin'});return created},
  async adjustQuota(id:number,input:QuotaAdjustment){return (await http.post<Tenant>(`/api/v1/admin/tenants/${id}/quota-adjustments`,input)).data},
  async changeStatus(id:number,status:number){return (await http.patch<Tenant>(`/api/v1/admin/tenants/${id}/status`,{status})).data},
}}
