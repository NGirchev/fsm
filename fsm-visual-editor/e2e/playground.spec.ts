import { expect } from '@playwright/test';
import { test, api, embedded } from './helpers';

test.beforeEach(async ({ request }) => {
  const seed = await (await request.get(`${api}/2`)).json();
  const definition = seed.definition;
  const draft = await (await request.post(api, { data: definition })).json();
  expect((await request.post(`${api}/${draft.version}/publish`)).ok()).toBe(true);
});

test('flow editor switches the active scenario while orders stay pinned to their version', async ({ page, request }) => {
  await page.goto(`${embedded}/#flow`);
  await page.getByLabel('Order version').selectOption('1');
  await expect(page.getByRole('button', { name: 'Make active', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Make active', exact: true }).click();
  await expect(page.locator('#flow-message')).toContainText('Active flow changed');
  await page.getByRole('tab', { name: 'Orders', exact: true }).click();
  await page.getByLabel('Amount', { exact: true }).fill('1000');
  await page.getByRole('button', { name: 'Run flow', exact: true }).click();
  await expect(page.locator('#test-outcome')).toHaveText('Scenario finished');
  await expect(page.locator('#test-summary')).toContainText('Commission: 20');
  await expect(page.locator('#test-steps li')).toHaveCount(3);
  const directId = Number((await page.locator('#order-title').textContent())!.match(/\d+/)![0]);
  await page.getByRole('tab', { name: 'Flow editor', exact: true }).click();
  await page.getByLabel('Order version').selectOption('2');
  await page.getByRole('button', { name: 'Make active', exact: true }).click();
  await expect(page.locator('#flow-message')).toContainText('Active flow changed');
  await page.getByRole('tab', { name: 'Orders', exact: true }).click();
  await page.getByRole('button', { name: 'Run flow', exact: true }).click();
  await expect(page.locator('#test-summary')).toContainText('Commission: 10');
  await expect(page.locator('#test-steps')).toContainText('COMMISSION_1_PERCENT');
  expect(await (await request.get(`${embedded}/api/orders/${directId}`)).json()).toMatchObject({ flowVersion: 1, commission: 20 });
});

test('one click runs the published flow and shows actual effects', async ({ page }, testInfo) => {
  await page.goto(embedded);
  await expect(page.getByLabel('Events to send, in order', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Create order', exact: true })).toHaveCount(0);
  await expect(page.getByLabel('Amount', { exact: true })).toHaveValue('100.00');
  await page.getByRole('button', { name: 'Run flow', exact: true }).click();
  await expect(page.locator('#test-outcome')).toHaveText('Scenario finished');
  const steps = page.locator('#test-steps');
  await expect(steps.locator('li')).toHaveCount(4);
  await expect(steps).toContainText('NEW → IN_PROGRESS');
  await expect(steps).toContainText('IN_PROGRESS → COMMISSION_2_PERCENT');
  await expect(steps).toContainText('COMMISSION_2_PERCENT → COMPLETED');
  await expect(steps).toContainText('amountBelowCommissionThreshold');
  await expect(steps).toContainText('commissionTwoPercent');
  await expect(steps).toContainText('Commission: 0 → 2');
  await expect(page.locator('#test-summary')).toContainText('Final state: COMPLETED');
  await expect(page.locator('#order-history')).toContainText('SUBMIT');
  await expect(page.locator('#order-history')).toContainText('FINISH');
  const orderTitle = await page.locator('#order-title').textContent();
  await page.reload();
  await expect(page.locator('#order-message')).toHaveText('Last order loaded with its saved history.');
  await expect(page.locator('#order-title')).toHaveText(orderTitle!);
  await expect(page.locator('#order-history')).toContainText('SUBMIT');
  await expect(page.locator('#order-history')).toContainText('FINISH');
  await page.screenshot({ path: testInfo.outputPath('playground-result.png'), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('amount 1000 selects the one-percent commission branch', async ({ page }) => {
  await page.goto(embedded);
  await page.getByLabel('Amount', { exact: true }).fill('1000');
  await page.getByRole('button', { name: 'Run flow', exact: true }).click();
  await expect(page.locator('#test-outcome')).toHaveText('Scenario finished');
  await expect(page.locator('#test-steps')).toContainText('IN_PROGRESS → COMMISSION_1_PERCENT');
  await expect(page.locator('#test-steps')).toContainText('amountAtLeastCommissionThreshold');
  await expect(page.locator('#test-summary')).toContainText('Commission: 10');
});

test('guard rejection stops the scenario without inventing successful steps', async ({ page }) => {
  await page.goto(embedded);
  await page.getByLabel('Amount', { exact: true }).fill('100.00');
  await page.route(`${embedded}/api/orders/*/events`, async (route) => {
    const body = route.request().postDataJSON();
    if (body.event === 'FINISH') await route.fulfill({ status: 409, json: { message: 'Guard rejected FINISH' } });
    else await route.continue();
  });
  await page.getByRole('button', { name: 'Run flow', exact: true }).click();
  await expect(page.locator('#test-outcome')).toHaveText('Stopped');
  await expect(page.locator('#test-steps li')).toHaveCount(3);
  await expect(page.locator('#test-steps')).toContainText('FINISH failed');
  await expect(page.locator('#test-summary')).toHaveText('Last confirmed state: IN_PROGRESS');
  await expect(page.locator('#order-fields [data-field="Commission"]')).toHaveText('0');
});
