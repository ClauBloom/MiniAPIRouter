import axios, { AxiosHeaders, type AxiosAdapter, type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { ApiError } from './errors'
import type { ApiEnvelope, ApiProblem } from './contracts'

interface RetriableConfig extends InternalAxiosRequestConfig { _sessionRetried?: boolean }
export interface SessionAccess { getAccessToken(): string | null; refresh(): Promise<string | null>; clear(): void }
export interface ContextAccess { getTenantOverride(): number | null; getLocale(): string }

export function createHttpClient(session: SessionAccess, context: ContextAccess, adapter?: AxiosAdapter): AxiosInstance {
  const client = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
    withCredentials: true,
    ...(adapter ? { adapter } : {}),
  })
  let refreshPromise: Promise<string | null> | null = null

  client.interceptors.request.use(config => {
    const headers = AxiosHeaders.from(config.headers)
    const token = session.getAccessToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)
    headers.set('Accept-Language', context.getLocale())
    const tenant = context.getTenantOverride()
    if (tenant !== null && config.url?.startsWith('/api/v1/tenant/')) headers.set('X-Tenant-Id', String(tenant))
    config.headers = headers
    return config
  })

  client.interceptors.response.use(response => {
    const envelope = response.data as ApiEnvelope<unknown>
    if (typeof envelope?.code === 'number') {
      if (envelope.code !== 0) throw new ApiError(envelope as unknown as ApiProblem)
      response.data = envelope.data
    }
    return response
  }, async (unknownError: unknown) => {
    const error = unknownError as AxiosError<ApiProblem>
    const config = error.config as RetriableConfig | undefined
    if (error.response?.status === 401 && config && !config._sessionRetried && !config.url?.endsWith('/auth/refresh')) {
      config._sessionRetried = true
      refreshPromise ??= session.refresh().finally(() => { refreshPromise = null })
      const token = await refreshPromise
      if (token) return client(config)
      session.clear()
    }
    if (error.response?.data?.error_code) throw new ApiError(error.response.data)
    throw error
  })
  return client
}
