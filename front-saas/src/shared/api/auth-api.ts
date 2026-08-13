import axios, { type AxiosInstance } from 'axios'
import type { AuthSession, CurrentUser, LoginInput } from './contracts'

export interface AuthApi {
  login(input: LoginInput): Promise<AuthSession>
  refresh(): Promise<AuthSession>
  logout(): Promise<void>
  me(): Promise<CurrentUser>
}

export function createAuthApi(http: AxiosInstance): AuthApi {
  return {
    login: async input => (await http.post<AuthSession>('/api/v1/auth/login', input)).data,
    refresh: async () => (await http.post<AuthSession>('/api/v1/auth/refresh')).data,
    logout: async () => { await http.post('/api/v1/auth/logout') },
    me: async () => (await http.get<CurrentUser>('/api/v1/auth/me')).data,
  }
}

const browserHttp = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL ?? '', withCredentials: true })
browserHttp.interceptors.response.use(response => {
  if (response.data && typeof response.data.code === 'number') response.data = response.data.data
  return response
})
export const browserAuthApi = createAuthApi(browserHttp)
