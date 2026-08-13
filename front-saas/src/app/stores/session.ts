import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { browserAuthApi, type AuthApi } from '@/shared/api/auth-api'
import type { CurrentUser, LoginInput } from '@/shared/api/contracts'

export function createSessionStore(api: AuthApi) {
  return defineStore('session', () => {
    const accessToken = ref<string | null>(null)
    const user = ref<CurrentUser | null>(null)
    const bootstrapped = ref(false)
    const isAuthenticated = computed(() => accessToken.value !== null && user.value !== null)

    function accept(session: { access_token: string; user: CurrentUser }) {
      accessToken.value = session.access_token
      user.value = session.user
      return session.access_token
    }
    function clear() { accessToken.value = null; user.value = null }
    function can(permission: string) { return user.value?.permissions.includes(permission) ?? false }
    async function login(input: LoginInput) { accept(await api.login(input)) }
    async function refresh() { return accept(await api.refresh()) }
    async function bootstrap() {
      if (bootstrapped.value) return
      try { await refresh(); user.value = await api.me() } catch { clear() } finally { bootstrapped.value = true }
    }
    async function logout() { try { await api.logout() } finally { clear() } }
    return { accessToken, user, bootstrapped, isAuthenticated, can, clear, login, refresh, bootstrap, logout }
  })
}

export const useSessionStore = createSessionStore(browserAuthApi)
