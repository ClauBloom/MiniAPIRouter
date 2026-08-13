import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createSessionStore } from './session'

const user = { id: 2, username: 'demo', nickname: 'Demo', role: 'tenant_admin' as const,
  tenant_id: 1, tenant_name: 'Demo', permissions: ['tenant:routing:write'] }

describe('session store', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('restores through refresh and never persists access token', async () => {
    const api = { login: vi.fn(), refresh: vi.fn().mockResolvedValue({ access_token: 'jwt', expires_in: 900, user }),
      logout: vi.fn(), me: vi.fn().mockResolvedValue(user) }
    const useStore = createSessionStore(api)
    const store = useStore()

    await store.bootstrap()

    expect(store.accessToken).toBe('jwt')
    expect(store.can('tenant:routing:write')).toBe(true)
    expect(localStorage.getItem('access_token')).toBeNull()
  })

  it('clears state when refresh fails', async () => {
    const api = { login: vi.fn(), refresh: vi.fn().mockRejectedValue(new Error('expired')),
      logout: vi.fn(), me: vi.fn() }
    const store = createSessionStore(api)()

    await store.bootstrap()

    expect(store.isAuthenticated).toBe(false)
    expect(store.accessToken).toBeNull()
  })
})
