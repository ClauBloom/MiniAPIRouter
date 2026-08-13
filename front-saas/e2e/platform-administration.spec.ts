import { expect, test } from '@playwright/test'

const admin = { id: 1, username: 'root', nickname: 'Platform Admin', role: 'super_admin', tenant_id: 0, tenant_name: 'Platform', permissions: ['platform:tenant:read','platform:tenant:write','platform:user:manage','platform:system:read'] }
const envelope = (data: unknown) => JSON.stringify({ code: 0, message: 'success', data, trace_id: 'trace-e2e' })

test('creates a tenant administrator, adjusts quota, enters context, and reviews audit', async ({ page }) => {
  const tenants = [{ id: 7, tenant_code: 'demo', tenant_name: 'Demo Corporation', plan: 'pro', quota_limit: 2000, quota_used: 500, max_rps: 20, status: 1 }]
  let createdAdmin: Record<string, unknown> | undefined
  let quotaReason = ''
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ contentType: 'application/json', body: envelope({ access_token: 'jwt', expires_in: 900, user: admin }) }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ contentType: 'application/json', body: envelope(admin) }))
  await page.route('**/api/v1/admin/tenants', async route => {
    if (route.request().method() === 'POST') {
      const input = route.request().postDataJSON(); const created = { id: 8, ...input, quota_used: 0, status: 1 }; tenants.push(created)
      await route.fulfill({ contentType: 'application/json', body: envelope(created) })
    } else await route.fulfill({ contentType: 'application/json', body: envelope({ list: tenants, total: tenants.length, page: 1, page_size: 20 }) })
  })
  await page.route('**/api/v1/admin/users', async route => { createdAdmin = route.request().postDataJSON(); await route.fulfill({ contentType: 'application/json', body: envelope({ id: 9, ...createdAdmin, status: 1 }) }) })
  await page.route('**/api/v1/admin/tenants/*/quota-adjustments', async route => { quotaReason = route.request().postDataJSON().reason; await route.fulfill({ contentType: 'application/json', body: envelope({ ...tenants[0], quota_limit: 3500 }) }) })
  await page.route('**/api/v1/admin/audit-logs', route => route.fulfill({ contentType: 'application/json', body: envelope({ list: [{ id: 1, action: 'TENANT_QUOTA_ADJUST', actor_user_id: 1, target_tenant_id: 7, trace_id: 'trace-audit' }], total: 1, page: 1, page_size: 20 }) }))

  await page.goto('/admin/tenants')
  await page.getByRole('button', { name: '创建租户' }).click()
  await page.getByLabel('租户编码').fill('acme')
  await page.getByLabel('租户', { exact: true }).fill('Acme AI')
  await page.getByLabel('用户名').fill('acme-owner')
  await page.getByLabel('密码').fill('not-persisted')
  await page.locator('[data-submit="create-tenant"]').click()
  await expect.poll(() => createdAdmin?.tenant_id).toBe(8)
  expect(createdAdmin?.role).toBe('tenant_admin')

  await page.locator('[data-action="quota-7"]:visible').click()
  await page.getByLabel('配额').fill('3500')
  await page.getByLabel('调整原因').fill('annual renewal')
  await page.locator('[data-submit="quota"]').click()
  await expect.poll(() => quotaReason).toBe('annual renewal')

  await page.locator('[data-action="enter-7"]:visible').click()
  await expect(page.getByRole('status')).toContainText('Demo Corporation')
  await page.locator('[data-action="leave-context"]').click()
  await expect(page.getByRole('status')).toHaveCount(0)

  await page.goto('/admin/audit-logs')
  await expect(page.getByText('TENANT_QUOTA_ADJUST')).toBeVisible()
  await expect(page.getByText('trace-audit')).toBeVisible()
})
