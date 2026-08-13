import { expect, test } from '@playwright/test'

const admin = { id: 2, username: 'demo_admin', nickname: 'Demo Admin', role: 'tenant_admin', tenant_id: 1, tenant_name: 'Demo', permissions: ['tenant:routing:read','tenant:routing:write','tenant:upstream:read','tenant:upstream:write'] }
const envelope = (data: unknown) => JSON.stringify({ code: 0, message: 'success', data, trace_id: 'trace-e2e' })

test('tenant admin simulates intent routing and reviews models', async ({ page }) => {
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ contentType: 'application/json', body: envelope({ access_token: 'jwt', expires_in: 900, user: admin }) }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ contentType: 'application/json', body: envelope(admin) }))
  await page.route('**/api/v1/tenant/intents', route => route.fulfill({ contentType: 'application/json', body: envelope({ list: [{ id: 1, intent_name: 'coding_review', model_weights: { 'qwen-max': 80, 'glm-5': 40 } }], total: 1, page: 1, page_size: 20 }) }))
  await page.route('**/api/v1/tenant/api-keys/models', route => route.fulfill({ contentType: 'application/json', body: envelope([{ id: 7, display_name: 'qwen-max', upstream_name: 'Primary' }]) }))
  await page.route('**/api/v1/tenant/route-rules/simulate', route => route.fulfill({ contentType: 'application/json', body: envelope({ matched: true, matched_rule_name: 'Code tasks', selected_model: 'qwen-max', fallback_order: ['glm-5'], evaluated_intent: 'coding_review', trace: [{ id: 'request', detail: 'request', status: 'complete' }, { id: 'intent', detail: 'coding_review', status: 'active' }] }) }))

  await page.goto('/routing-rules')
  await page.getByLabel('意图').fill('coding_review')
  await page.getByRole('button', { name: '模拟路由' }).click()
  await expect(page.getByText('qwen-max', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('list')).toContainText('coding_review')
})

test('routing workspace is usable in English', async ({ page }) => {
  const enAdmin = { ...admin, nickname: 'Demo Admin' }
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ contentType: 'application/json', body: envelope({ access_token: 'jwt', expires_in: 900, user: enAdmin }) }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ contentType: 'application/json', body: envelope(enAdmin) }))
  await page.route('**/api/v1/tenant/intents', route => route.fulfill({ contentType: 'application/json', body: envelope({ list: [], total: 0, page: 1, page_size: 20 }) }))
  await page.route('**/api/v1/tenant/api-keys/models', route => route.fulfill({ contentType: 'application/json', body: envelope([]) }))
  await page.goto('/routing-rules')
  await page.getByRole('button', { name: '切换语言' }).click()
  await expect(page.getByRole('heading', { name: 'Routing rules' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Intent configuration' })).toBeVisible()
})
