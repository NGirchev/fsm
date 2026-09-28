import { expect } from '@playwright/test';
import { test, editor, api, embedded, connect, exportDocument, fixture, importDocument, openDraft, selectEdge, expandSection } from './helpers';

test.beforeEach(async ({ request }) => {
  // Every case starts from the seeded runtime definition, regardless of execution order.
  const seed = await (await request.get(`${api}/1`)).json();
  const draft = await (await request.post(api, { data: seed.definition })).json();
  expect((await request.post(`${api}/${draft.version}/publish`)).ok()).toBe(true);
});

test('load failure can be retried', async ({ page }) => {
  await page.route(api, (route) => route.fulfill({ status: 503, json: { message: 'Temporarily unavailable' } }));
  await page.goto(`${embedded}/#flow`);
  await expect(page.getByRole('status')).toHaveText('Temporarily unavailable');
  await page.unroute(api);
  await page.getByRole('button', { name: 'Retry', exact: true }).click();
  await expect(page.getByLabel('Order version')).toBeVisible();
});

test('active version is read only, including keyboard and graph interaction lock', async ({ page }) => {
  await page.goto(`${embedded}/#flow`);
  await expect(page.getByLabel('Order version')).toBeVisible();
  for (const name of ['Save draft', 'Publish', 'Delete draft', 'Add state', 'Import JSON']) {
    await expect((['Save draft', 'Create draft', 'Publish', 'Delete draft', 'Discard changes'].includes(name) ? page : editor(page)).getByRole('button', { name, exact: true })).toBeDisabled();
  }
  await expect(editor(page).getByLabel('Name', { exact: true })).toBeDisabled();
  const before = await exportDocument(page);
  await editor(page).locator('.react-flow__node').first().click();
  await page.keyboard.press('Backspace');
  await expect(editor(page).getByRole('button', { name: 'Delete selected', exact: true })).toBeDisabled();
  await editor(page).getByRole('button', { name: 'Toggle Interactivity', exact: true }).click();
  const node = editor(page).locator('.react-flow__node').first();
  const box = (await node.boundingBox())!;
  await page.mouse.move(box.x + 50, box.y + 15);
  await page.mouse.down();
  await page.mouse.move(box.x + 140, box.y + 80, { steps: 10 });
  await page.mouse.up();
  expect(await exportDocument(page)).toEqual(before);
});

test('build amount branches and add two condition beans through the editor', async ({ page, request }) => {
  await page.goto(`${embedded}/#flow`);
  await page.getByLabel('Order version').selectOption('1');
  await page.getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Draft created');
  await editor(page).locator('.react-flow__node').filter({ hasText: /^COMPLETED$/ }).click();
  await editor(page).getByLabel('Label', { exact: true }).fill('SENT');
  await editor(page).getByRole('button', { name: 'Add state', exact: true }).click();
  await editor(page).getByLabel('Label', { exact: true }).fill('FAILED');
  let document = await exportDocument(page);
  const pending = document.states.find((state) => state.label === 'IN_PROGRESS')!.id;
  const failed = document.states.find((state) => state.label === 'FAILED')!.id;
  const sentState = document.states.find((state) => state.label === 'SENT')!.id;
  const sent = document.transitions.find((edge) => edge.trigger.kind === 'event' && edge.trigger.event === 'FINISH')!;
  // This test builds its own branches from a clean transition; the seeded flow already has amount-based behavior.
  sent.to = sentState;
  sent.conditions = [];
  sent.actions = [];
  sent.postActions = [];
  document.transitions = document.transitions.filter((edge) =>
    edge.id === sent.id || (edge.trigger.kind === 'event' && edge.trigger.event === 'SUBMIT'));
  await importDocument(page, document);
  await selectEdge(page, sent.id);
  await expect(editor(page).getByRole('button', { name: 'Add selected bean', exact: true })).toHaveCount(0);
  await expect(editor(page).getByRole('button', { name: 'Add guard', exact: true })).toHaveCount(0);
  const catalog: { id: string; kind: string; description: string }[] =
    await (await request.get(`${embedded}/api/flows/order/behaviors`)).json();
  await expandSection(page, 'Execution settings');
  for (const [group, kind] of [['Guards', 'guard'], ['Actions', 'action'], ['Post actions', 'action'],
    ['State listeners', 'stateListener'], ['Completion listeners', 'completionListener']]) {
    const picker = editor(page).getByLabel(`Add ${group}`, { exact: true });
    const beans = catalog.filter((bean) => bean.kind === kind);
    const available = group === 'State listeners' ? beans.filter((bean) => bean.id !== 'recordOrderHistory') : beans;
    expect(await picker.locator('option').evaluateAll((options) => options.slice(1).map((option) => option.textContent)))
      .toEqual(available.map((bean) => bean.id));
    for (const bean of available) {
      await expect(picker.locator(`option[value="${bean.id}"]`)).toHaveAttribute('title', bean.description);
    }
  }
  await editor(page).getByLabel('Add Guards', { exact: true }).selectOption('amountBelowCommissionThreshold');
  await editor(page).getByLabel('Guards 1', { exact: true }).hover();
  await expect(editor(page).getByLabel('Guards 1', { exact: true }))
    .toHaveAttribute('title', catalog.find((bean) => bean.id === 'amountBelowCommissionThreshold')!.description);
  await editor(page).getByLabel('Guards 1', { exact: true }).selectOption('amountAtLeastCommissionThreshold');
  await expect(editor(page).getByLabel('Guards 1', { exact: true }))
    .toHaveAttribute('title', catalog.find((bean) => bean.id === 'amountAtLeastCommissionThreshold')!.description);
  await editor(page).getByLabel('Guards 1', { exact: true }).selectOption('amountBelowCommissionThreshold');
  await editor(page).getByLabel('Add Actions', { exact: true }).selectOption('commissionTwoPercent');
  await editor(page).getByLabel('Add Post actions', { exact: true }).selectOption('logOrderNotification');
  await editor(page).getByRole('button', { name: 'Fit View', exact: true }).click();
  await connect(page, pending, failed);
  document = await exportDocument(page);
  await selectEdge(page, document.transitions.find((edge) => edge.to === failed)!.id);
  await editor(page).locator('.selected-panel').getByRole('button', { name: 'Event', exact: true }).click();
  await editor(page).locator('.selected-panel').getByRole('combobox', { name: 'Event', exact: true }).selectOption('FINISH');
  await editor(page).getByLabel('Add Guards', { exact: true }).selectOption('amountAtLeastCommissionThreshold');
  await editor(page).getByLabel('Add Actions', { exact: true }).selectOption('commissionOnePercent');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toHaveText('Draft and layout saved.');
  const version = await page.getByLabel('Order version').inputValue();
  await page.reload();
  await page.getByLabel('Order version').selectOption(version);
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Published');
  for (const [amount, state, commission] of [
    ['100.00', 'SENT', 2], ['1000.00', 'FAILED', 10],
  ] as const) {
    const order = await (await request.post(`${embedded}/api/orders`, { data: { amount } })).json();
    for (const event of ['SUBMIT', 'FINISH']) {
      expect((await request.post(`${embedded}/api/orders/${order.id}/events`, { data: { event } })).status()).toBe(200);
    }
    expect(await (await request.get(`${embedded}/api/orders/${order.id}`)).json()).toMatchObject({
      flowVersion: Number(version), state, commission,
    });
  }
});

test('create, discard, save and reload preserve coordinates and multiline description', async ({ page, request }) => {
  const version = await openDraft(page);
  const original = await exportDocument(page);
  await editor(page).getByLabel('Name', { exact: true }).fill('Discard me');
  await expect(page.getByLabel('Order version')).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Create draft', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Discard changes' }).click();
  expect(await exportDocument(page)).toEqual(original);
  await editor(page).locator('.react-flow__node').first().click();
  await editor(page).getByLabel('Description').fill('Order\n    retain indentation');
  const box = (await editor(page).locator('.react-flow__node').first().boundingBox())!;
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

test('delete draft can be cancelled, removes dirty draft and reuses its highest version', async ({ page, request }) => {
  const version = await openDraft(page);
  await editor(page).getByLabel('Name', { exact: true }).fill('Unsaved name');
  page.once('dialog', (dialog) => dialog.dismiss());
  await page.getByRole('button', { name: 'Delete draft', exact: true }).click();
  await expect(editor(page).getByLabel('Name', { exact: true })).toHaveValue('Unsaved name');
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
  expect(Number(await page.getByLabel('Order version').inputValue())).toBe(version);
});

test('save, publish, create and delete API failures retain the editable draft', async ({ page }) => {
  const version = await openDraft(page);
  const fail = async (route: import('@playwright/test').Route) => route.fulfill({ status: 409, json: { message: 'Test conflict' } });
  await editor(page).getByLabel('Name', { exact: true }).fill('Keep my edit');
  await page.route(`${api}/${version}`, fail);
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Test conflict');
  await expect(editor(page).getByLabel('Name', { exact: true })).toHaveValue('Keep my edit');
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Delete draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Test conflict');
  await expect(editor(page).getByLabel('Name', { exact: true })).toHaveValue('Keep my edit');
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
  const document = await exportDocument(page);
  document.behaviors.conditions.push({ id: 'nonexistentGuard' });
  document.transitions[0].conditions.push('nonexistentGuard');
  await importDocument(page, document);
  await selectEdge(page, document.transitions[0].id);
  await expect(editor(page).getByLabel('Guards 1', { exact: true })).toHaveValue('nonexistentGuard');
  await expect(editor(page).getByLabel('Guards 1', { exact: true })).toHaveAttribute('title', 'Unavailable bean: nonexistentGuard');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText(/nonexistentGuard|bean/i);
  await expect(page.getByRole('button', { name: 'Delete draft', exact: true })).toBeEnabled();
});

test('pending save prevents duplicate operations and editing', async ({ page }) => {
  const version = await openDraft(page);
  await editor(page).getByLabel('Name', { exact: true }).fill('Pending save');
  let release!: () => void;
  const pending = new Promise<void>((done) => { release = done; });
  await page.route(`${api}/${version}`, async (route) => { await pending; await route.continue(); });
  try {
    await page.getByRole('button', { name: 'Save draft', exact: true }).click();
    for (const name of ['Save draft', 'Create draft', 'Publish', 'Delete draft', 'Discard changes', 'Add state', 'Import JSON']) {
      await expect((['Save draft', 'Create draft', 'Publish', 'Delete draft', 'Discard changes'].includes(name) ? page : editor(page)).getByRole('button', { name, exact: true })).toBeDisabled();
    }
    await expect(editor(page).getByLabel('Name', { exact: true })).toBeDisabled();
    await expect(page.getByLabel('Order version')).toBeDisabled();
  } finally { release(); }
  await expect(page.getByRole('status').first()).toHaveText('Draft and layout saved.');
  await expect(editor(page).getByLabel('Name', { exact: true })).toBeEnabled();
});

test('invalid graph and negative automatic limit are rejected before saving', async ({ page, request }) => {
  const version = await openDraft(page);
  const original = await (await request.get(`${api}/${version}`)).json();
  await editor(page).getByLabel('Auto transitions', { exact: true }).check();
  await editor(page).getByLabel('Automatic transition limit').fill('-1');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Automatic transition limit');
  expect(await (await request.get(`${api}/${version}`)).json()).toEqual(original);
  await page.getByRole('button', { name: 'Discard changes' }).click();
  await editor(page).locator('.node-name').filter({ hasText: /^NEW$/ }).click();
  await editor(page).getByLabel('Label', { exact: true }).fill('COMPLETED');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Duplicate state label');
  expect(await (await request.get(`${api}/${version}`)).json()).toEqual(original);
});

test('event length validation blocks saving and accepts the 120-character boundary', async ({ page, request }) => {
  const version = await openDraft(page);
  const original = await (await request.get(`${api}/${version}`)).json();
  const input = editor(page).getByLabel('Event ID 1', { exact: true });
  await input.fill('E'.repeat(121));
  await expect(editor(page).locator('.validation-panel')).toContainText('Event ID must contain at most 120 characters.');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.locator('#flow-message')).toContainText('Event ID must contain at most 120 characters.');
  expect(await (await request.get(`${api}/${version}`)).json()).toEqual(original);
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeDisabled();
  await input.fill('E'.repeat(120));
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.locator('#flow-message')).toHaveText('Draft and layout saved.');
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.locator('#flow-message')).toContainText('Published');
  const created = await request.post(`${embedded}/api/orders`, { data: { amount: 100 } });
  expect(created.status()).toBe(201);
  const order = await created.json();
  expect(order.flowVersion).toBe(version);
  const handled = await request.post(`${embedded}/api/orders/${order.id}/events`, { data: { event: 'E'.repeat(120) } });
  expect(handled.status()).toBe(200);
});

test('local auto executes the eventless transition and switching to Event remains publishable', async ({ page, request }) => {
  await openDraft(page);
  await importDocument(page, fixture);
  await selectEdge(page);
  await editor(page).locator('.selected-panel').getByRole('button', { name: 'Auto', exact: true }).click();
  await editor(page).getByLabel('Run this transition automatically even when global auto transitions are disabled').check();
  await editor(page).getByLabel('Automatic transition limit').fill('0');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Published');
  const automaticOrder = await request.post(`${embedded}/api/orders`, { data: { amount: '100.00' } });
  expect(automaticOrder.ok()).toBe(true);
  expect(await automaticOrder.json()).toMatchObject({ state: 'B' });

  await page.getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Draft created');
  await selectEdge(page);
  await editor(page).locator('.selected-panel').getByRole('button', { name: 'Event', exact: true }).click();
  expect((await exportDocument(page)).transitions[0].autoTransitionEnabled).toBe(false);
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Published');
  const order = await (await request.post(`${embedded}/api/orders`, { data: { amount: '100.00' } })).json();
  expect(order.state).toBe('A');
  const handled = await request.post(`${embedded}/api/orders/${order.id}/events`, { data: { event: 'GO' } });
  expect(handled.ok()).toBe(true);
  expect(await handled.json()).toMatchObject({ state: 'B' });
});

test('new Auto transition stays automatic after saving and reloading the example flow', async ({ page, request }) => {
  const version = await openDraft(page);
  await importDocument(page, fixture);
  await connect(page, 'b', 'c');

  const created = (await exportDocument(page)).transitions.find((transition) => transition.from === 'b')!;
  expect(created.trigger).toEqual({ kind: 'auto' });
  expect(created.autoTransitionEnabled).toBe(true);

  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toHaveText('Draft and layout saved.');
  const saved = await (await request.get(`${api}/${version}`)).json();
  expect(saved.definition.table.transitions.B[0].event ?? null).toBeNull();
  expect(saved.definition.table.transitions.B[0].to).toMatchObject({ state: 'C', autoTransitionEnabled: true });

  await page.reload();
  await page.getByLabel('Order version').selectOption(String(version));
  expect((await exportDocument(page)).transitions.find((transition) => transition.from === 'b')?.autoTransitionEnabled).toBe(true);

  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Published');
  const order = await (await request.post(`${embedded}/api/orders`, { data: { amount: '100.00' } })).json();
  expect(order.state).toBe('A');
  const result = await request.post(`${embedded}/api/orders/${order.id}/events`, { data: { event: 'GO' } });
  expect(result.ok(), await result.text()).toBe(true);
  expect(await result.json()).toMatchObject({ state: 'C' });
});

test('unsaved navigation warns and cancellation keeps edits', async ({ page }) => {
  await openDraft(page);
  await editor(page).getByLabel('Name', { exact: true }).fill('Keep before reload');
  const warning = page.waitForEvent('dialog');
  const reload = page.evaluate(() => window.location.reload());
  const dialog = await warning;
  expect(dialog.type()).toBe('beforeunload');
  await dialog.dismiss();
  await reload;
  await expect(editor(page).getByLabel('Name', { exact: true })).toHaveValue('Keep before reload');
  page.once('dialog', (dialog) => dialog.accept());
  await page.reload();
  await expect(editor(page).getByLabel('Name', { exact: true })).not.toHaveValue('Keep before reload');
});

test('publish changes new orders while existing orders retain their version; archive cannot be deleted', async ({ page, request }) => {
  const oldOrder = await (await request.post(`${embedded}/api/orders`, { data: { amount: '100.00' } })).json();
  const version = await openDraft(page);
  await editor(page).locator('.node-name').filter({ hasText: /^NEW$/ }).click();
  await editor(page).getByLabel('Label', { exact: true }).fill('NEW_VISUAL');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Published');
  const newOrder = await (await request.post(`${embedded}/api/orders`, { data: { amount: '100.00' } })).json();
  expect(newOrder).toMatchObject({ state: 'NEW_VISUAL', flowVersion: version });
  expect(await (await request.get(`${embedded}/api/orders/${oldOrder.id}`)).json()).toMatchObject(oldOrder);
  expect((await request.delete(`${api}/${version}`)).status()).toBe(409);
  expect((await request.delete(`${api}/${oldOrder.flowVersion}`)).status()).toBe(409);
  await page.getByLabel('Order version').selectOption(String(oldOrder.flowVersion));
  await expect(editor(page).getByLabel('Name', { exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Delete draft', exact: true })).toBeDisabled();
});
