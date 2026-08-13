import { expect, test } from '@playwright/test'

const user = { id: 2, username: 'demo_admin', nickname: 'Demo Admin', role: 'tenant_admin', tenant_id: 1, tenant_name: 'Demo Corporation', permissions: ['tenant:playground:use','tenant:routing:write'] }

test.beforeEach(async ({ page }) => {
  let authenticated = false
  await page.route('**/api/v1/auth/refresh', async route => route.fulfill({ status: authenticated ? 200 : 401, contentType: 'application/json', body: authenticated ? JSON.stringify({ code: 0, message: 'success', data: { access_token: 'restored', expires_in: 900, user } }) : JSON.stringify({ code: 401, message: 'Unauthorized', error_code: 'UNAUTHORIZED' }) }))
  await page.route('**/api/v1/auth/login', async route => { authenticated = true; await route.fulfill({ status: 200, headers: { 'set-cookie': 'miniapi_refresh=test; Path=/api/v1/auth; HttpOnly; SameSite=Lax' }, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'success', data: { access_token: 'jwt', expires_in: 900, user } }) }) })
  await page.route('**/api/v1/auth/me', async route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'success', data: user }) }))
})

test('signs in and reaches the responsive workspace', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo_admin')
  await page.getByLabel('密码').fill('correct-password')
  await page.getByLabel('租户编码').fill('demo')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/overview$/)
  await expect(page.getByText('Routing overview')).toBeVisible()
  await expect(page.evaluate(() => localStorage.getItem('access_token'))).resolves.toBeNull()
})
