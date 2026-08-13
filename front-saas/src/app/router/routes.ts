import type { RouteRecordRaw } from 'vue-router'
import AppShell from '@/app/layout/AppShell.vue'
import LoginView from '@/features/auth/LoginView.vue'
import ForbiddenView from '@/features/auth/ForbiddenView.vue'
import DashboardPlaceholderView from '@/features/dashboard/DashboardPlaceholderView.vue'
import TenantManagementView from '@/features/tenants/TenantManagementView.vue'
import UserManagementView from '@/features/users/UserManagementView.vue'
import AuditLogView from '@/features/system/audit/AuditLogView.vue'
import RoutingWorkspaceView from '@/features/routing/RoutingWorkspaceView.vue'
import UpstreamManagementView from '@/features/upstreams/UpstreamManagementView.vue'
import PlaygroundView from '@/features/playground/PlaygroundView.vue'
import ProxyKeyManagementView from '@/features/proxy-keys/ProxyKeyManagementView.vue'
import LogsView from '@/features/observability/LogsView.vue'
import UsageView from '@/features/observability/UsageView.vue'
import SystemMonitorView from '@/features/system/SystemMonitorView.vue'
import MembersView from '@/features/members/MembersView.vue'

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/overview' },
  { path: '/login', component: LoginView },
  { path: '/forbidden', component: ForbiddenView },
  { path: '/', component: AppShell, children: [
    { path: 'overview', component: DashboardPlaceholderView, meta: { titleKey: 'navigation.overview' } },
    { path: 'playground', component: PlaygroundView, meta: { titleKey: 'navigation.playground', permission: 'tenant:playground:use' } },
    { path: 'proxy-keys', component: ProxyKeyManagementView, meta: { titleKey: 'navigation.proxyKeys', permission: 'tenant:proxy_key:manage' } },
    { path: 'admin/tenants', component: TenantManagementView, meta: { titleKey: 'navigation.tenants', permission: 'platform:tenant:read' } },
    { path: 'admin/users', component: UserManagementView, meta: { titleKey: 'navigation.users', permission: 'platform:user:manage' } },
    { path: 'admin/audit-logs', component: AuditLogView, meta: { titleKey: 'audit.title', permission: 'platform:system:read' } },
    { path: 'routing-rules', component: RoutingWorkspaceView, meta: { titleKey: 'navigation.rules', permission: 'tenant:routing:read' } },
    { path: 'upstreams', component: UpstreamManagementView, meta: { titleKey: 'navigation.upstreams', permission: 'tenant:upstream:read' } },
    { path: 'logs', component: LogsView, meta: { titleKey: 'navigation.logs', permission: 'tenant:log:read' } },
    { path: 'usage', component: UsageView, meta: { titleKey: 'navigation.usage', permission: 'tenant:usage:read' } },
    { path: 'system/monitor', component: SystemMonitorView, meta: { titleKey: 'navigation.monitor', permission: 'platform:system:read' } },
    { path: 'members', component: MembersView, meta: { titleKey: 'members.title', permission: 'tenant:member:manage' } },
  ] },
  { path: '/:pathMatch(.*)*', redirect: '/overview' },
]
