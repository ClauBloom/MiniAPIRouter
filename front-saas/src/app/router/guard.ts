import type { Router } from 'vue-router'
export function installSessionGuard(router: Router, session: { bootstrap(): Promise<void>; isAuthenticated: boolean; can(permission: string): boolean }) {
  router.beforeEach(async to => {
    await session.bootstrap()
    if (to.path === '/login') return session.isAuthenticated ? '/overview' : true
    if (!session.isAuthenticated) return { path: '/login', query: { redirect: to.fullPath } }
    const permission = to.meta.permission as string | undefined
    if (permission && !session.can(permission)) return '/forbidden'
    return true
  })
}
