import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia } from 'pinia'
import { expect, it, vi } from 'vitest'
import UserManagementView from './UserManagementView.vue'
import { zhCN } from '@/locales/zh-CN'

it('changes user status through an explicit action', async () => {
  const changeStatus=vi.fn().mockResolvedValue(undefined)
  const wrapper=mount(UserManagementView,{global:{plugins:[createPinia(),createI18n({legacy:false,locale:'zh-CN',messages:{'zh-CN':zhCN}})]},props:{users:[{id:8,tenant_id:7,username:'owner',nickname:'Owner',role:'tenant_admin',status:1}],changeStatus}})
  await wrapper.get('[data-action="status-8"]').trigger('click')
  expect(changeStatus).toHaveBeenCalledWith(8,0)
})

it('reveals reset password once and clears it when dismissed', async () => {
  const resetPassword=vi.fn().mockResolvedValue({temporary_password:'one-time-secret'})
  const wrapper=mount(UserManagementView,{global:{plugins:[createPinia(),createI18n({legacy:false,locale:'zh-CN',messages:{'zh-CN':zhCN}})]},props:{users:[{id:8,tenant_id:7,username:'owner',nickname:'Owner',role:'tenant_admin',status:1}],resetPassword}})
  await wrapper.get('[data-action="reset-8"]').trigger('click')
  expect(wrapper.get('[role="dialog"]').text()).toContain('one-time-secret')
  await wrapper.get('[data-action="dismiss-password"]').trigger('click')
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  expect(wrapper.text()).not.toContain('one-time-secret')
})
