import { expect } from '@playwright/test';
import { test, api, embedded, connect, exportDocument, fixture, importDocument, openDraft, selectEdge } from './helpers';

test.beforeEach(async ({ request }) => {
  // Every case starts from the seeded runtime definition, regardless of execution order.
  const seed = await (await request.get(`${api}/1`)).json();
  const draft = await (await request.post(api, { data: seed.definition })).json();
  expect((await request.post(`${api}/${draft.version}/publish`)).ok()).toBe(true);
});

test('load failure can be retried', async ({ page }) => {
  await page.route(api, (route) => route.fulfill({ status: 503, json: { message: 'Temporarily unavailable' } }));
  await page.goto(`${embedded}/fsm-editor/`);
  await expect(page.getByRole('status')).toHaveText('Temporarily unavailable');
  await page.unroute(api);
  await page.getByRole('button', { name: 'Retry', exact: true }).click();
  await expect(page.getByLabel('Order version')).toBeVisible();
});

test('active version is read only, including keyboard and graph interaction lock', async ({ page }) => {
  await page.goto(`${embedded}/fsm-editor/`);
  await expect(page.getByLabel('Order version')).toBeVisible();
  for (const name of ['Save draft', 'Publish', 'Delete draft', 'Add state', 'Import JSON']) {
    await expect(page.getByRole('button', { name, exact: true })).toBeDisabled();
  }
  await expect(page.getByLabel('Name', { exact: true })).toBeDisabled();
  const before = await exportDocument(page);
  await page.locator('.react-flow__node').first().click();
  await page.keyboard.press('Backspace');
  await expect(page.getByRole('button', { name: 'Delete selected', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Toggle Interactivity', exact: true }).click();
  const node = page.locator('.react-flow__node').first();
  const box = (await node.boundingBox())!;
  await page.mouse.move(box.x + 50, box.y + 15);
  await page.mouse.down();
  await page.mouse.move(box.x + 140, box.y + 80, { steps: 10 });
  await page.mouse.up();
  expect(await exportDocument(page)).toEqual(before);
});

test('build SENT and FAILED branches and add two condition beans through the editor', async ({ page, request }) => {
  await page.goto(`${embedded}/fsm-editor/`);
  await page.getByLabel('Order version').selectOption('1');
  await page.getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Draft created');
  await page.locator('.react-flow__node').filter({ hasText: /^COMPLETED$/ }).click();
  await page.getByLabel('Label', { exact: true }).fill('SENT');
  await page.getByRole('button', { name: 'Add state', exact: true }).click();
  await page.getByLabel('Label', { exact: true }).fill('FAILED');
  for (const [index, name] of ['orderApproved', 'orderNotApproved'].entries()) {
    await page.getByRole('button', { name: 'Add guard', exact: true }).click();
    await page.getByLabel(`Guard ID ${index + 1}`, { exact: true }).fill(name);
  }
  await page.getByRole('button', { name: 'Add action', exact: true }).click();
  await page.getByLabel('Action ID 1', { exact: true }).fill('notifyOrderCompleted');
  let document = await exportDocument(page);
  const pending = document.states.find((state) => state.label === 'IN_PROGRESS')!.id;
  const failed = document.states.find((state) => state.label === 'FAILED')!.id;
  const sent = document.transitions.find((edge) => edge.trigger.kind === 'event' && edge.trigger.event === 'FINISH')!;
  await selectEdge(page, sent.id);
  await page.locator('.selected-panel').getByLabel('orderApproved', { exact: true }).check();
  await page.locator('.selected-panel').getByRole('group', { name: 'Post actions', exact: true })
    .getByLabel('notifyOrderCompleted').check();
  await page.getByRole('button', { name: 'Fit View', exact: true }).click();
  await connect(page, pending, failed);
  document = await exportDocument(page);
  await selectEdge(page, document.transitions.find((edge) => edge.to === failed)!.id);
  await page.locator('.selected-panel').getByRole('button', { name: 'Event', exact: true }).click();
  await page.locator('.selected-panel').getByRole('combobox', { name: 'Event', exact: true }).selectOption('FINISH');
  await page.locator('.selected-panel').getByLabel('orderNotApproved', { exact: true }).check();
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toHaveText('Draft and layout saved.');
  const version = await page.getByLabel('Order version').inputValue();
  await page.reload();
  await page.getByLabel('Order version').selectOption(version);
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Published');
  for (const approved of [true, false]) {
    const order = await (await request.post(`${embedded}/api/orders`, { data: { approved } })).json();
    for (const event of ['SUBMIT', 'FINISH']) {
      expect((await request.post(`${embedded}/api/orders/${order.id}/events`, { data: { event } })).status()).toBe(200);
    }
    expect(await (await request.get(`${embedded}/api/orders/${order.id}`)).json()).toMatchObject({
      flowVersion: Number(version), state: approved ? 'SENT' : 'FAILED', notificationSent: approved,
    });
  }
});

test('create, discard, save and reload preserve coordinates and multiline description', async ({ page, request }) => {
  const version = await openDraft(page);
  const original = await exportDocument(page);
  await page.getByLabel('Name', { exact: true }).fill('Discard me');
  await expect(page.getByLabel('Order version')).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Create draft', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Discard changes' }).click();
  expect(await exportDocument(page)).toEqual(original);
  await page.locator('.react-flow__node').first().click();
  await page.getByLabel('Description').fill('Order\n    retain indentation');
  const box = (await page.locator('.react-flow__node').first().boundingBox())!;
  await page.mouse.move(box.x + 40, box.y + 15);
  await page.mouse.down();
  await page.mouse.move(box.x + 140, box.y + 95, { steps: 10 });
  await page.mouse.up();
  const edited = await exportDocument(page);
  expect(edited.states[0].position).not.toEqual(original.states[0].position);
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toHaveText('Draft and layout saved.');
  await page.reload();
  await page.getByLabel('Order version').selectOption(String(version));
  expect(await exportDocument(page)).toEqual(edited);
  const stored = await (await request.get(`${api}/${version}`)).json();
  expect(stored.definition.editor.states).toEqual(edited.states);
});

test('delete draft can be cancelled, removes dirty draft and never reuses its version', async ({ page, request }) => {
  const version = await openDraft(page);
  await page.getByLabel('Name', { exact: true }).fill('Unsaved name');
  page.once('dialog', (dialog) => dialog.dismiss());
  await page.getByRole('button', { name: 'Delete draft', exact: true }).click();
  await expect(page.getByLabel('Name', { exact: true })).toHaveValue('Unsaved name');
  page.once('dialog', async (dialog) => {
    expect(dialog.message()).toContain('Unsaved changes');
    await dialog.accept();
  });
  await page.getByRole('button', { name: 'Delete draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toHaveText('Draft deleted.');
  await expect(page.getByLabel('Order version').locator(`option[value="${version}"]`)).toHaveCount(0);
  expect((await request.get(`${api}/${version}`)).status()).toBe(404);
  expect((await request.delete(`${api}/${version}`)).status()).toBe(404);
  expect((await request.post(`${api}/${version}/publish`)).status()).toBe(404);
  expect((await request.put(`${api}/${version}`, { data: (await (await request.get(`${api}/1`)).json()).definition })).status()).toBe(409);
  await page.reload();
  await expect(page.getByLabel('Order version').locator(`option[value="${version}"]`)).toHaveCount(0);
  await page.getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Draft created');
  expect(Number(await page.getByLabel('Order version').inputValue())).toBeGreaterThan(version);
});

test('save, publish, create and delete API failures retain the editable draft', async ({ page }) => {
  const version = await openDraft(page);
  const fail = async (route: import('@playwright/test').Route) => route.fulfill({ status: 409, json: { message: 'Test conflict' } });
  await page.getByLabel('Name', { exact: true }).fill('Keep my edit');
  await page.route(`${api}/${version}`, fail);
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Test conflict');
  await expect(page.getByLabel('Name', { exact: true })).toHaveValue('Keep my edit');
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Delete draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Test conflict');
  await expect(page.getByLabel('Name', { exact: true })).toHaveValue('Keep my edit');
  await page.unroute(`${api}/${version}`);
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Save draft', exact: true })).toBeDisabled();
  await page.route(`${api}/${version}/publish`, fail);
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Test conflict');
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeEnabled();
  await page.route(api, fail);
  await page.getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Test conflict');
  await expect(page.getByLabel('Order version')).toHaveValue(String(version));
});

test('publication validates Spring bean references', async ({ page }) => {
  await openDraft(page);
  await page.getByRole('button', { name: 'Add guard', exact: true }).click();
  const guard = page.getByLabel(/^Guard ID /).last();
  await guard.fill('nonexistentGuard');
  const document = await exportDocument(page);
  await selectEdge(page, document.transitions[0].id);
  await page.locator('.selected-panel').getByLabel('nonexistentGuard').check();
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText(/nonexistentGuard|bean/i);
  await expect(page.getByRole('button', { name: 'Delete draft', exact: true })).toBeEnabled();
});

test('pending save prevents duplicate operations and editing', async ({ page }) => {
  const version = await openDraft(page);
  await page.getByLabel('Name', { exact: true }).fill('Pending save');
  let release!: () => void;
  const pending = new Promise<void>((done) => { release = done; });
  await page.route(`${api}/${version}`, async (route) => { await pending; await route.continue(); });
  try {
    await page.getByRole('button', { name: 'Save draft', exact: true }).click();
    for (const name of ['Save draft', 'Create draft', 'Publish', 'Delete draft', 'Discard changes', 'Add state', 'Import JSON']) {
      await expect(page.getByRole('button', { name, exact: true })).toBeDisabled();
    }
    await expect(page.getByLabel('Name', { exact: true })).toBeDisabled();
    await expect(page.getByLabel('Order version')).toBeDisabled();
  } finally { release(); }
  await expect(page.getByRole('status').first()).toHaveText('Draft and layout saved.');
  await expect(page.getByLabel('Name', { exact: true })).toBeEnabled();
});

test('invalid graph and automatic limit are rejected before saving', async ({ page, request }) => {
  const version = await openDraft(page);
  const original = await (await request.get(`${api}/${version}`)).json();
  await page.getByLabel('Auto transitions', { exact: true }).check();
  await page.getByLabel('Automatic transition limit').fill('0');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Automatic transition limit');
  expect(await (await request.get(`${api}/${version}`)).json()).toEqual(original);
  await page.getByRole('button', { name: 'Discard changes' }).click();
  await page.locator('.react-flow__node').filter({ hasText: /^NEW$/ }).click();
  await page.getByLabel('Label', { exact: true }).fill('COMPLETED');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Duplicate state label');
  expect(await (await request.get(`${api}/${version}`)).json()).toEqual(original);
});

test('local auto executes the eventless transition and switching to Event remains publishable', async ({ page, request }) => {
  await openDraft(page);
  await importDocument(page, fixture);
  await selectEdge(page);
  await page.locator('.selected-panel').getByRole('button', { name: 'Auto', exact: true }).click();
  await page.getByLabel('Run this transition automatically even when global auto transitions are disabled').check();
  await page.getByLabel('Automatic transition limit').fill('10');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Published');
  const automaticOrder = await request.post(`${embedded}/api/orders`);
  expect(automaticOrder.ok()).toBe(true);
  expect(await automaticOrder.json()).toMatchObject({ state: 'B' });

  await page.getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Draft created');
  await selectEdge(page);
  await page.locator('.selected-panel').getByRole('button', { name: 'Event', exact: true }).click();
  expect((await exportDocument(page)).transitions[0].autoTransitionEnabled).toBe(false);
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Published');
  const order = await (await request.post(`${embedded}/api/orders`)).json();
  expect(order.state).toBe('A');
  const handled = await request.post(`${embedded}/api/orders/${order.id}/events`, { data: { event: 'GO' } });
  expect(handled.ok()).toBe(true);
  expect(await handled.json()).toMatchObject({ state: 'B' });
});

test('unsaved navigation warns and cancellation keeps edits', async ({ page }) => {
  await openDraft(page);
  await page.getByLabel('Name', { exact: true }).fill('Keep before reload');
  const warning = page.waitForEvent('dialog');
  const reload = page.evaluate(() => window.location.reload());
  const dialog = await warning;
  expect(dialog.type()).toBe('beforeunload');
  await dialog.dismiss();
  await reload;
  await expect(page.getByLabel('Name', { exact: true })).toHaveValue('Keep before reload');
  page.once('dialog', (dialog) => dialog.accept());
  await page.reload();
  await expect(page.getByLabel('Name', { exact: true })).not.toHaveValue('Keep before reload');
});

test('publish changes new orders while existing orders retain their version; archive cannot be deleted', async ({ page, request }) => {
  const oldOrder = await (await request.post(`${embedded}/api/orders`)).json();
  const version = await openDraft(page);
  await page.locator('.react-flow__node').filter({ hasText: /^NEW$/ }).click();
  await page.getByLabel('Label', { exact: true }).fill('NEW_VISUAL');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Published');
  const newOrder = await (await request.post(`${embedded}/api/orders`)).json();
  expect(newOrder).toMatchObject({ state: 'NEW_VISUAL', flowVersion: version });
  expect(await (await request.get(`${embedded}/api/orders/${oldOrder.id}`)).json()).toMatchObject(oldOrder);
  expect((await request.delete(`${api}/${version}`)).status()).toBe(409);
  expect((await request.delete(`${api}/${oldOrder.flowVersion}`)).status()).toBe(409);
  await page.getByLabel('Order version').selectOption(String(oldOrder.flowVersion));
  await expect(page.getByLabel('Name', { exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Delete draft', exact: true })).toBeDisabled();
});
