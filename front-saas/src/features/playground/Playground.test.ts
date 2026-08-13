import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia } from 'pinia'
import { expect, it, vi } from 'vitest'
import Playground from './Playground.vue'
import { zhCN } from '@/locales/zh-CN'

it('streams deltas into the conversation and exposes distinct error states', async () => {
  const stream = vi.fn().mockImplementation((input: { handlers: { onDelta: (t: string) => void } }) => { input.handlers.onDelta('hi'); return { abort: () => {} } })
  const wrapper = mount(Playground, { global: { plugins: [createPinia(), createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })] }, props: { stream } })
  await wrapper.get('textarea[name="prompt"]').setValue('hello')
  await wrapper.get('[data-action="send"]').trigger('click')
  expect(stream).toHaveBeenCalled()
  expect(wrapper.get('[role="log"]').text()).toContain('hi')
})
