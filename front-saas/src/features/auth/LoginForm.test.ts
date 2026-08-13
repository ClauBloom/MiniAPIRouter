import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import LoginForm from './components/LoginForm.vue'
import { zhCN } from '@/locales/zh-CN'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
describe('LoginForm', () => {
  it('validates required credentials before submitting', async () => {
    const submit = vi.fn()
    const wrapper = mount(LoginForm, { global: { plugins: [i18n] }, props: { pending: false, onSubmit: submit } })
    await wrapper.get('form').trigger('submit')
    expect(submit).not.toHaveBeenCalled()
    expect(wrapper.get('[data-field-error="username"]').text()).toContain('用户名')
  })

  it('submits credentials without rendering password', async () => {
    const submit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(LoginForm, { global: { plugins: [i18n] }, props: { pending: false, onSubmit: submit } })
    await wrapper.get('input[name="username"]').setValue('demo')
    await wrapper.get('input[name="password"]').setValue('secret-value')
    await wrapper.get('input[name="tenant_code"]').setValue('demo')
    await wrapper.get('form').trigger('submit')
    expect(submit).toHaveBeenCalledWith({ username: 'demo', password: 'secret-value', tenant_code: 'demo' })
    expect(wrapper.text()).not.toContain('secret-value')
  })
})
