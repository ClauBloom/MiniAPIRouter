import type { AxiosAdapter } from 'axios'
import { expect, it } from 'vitest'
import { createHttpClient } from '@/shared/api/http'
import { createTenantApi } from './tenant-api'

it('sends dedicated quota adjustment contract', async () => {
  let request: Parameters<AxiosAdapter>[0] | undefined
  const adapter: AxiosAdapter = async config => { request=config; return {data:{code:0,message:'success',data:{id:7,quota_limit:3000}},status:200,statusText:'OK',headers:{},config} }
  const http=createHttpClient({getAccessToken:()=> 'jwt',refresh:async()=>null,clear:()=>{}},{getTenantOverride:()=>null,getLocale:()=> 'zh-CN'},adapter)
  const api=createTenantApi(http)
  await api.adjustQuota(7,{quota_limit:3000,reason:'renewal'})
  expect(request?.url).toBe('/api/v1/admin/tenants/7/quota-adjustments')
  expect(JSON.parse(String(request?.data))).toEqual({quota_limit:3000,reason:'renewal'})
})
