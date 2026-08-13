import { expect, test } from '@playwright/test'

const admin = { id: 1, username: 'root', nickname: 'Platform Admin', role: 'super_admin', tenant_id: 0, tenant_name: 'Platform', permissions: ['tenant:log:read','tenant:usage:read','platform:system:read','platform:system:write'] }
const envelope = (data: unknown) => JSON.stringify({ code: 0, message: 'success', data, trace_id: 'trace-e2e' })

test('super admin reviews logs, route trace, usage, and whitelisted config', async ({ page }) => {
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ contentType: 'application/json', body: envelope({ access_token: 'jwt', expires_in: 900, user: admin }) }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ contentType: 'application/json', body: envelope(admin) }))
  await page.route('**/api/v1/tenant/logs', route => route.fulfill({ contentType: 'application/json', body: envelope({ list: [{ id: 7, trace_id: 'trace-7', model: 'qwen-max', status: 'success', total_tokens: 300 }], total: 1, page: 1, page_size: 20 }) }))
  await page.route('**/api/v1/tenant/logs/7/route-trace', route => route.fulfill({ contentType: 'application/json', body: envelope({ trace: [{ id: 'request', detail: 'qwen-max', status: 'complete' }, { id: 'model', detail: 'qwen-max → openai', status: 'complete' }] }) }))
  await page.route('**/api/v1/tenant/usage/summary', route => route.fulfill({ contentType: 'application/json', body: envelope({ total_requests: 42, total_tokens: 900, success_rate: 0.9, model_distribution: [] }) }))
  await page.route('**/api/v1/tenant/usage/by-model', route => route.fulfill({ contentType: 'application/json', body: envelope([{ model: 'qwen-max', cnt: 9, tokens: 300 }]) }))
  await page.route('**/api/v1/admin/system/health', route => route.fulfill({ contentType: 'application/json', body: envelope({ status: 'UP', database: 'UP', redis: 'UP' }) }))
  await page.route('**/api/v1/admin/system/config', route => route.fulfill({ contentType: 'application/json', body: envelope({ log_retention_days: '30', default_retry_count: '1', default_upstream_timeout_ms: '30000' }) }))

  await page.goto('/logs')
  await page.getByRole('button', { name: '查看追踪' }).click()
  await expect(page.getByRole('dialog')).toContainText('qwen-max → openai')

  await page.goto('/usage')
  await expect(page.getByText('42', { exact: true })).toBeVisible()
  await expect(page.getByText('qwen-max: 9', { exact: false })).toBeVisible()

  await page.goto('/system/monitor')
  await expect(page.getByText('UP').first()).toBeVisible()
})

test('observability console is usable in English', async ({ page }) => {
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ contentType: 'application/json', body: envelope({ access_token: 'jwt', expires_in: 900, user: admin }) }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ contentType: 'application/json', body: envelope(admin) }))
  await page.route('**/api/v1/tenant/logs', route => route.fulfill({ contentType: 'application/json', body: envelope({ list: [], total: 0, page: 1, page_size: 20 }) }))
  await page.goto('/logs')
  await page.getByRole('button', { name: '切换语言' }).click()
  await expect(page.getByRole('heading', { name: 'Request logs' })).toBeVisible()
})
