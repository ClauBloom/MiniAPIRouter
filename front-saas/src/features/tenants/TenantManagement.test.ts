import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import TenantListView from './TenantListView.vue'
import { zhCN } from '@/locales/zh-CN'
import { useTenantContextStore } from '@/app/stores/tenant-context'

const tenant = { id: 7, tenant_code: 'acme', tenant_name: 'Acme AI', plan: 'pro', quota_limit: 2000, quota_used: 500, max_rps: 20, status: 1 }

describe('TenantListView', () => {
  it('creates a tenant with its administrator credentials', async () => {
    const createTenant = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(TenantListView, { global: { plugins: [createPinia(), createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })] }, props: { tenants: [], onCreateTenant: createTenant } })
    await wrapper.get('[data-action="create-tenant"]').trigger('click')
    await wrapper.get('input[name="tenant_code"]').setValue('acme')
    await wrapper.get('input[name="tenant_name"]').setValue('Acme AI')
    await wrapper.get('input[name="admin_username"]').setValue('acme-owner')
    await wrapper.get('input[name="admin_password"]').setValue('safe-password')
    await wrapper.get('[data-submit="create-tenant"]').trigger('click')
    expect(createTenant).toHaveBeenCalledWith(expect.objectContaining({ tenant_code: 'acme', tenant_name: 'Acme AI', admin_username: 'acme-owner', admin_password: 'safe-password' }))
    expect(wrapper.text()).not.toContain('safe-password')
  })
  it('requires an audit reason before quota adjustment', async () => {
    const adjustQuota = vi.fn()
    const wrapper = mount(TenantListView, { global: { plugins: [createPinia(), createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })] }, props: { tenants: [tenant], onAdjustQuota: adjustQuota } })
    await wrapper.get('[data-action="quota-7"]').trigger('click')
    await wrapper.get('[data-submit="quota"]').trigger('click')
    expect(adjustQuota).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('原因')
  })

  it('enters and leaves the selected tenant workspace', async () => {
    const pinia = createPinia(); setActivePinia(pinia)
    const wrapper = mount(TenantListView, { global: { plugins: [pinia, createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })] }, props: { tenants: [tenant] } })
    const context = useTenantContextStore()
    await wrapper.get('[data-action="enter-7"]').trigger('click')
    expect(context.overrideTenantId).toBe(7)
    expect(context.overrideTenantName).toBe('Acme AI')
    await wrapper.get('[data-action="leave-context"]').trigger('click')
    expect(context.overrideTenantId).toBeNull()
  })
})
