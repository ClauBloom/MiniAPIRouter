import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import AppShell from './AppShell.vue'
import { enUS } from '@/locales/en-US'

const i18n = createI18n({ legacy: false, locale: 'en-US', messages: { 'en-US': enUS } })
describe('AppShell', () => {
  it('renders accessible navigation and filters platform items by permission', () => {
    const wrapper = mount(AppShell, { global: { plugins: [createPinia(), i18n], stubs: { RouterView: true, RouterLink: true } },
      props: { permissions: ['tenant:routing:read'], username: 'demo', tenantName: 'Demo' } })
    expect(wrapper.get('[aria-label="Primary navigation"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="platform-admin-group"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Demo')
  })
})
