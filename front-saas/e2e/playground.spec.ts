import { expect, test } from '@playwright/test'

const user = { id: 2, username: 'demo_admin', nickname: 'Demo Admin', role: 'tenant_admin', tenant_id: 1, tenant_name: 'Demo', permissions: ['tenant:playground:use','tenant:proxy_key:manage'] }
const envelope = (data: unknown) => JSON.stringify({ code: 0, message: 'success', data, trace_id: 'trace-e2e' })

test('generates a proxy key once and runs a streaming playground response', async ({ page }) => {
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ contentType: 'application/json', body: envelope({ access_token: 'jwt', expires_in: 900, user }) }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ contentType: 'application/json', body: envelope(user) }))
  await page.route('**/api/v1/tenant/proxy-keys', async route => {
    if (route.request().method() === 'POST') await route.fulfill({ contentType: 'application/json', body: envelope({ api_key: 'sk-miniapi-demo-0123456789abcdef', created_at: '2026-08-13T00:00:00Z' }) })
    else await route.fulfill({ contentType: 'application/json', body: envelope([{ api_key_masked: 'sk-miniapi-demo-...cdef', created_at: '2026-08-13T00:00:00Z' }]) })
  })

  await page.goto('/proxy-keys')
  await page.getByRole('button', { name: '生成密钥' }).click()
  await expect(page.getByRole('dialog')).toContainText('sk-miniapi-demo-0123456789abcdef')
  await page.getByRole('button', { name: '取消' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.getByText('sk-miniapi-demo-...cdef')).toBeVisible()
})

test('streams deltas into the playground output', async ({ page }) => {
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ contentType: 'application/json', body: envelope({ access_token: 'jwt', expires_in: 900, user }) }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ contentType: 'application/json', body: envelope(user) }))
  await page.route('**/v1/chat/completions', route => route.fulfill({
    status: 200, contentType: 'text/event-stream',
    body: 'data: {"choices":[{"delta":{"content":"hel"}}]}\n\ndata: {"choices":[{"delta":{"content":"lo"}}]}\n\ndata: [DONE]\n\n',
  }))
  await page.goto('/playground')
  await page.getByLabel('模型').fill('qwen-max')
  await page.getByLabel('代理密钥').fill('sk-test')
  await page.getByLabel('提示词').fill('hi')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByRole('log')).toContainText('hello')
})
