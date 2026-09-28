import { expect, type Page, type APIRequestContext } from '@playwright/test';
import { test, editor, embedded, api, exportDocument, connect, selectEdge, admin } from './helpers';

async function prepareBasicActive(request: APIRequestContext) {
  const seed = await (await request.get(`${api}/1`)).json();
  const submit = seed.definition.table.transitions.NEW[0];
  const finish = structuredClone(seed.definition.table.transitions.IN_PROGRESS[0]);
  finish.to = { state: 'COMPLETED', conditions: [], actions: [], postActions: [], timeout: null };
  seed.definition.table = { autoTransitionEnabled: false, maxImmediateAutoTransitions: 0,
    transitions: { NEW: [submit], IN_PROGRESS: [finish], COMPLETED: [] } };
  delete seed.definition.editor;
  const draft = await (await request.post(api, { data: seed.definition })).json();
  expect((await request.post(`${api}/${draft.version}/publish`)).ok()).toBe(true);
}

async function choose(page: Page, group: string, id: string) {
  await editor(page).locator('.selected-panel').getByLabel(`Add ${group}`, { exact: true }).selectOption(id);
}

async function addState(page: Page, label: string) {
  await editor(page).getByRole('button', { name: 'Add state', exact: true }).click();
  await editor(page).getByLabel('Label', { exact: true }).fill(label);
}

async function addBranch(page: Page, from: string, to: string, event: string) {
  const document = await exportDocument(page);
  const source = document.states.find((state) => state.label === from)!.id;
  const target = document.states.find((state) => state.label === to)!.id;
  await editor(page).getByRole('button', { name: 'Fit View', exact: true }).click();
  await connect(page, source, target);
  const next = await exportDocument(page);
  const edge = next.transitions.find((item) => item.from === source && item.to === target && item.trigger.kind === 'auto')!;
  await editor(page).locator(`.flow-edge-label[data-id="${edge.id}"]`).dispatchEvent('click');
  await expect(editor(page).locator('.selected-panel h2')).toHaveText('Transition');
  await editor(page).locator('.selected-panel').getByRole('button', { name: 'Event', exact: true }).click();
  await editor(page).locator('.selected-panel').getByRole('combobox', { name: 'Event', exact: true }).selectOption(event);
  return edge.id;
}

async function publish(page: Page) {
  await admin(page).getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(admin(page).getByRole('status').first()).toHaveText('Draft and layout saved.');
  const version = await admin(page).getByLabel('Version').inputValue();
  const expected = await exportDocument(page);
  await page.reload();
  await admin(page).getByLabel('Version').selectOption(version);
  expect(await exportDocument(page)).toEqual(expected);
  await admin(page).getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(admin(page).getByRole('status').first()).toContainText('Published');
  return Number(version);
}

async function order(request: APIRequestContext, amount: string) {
  const response = await request.post(`${embedded}/api/orders`, { data: { amount } });
  expect(response.status()).toBe(201);
  return response.json();
}

async function event(request: APIRequestContext, id: number, name: string) {
  const response = await request.post(`${embedded}/api/orders/${id}/events`, {
    data: { event: name, source: 'browser-test', requestId: `${id}-${name}` },
  });
  expect(response.status()).toBe(200);
  return response.json();
}

test('replace commission beans in UI, preserve pinned orders and record history', async ({ page, request }, testInfo) => {
  test.setTimeout(90000);
  await prepareBasicActive(request);
  await page.goto(`${embedded}/#flow`);
  await admin(page).getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(admin(page).getByRole('status').first()).toContainText('Draft created');
  await editor(page).locator('.react-flow__node').filter({ hasText: /^COMPLETED$/ }).click();
  await editor(page).getByLabel('Label', { exact: true }).fill('DONE');
  await addState(page, 'DISCOUNTED');
  let document = await exportDocument(page);
  const belowThreshold = document.transitions.find((item) => item.trigger.kind === 'event' && item.trigger.event === 'FINISH')!.id;
  await selectEdge(page, belowThreshold);
  await choose(page, 'Guards', 'amountBelowCommissionThreshold');
  await choose(page, 'Actions', 'commissionTwoPercent');
  await choose(page, 'Post actions', 'logOrderNotification');
  const atLeastThreshold = await addBranch(page, 'IN_PROGRESS', 'DISCOUNTED', 'FINISH');
  await choose(page, 'Guards', 'amountAtLeastCommissionThreshold');
  await choose(page, 'Actions', 'commissionOnePercent');
  await choose(page, 'Post actions', 'logOrderNotification');
  const first = await publish(page);
  for (const [amount, fee, state] of [
    ['100.00', 2, 'DONE'], ['1000.00', 10, 'DISCOUNTED'],
  ] as const) {
    const created = await order(request, amount);
    await event(request, created.id, 'SUBMIT');
    expect(await event(request, created.id, 'FINISH')).toMatchObject({ commission: fee, state, flowVersion: first });
  }
  const pinned = await order(request, '100.00');
  await event(request, pinned.id, 'SUBMIT');

  await admin(page).getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(admin(page).getByRole('status').first()).toContainText('Draft created');
  for (const [id, oldBean, newBean] of [[belowThreshold, 'commissionTwoPercent', 'commissionOnePercent'],
    [atLeastThreshold, 'commissionOnePercent', 'commissionTwoPercent']]) {
    await selectEdge(page, id);
    await editor(page).getByRole('button', { name: `Remove Actions ${oldBean}`, exact: true }).click();
    await choose(page, 'Actions', newBean);
  }
  const second = await publish(page);
  expect(second).toBeGreaterThan(first);
  expect(await event(request, pinned.id, 'FINISH')).toMatchObject({ commission: 2, state: 'DONE', flowVersion: first });
  for (const [amount, commission, state] of [['100.00', 1, 'DONE'], ['1000.00', 20, 'DISCOUNTED']] as const) {
    const created = await order(request, amount);
    await event(request, created.id, 'SUBMIT');
    expect(await event(request, created.id, 'FINISH')).toMatchObject({ commission, state, flowVersion: second });
    const history = await (await request.get(`${embedded}/api/orders/${created.id}/history`)).json();
    expect(history).toEqual(expect.arrayContaining([expect.objectContaining({ event: 'FINISH', source: 'browser-test', requestId: `${created.id}-FINISH` })]));
    expect(history.filter((item: { kind: string }) => item.kind === 'STATE_CHANGED')).toHaveLength(2);
  }
  await admin(page).getByLabel('Version').selectOption(String(first));
  expect((await exportDocument(page)).transitions.find((item) => item.id === belowThreshold)?.actions).toEqual(['commissionTwoPercent']);
  await selectEdge(page, belowThreshold);
  await expect(editor(page).getByLabel('Add Guards', { exact: true })).toBeDisabled();
  await admin(page).getByLabel('Version').selectOption(String(second));
  await expect(editor(page).getByLabel('State listeners 1', { exact: true })).toHaveValue('recordOrderHistory');
  await page.screenshot({ path: testInfo.outputPath('order-components.png'), fullPage: true });
  expect((await (await request.get(`${api}/${second}`)).json()).definition.execution.stateListeners).toContain('recordOrderHistory');
});

test('branch priority changes the first matching result and survives reload', async ({ page, request }) => {
  await prepareBasicActive(request);
  await page.goto(`${embedded}/#flow`);
  await admin(page).getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(admin(page).getByRole('status').first()).toContainText('Draft created');
  await addState(page, 'ALTERNATIVE');
  const document = await exportDocument(page);
  await selectEdge(page, document.transitions.find((item) => item.trigger.kind === 'event' && item.trigger.event === 'FINISH')!.id);
  await choose(page, 'Actions', 'commissionTwoPercent');
  await addBranch(page, 'IN_PROGRESS', 'ALTERNATIVE', 'FINISH');
  await choose(page, 'Actions', 'commissionOnePercent');
  await editor(page).getByRole('button', { name: 'Move branch 2 up', exact: true }).click();
  await publish(page);
  const created = await order(request, '100.00');
  await event(request, created.id, 'SUBMIT');
  expect(await event(request, created.id, 'FINISH')).toMatchObject({ state: 'ALTERNATIVE', commission: 1 });
});
