<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSessionStore } from '@/app/stores/session'
import { useTenantContextStore } from '@/app/stores/tenant-context'
import LocaleSwitcher from './LocaleSwitcher.vue'
const props = defineProps<{ permissions?: string[]; username?: string; tenantName?: string }>()
const { t } = useI18n()
const session = useSessionStore()
const tenantContext = useTenantContextStore()
const mobileOpen = ref(false)
const effectivePermissions = computed(() => props.permissions ?? session.user?.permissions ?? [])
const displayUsername = computed(() => props.username ?? session.user?.nickname ?? session.user?.username ?? '')
const displayTenant = computed(() => tenantContext.overrideTenantName ?? props.tenantName ?? session.user?.tenant_name ?? '')
const canPlatform = computed(() => effectivePermissions.value.some(p => p.startsWith('platform:')))
interface NavigationGroup { key: string; label?: string; items: Array<{ label: string; to: string }> }
const groups = computed<NavigationGroup[]>(() => [
  { key: 'workspace', items: [{ label: 'navigation.overview', to: '/overview' }, { label: 'navigation.playground', to: '/playground' }] },
  { key: 'routing', label: 'navigation.routing', items: [{ label: 'navigation.upstreams', to: '/upstreams' }, { label: 'navigation.rules', to: '/routing-rules' }, { label: 'navigation.intents', to: '/intents' }, { label: 'navigation.proxyKeys', to: '/proxy-keys' }] },
  { key: 'observability', label: 'navigation.observability', items: [{ label: 'navigation.logs', to: '/logs' }, { label: 'navigation.usage', to: '/usage' }, { label: 'navigation.monitor', to: '/system/monitor' }] },
])
</script>
<template>
  <div class="shell">
    <button
      class="mobile-trigger"
      :aria-label="t('actions.openNavigation')"
      @click="mobileOpen=true"
    >
      ☰
    </button>
    <aside
      :class="['sidebar',{open:mobileOpen}]"
      :aria-label="t('actions.openNavigation')"
    >
      <div class="brand">
        <b>{{ t('brand.name') }}</b><span>{{ t('brand.console') }}</span>
      </div>
      <button
        class="mobile-close"
        :aria-label="t('actions.closeNavigation')"
        @click="mobileOpen=false"
      >
        ×
      </button>
      <nav aria-label="Primary navigation">
        <section
          v-for="group in groups"
          :key="group.key"
        >
          <h2 v-if="group.label">
            {{ t(group.label) }}
          </h2><RouterLink
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
          >
            {{ t(item.label) }}
          </RouterLink>
        </section>
        <section
          v-if="canPlatform"
          data-testid="platform-admin-group"
        >
          <h2>{{ t('navigation.platform') }}</h2><RouterLink to="/admin/tenants">
            {{ t('navigation.tenants') }}
          </RouterLink><RouterLink to="/admin/users">
            {{ t('navigation.users') }}
          </RouterLink><RouterLink to="/admin/audit-logs">
            {{ t('audit.title') }}
          </RouterLink>
        </section>
      </nav>
    </aside>
    <div
      v-if="mobileOpen"
      class="scrim"
      @click="mobileOpen=false"
    />
    <main>
      <header><LocaleSwitcher /><span class="tenant">{{ displayTenant }}</span><span>{{ displayUsername }}</span></header><div class="content">
        <RouterView />
      </div>
    </main>
  </div>
</template>
<style scoped lang="scss">
.shell{min-height:100vh;display:grid;grid-template-columns:var(--sidebar-width) 1fr}.sidebar{background:var(--surface);border-right:1px solid var(--border);padding:24px 18px;z-index:20}.brand{display:grid;margin-bottom:28px}.brand b{font-size:17px}.brand span{color:var(--route);font-size:12px}.sidebar section{display:grid;gap:4px;margin:18px 0}.sidebar h2{font-size:11px;text-transform:uppercase;color:var(--muted);letter-spacing:.08em}.sidebar a{display:flex;align-items:center;padding:0 12px;border-radius:8px;color:var(--ink);text-decoration:none}.sidebar a.router-link-active{background:var(--route-soft);color:var(--route);font-weight:600}main{min-width:0}header{height:68px;background:var(--surface);border-bottom:1px solid var(--border);display:flex;align-items:center;justify-content:flex-end;gap:24px;padding:0 28px}.tenant{background:var(--route-soft);padding:6px 12px;border-radius:8px}.content{padding:28px}.mobile-trigger,.mobile-close,.scrim{display:none}@media(max-width:1279px) and (min-width:768px){.shell{grid-template-columns:var(--sidebar-collapsed) 1fr}.sidebar{padding:20px 8px}.brand span,.sidebar h2{display:none}.sidebar a{font-size:0;justify-content:center}.sidebar a::first-letter{font-size:15px}}@media(max-width:767px){.shell{display:block}.sidebar{position:fixed;inset:0 auto 0 0;width:min(86vw,320px);transform:translateX(-105%);transition:transform .16s}.sidebar.open{transform:none}.mobile-trigger{display:block;position:fixed;left:10px;top:10px;z-index:10;border:0;background:var(--surface);font-size:20px}.mobile-close{display:block;position:absolute;right:12px;top:12px;border:0;background:none;font-size:24px}.scrim{display:block;position:fixed;inset:0;background:rgba(9,21,16,.45);z-index:15}header{padding-left:64px;height:64px}.content{padding:20px 16px}}
</style>
