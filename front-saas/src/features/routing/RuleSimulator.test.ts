import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia } from 'pinia'
import { expect, it, vi } from 'vitest'
import RuleSimulator from './RuleSimulator.vue'
import { zhCN } from '@/locales/zh-CN'

it('renders an ordered trace and selected model from a simulation', async () => {
  const simulate = vi.fn().mockResolvedValue({ matched: true, matched_rule_name: 'Code tasks', selected_model: 'qwen-max', fallback_order: ['glm-5'], evaluated_intent: 'coding_review', trace: [{ id: 'request', detail: 'request', status: 'complete' }, { id: 'intent', detail: 'coding_review', status: 'active' }] })
  const wrapper = mount(RuleSimulator, { global: { plugins: [createPinia(), createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })] }, props: { simulate } })
  await wrapper.get('input[name="intent"]').setValue('coding_review')
  await wrapper.get('[data-action="run-simulation"]').trigger('click')
  expect(wrapper.text()).toContain('qwen-max')
  expect(wrapper.get('ol').text()).toContain('coding_review')
})
