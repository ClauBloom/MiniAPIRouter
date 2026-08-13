import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { expect, it, vi } from 'vitest'
import UpstreamForm from './UpstreamForm.vue'
import { zhCN } from '@/locales/zh-CN'

it('submits connection and mapping without rendering the secret', async () => {
  const onSubmit = vi.fn().mockResolvedValue(undefined)
  const wrapper = mount(UpstreamForm, { global: { plugins: [createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })] }, props: { onSubmit } })
  await wrapper.get('input[name="name"]').setValue('Primary')
  await wrapper.get('input[name="api_key"]').setValue('sk-secret-value')
  await wrapper.get('input[name="base_url"]').setValue('https://api.example.com')
  await wrapper.get('input[name="mapping_display"]').setValue('qwen-max')
  await wrapper.get('input[name="mapping_real"]').setValue('qwen-max-0125')
  await wrapper.get('form').trigger('submit')
  expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ name: 'Primary', api_key: 'sk-secret-value', model_mapping: { 'qwen-max': 'qwen-max-0125' } }))
  expect(wrapper.text()).not.toContain('sk-secret-value')
})
