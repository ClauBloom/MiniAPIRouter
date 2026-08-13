import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import RouteTrace from './RouteTrace.vue'
import { enUS } from '@/locales/en-US'

describe('RouteTrace', () => {
  it('provides an ordered text equivalent and current step semantics', () => {
    const i18n = createI18n({ legacy: false, locale: 'en-US', messages: { 'en-US': enUS } })
    const wrapper = mount(RouteTrace, { global: { plugins: [i18n] }, props: { steps: [
      { id: 'request', label: 'Request', status: 'complete' },
      { id: 'intent', label: 'Intent', detail: 'coding_review', status: 'active' },
      { id: 'model', label: 'Model', status: 'skipped' },
    ] } })
    expect(wrapper.get('ol').attributes('aria-label')).toBeTruthy()
    expect(wrapper.get('[aria-current="step"]').text()).toContain('Intent')
    expect(wrapper.text()).toContain('coding_review')
  })
})
