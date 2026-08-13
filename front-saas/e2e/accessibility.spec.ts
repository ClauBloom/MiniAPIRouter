import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

const admin = { id: 1, username: 'root', nickname: 'Platform Admin', role: 'super_admin', tenant_id: 0, tenant_name: 'Platform', permissions: ['platform:tenant:read','tenant:log:read','tenant:usage:read','platform:system:read'] }
const envelope = (data: unknown) => JSON.stringify({ code: 0, message: 'success', data, trace_id: 'trace-e2e' })

test('login page has no critical accessibility violations', async ({ page }) => {
  await page.goto('/login')
  const results = await new AxeBuilder({ page }).analyze()
  expect(results.violations.filter(v => v.impact === 'critical')).toEqual([])
})

test('workspace shell has no critical accessibility violations', async ({ page }) => {
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ contentType: 'application/json', body: envelope({ access_token: 'jwt', expires_in: 900, user: admin }) }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ contentType: 'application/json', body: envelope(admin) }))
  await page.route('**/api/v1/admin/tenants', route => route.fulfill({ contentType: 'application/json', body: envelope({ list: [], total: 0, page: 1, page_size: 20 }) }))
  await page.goto('/admin/tenants')
  const results = await new AxeBuilder({ page }).analyze()
  expect(results.violations.filter(v => v.impact === 'critical')).toEqual([])
})
