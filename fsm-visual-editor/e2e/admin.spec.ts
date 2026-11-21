import { expect } from '@playwright/test';
import { test, embedded, editor, backend, standalone, fulfillApi, selectEdge } from './helpers';
import type { EditorConfig } from '../src/domain/backend';

const connected = `${standalone}/?backend=${encodeURIComponent(backend)}`;

test('editor connected to a backend creates the first draft, uses the configured request function and keeps flows separate', async ({ page }) => {
  // HTTP fixtures isolate editor behavior; real registration/lifecycle/security are covered by JVM tests.
  // A deployment's config.js supplies credentials; this one adds a header to every request.
  await page.addInitScript(() => {
    const config: EditorConfig = { fetch: (input, init) => {
      const headers = new Headers(init?.headers);
      headers.set('X-Host-Token', 'deployment-token');
      return fetch(input, { ...init, headers });
    } };
    window.fsmEditorConfig = config;
  });
  const initial = { initialState: 'NEW', table: { autoTransitionEnabled: false, transitions: { NEW: [] } } };
  type Version = { version: number; status: string; definition: unknown };
  const versions: Record<string, Version[]> = { first: [], second: [] };
  const writes: string[] = [];
  await page.route(`${backend}/api/**`, async (route) => {
    expect(route.request().headers()['x-host-token']).toBe('deployment-token');
    const path = new URL(route.request().url()).pathname.split('/api/')[1];
    if (path === 'flows') return fulfillApi(route, 200, [
      { flowKey: 'first', title: 'First flow' }, { flowKey: 'second', title: 'Second flow' },
    ]);
    const [, key, resource, number] = path.split('/');
    if (resource === 'behaviors') return fulfillApi(route, 200, []);
    const method = route.request().method();
    if (method === 'GET') return fulfillApi(route, 200, versions[key]);
    writes.push(`${method} ${key}`);
    const definition = route.request().postDataJSON() ?? initial;
    if (method === 'POST') {
      const version = { version: versions[key].length + 1, status: 'DRAFT', definition };
      versions[key].push(version);
      return fulfillApi(route, 201, version);
    }
    if (method === 'PUT') {
      const version = versions[key].find((item) => item.version === Number(number))!;
      version.definition = definition;
      return fulfillApi(route, 200, version);
    }
    throw new Error(`Unexpected request: ${method} ${path}`);
  });
  await page.goto(connected);
  await expect(page.getByText(`Backend ${new URL(embedded).host}`)).toBeVisible();
  await expect(page.getByRole('status')).toContainText('No versions yet');
  await page.getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Draft created');
  await editor(page).getByLabel('Name', { exact: true }).fill('My first flow');
  await expect(page.getByLabel('Flow', { exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Draft and layout saved.');
  await page.getByLabel('Flow', { exact: true }).selectOption('second');
  await expect(page.getByRole('status')).toContainText('No versions yet');
  await page.getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Draft created');
  await page.getByLabel('Flow', { exact: true }).selectOption('first');
  await expect(editor(page).getByLabel('Name', { exact: true })).toHaveValue('My first flow');
  expect(writes).toEqual(['POST first', 'PUT first', 'POST second']);
});

test('editor manages flows by kind, filters beans by flow kind and stores final states and limits', async ({ page }) => {
  // HTTP fixtures play an application that creates agent flows at run time and protects its built-in ones.
  type Flow = { flowKey: string; title: string; kind: string; description: string | null; deletable: boolean };
  type Definition = { finalStates?: unknown; execution?: { maxIterations?: number };
    table: { maxImmediateAutoTransitions: number; transitions: Record<string, { to: { conditions: string[] } }[]> } };
  type Version = { version: number; status: string; definition: unknown };
  const template = { initialState: 'NEW', table: { autoTransitionEnabled: false, maxImmediateAutoTransitions: 0, transitions: {
    NEW: [{ from: 'NEW', event: 'GO', to: { state: 'DONE', conditions: [], actions: [], postActions: [], timeout: null } }],
    DONE: [] } } };
  const flows: Flow[] = [
    { flowKey: 'react', title: 'ReAct agent', kind: 'agent', description: 'Built-in loop', deletable: false },
    { flowKey: 'request', title: 'Request pipeline', kind: 'request', description: null, deletable: false },
  ];
  const versions: Record<string, Version[]> = { react: [{ version: 1, status: 'ACTIVE', definition: template }], request: [] };
  const catalog = [
    { id: 'agent.hasError', kind: 'guard', flowKind: 'agent', description: 'The step failed' },
    { id: 'document.isImage', kind: 'guard', flowKind: 'document', description: 'The file is an image' },
    { id: 'agent.think', kind: 'action', flowKind: 'agent', description: 'Ask the model' },
  ];
  const writes: unknown[][] = [];
  await page.route(`${backend}/api/**`, async (route) => {
    const method = route.request().method();
    const [, key, resource, number] = new URL(route.request().url()).pathname.split('/api/')[1].split('/');
    const body = route.request().postDataJSON();
    if (resource === 'behaviors') return fulfillApi(route, 200, catalog);
    if (method === 'GET') return fulfillApi(route, 200, key ? versions[key] : flows);
    writes.push([method, [key, resource, number].filter(Boolean).join('/'), body]);
    if (!key) {
      if (body.kind !== 'agent') return fulfillApi(route, 400, { message: 'Only agent flows can be created' });
      const flow = { flowKey: body.flowKey, title: body.title, kind: body.kind, description: body.description ?? null, deletable: true };
      flows.push(flow);
      const source = body.copyOf ? versions[body.copyOf][0].definition : template;
      versions[flow.flowKey] = [{ version: 1, status: 'DRAFT', definition: source }];
      return fulfillApi(route, 201, flow);
    }
    const flow = flows.find((item) => item.flowKey === key)!;
    if (resource) {
      versions[key][0].definition = body;
      return fulfillApi(route, 200, versions[key][0]);
    }
    if (method === 'PUT') return fulfillApi(route, 200, Object.assign(flow, { title: body.title, description: body.description ?? null }));
    if (key === 'mine') return fulfillApi(route, 409, { message: 'The default agent cannot be deleted' });
    flows.splice(flows.indexOf(flow), 1);
    return fulfillApi(route, 204, undefined);
  });
  const status = page.getByRole('status');
  const flowSelect = page.getByLabel('Flow', { exact: true });
  const button = (name: string) => page.getByRole('button', { name, exact: true });

  // ?flow= chooses the first flow to open; the select groups flows by kind.
  await page.goto(`${connected}&flow=request`);
  await expect(status).toContainText('No versions yet');
  await expect(flowSelect).toHaveValue('request');
  expect(await flowSelect.locator('optgroup').evaluateAll((groups) => groups.map((group) => (group as HTMLOptGroupElement).label)))
    .toEqual(['agent', 'request']);
  await flowSelect.selectOption('react');
  await expect(status).toContainText('Flow loaded');
  await expect(button('Delete flow')).toBeDisabled();

  // A refusal of the backend is shown and keeps the form; a new flow opens with its first draft.
  await button('New flow').click();
  await page.getByLabel('Flow key').fill('mine');
  await page.getByLabel('Flow title').fill('My agent');
  await expect(page.getByLabel('Flow kind')).toHaveValue('agent');
  await page.getByLabel('Flow kind').fill('request');
  await button('Create flow').click();
  await expect(status).toHaveText('Only agent flows can be created');
  await page.getByLabel('Flow kind').fill('agent');
  await page.getByLabel('Flow description').fill('Made in the editor');
  await button('Create flow').click();
  await expect(status).toContainText('Draft loaded');
  await expect(flowSelect).toHaveValue('mine');
  await expect(flowSelect.locator('option')).toHaveText(['ReAct agent', 'My agent', 'Request pipeline']);

  // The bean filter starts at the kind of the open flow and can show every kind.
  await editor(page).getByRole('button', { name: 'Fit View', exact: true }).click();
  await selectEdge(page, 'transition-0');
  await expect(editor(page).getByLabel('Bean kind')).toHaveValue('agent');
  await expect(editor(page).getByLabel('Add Guards').locator('option')).toHaveText(['Choose a bean…', 'agent.hasError']);
  await editor(page).getByLabel('Bean kind').selectOption('');
  await expect(editor(page).getByLabel('Add Guards').locator('option'))
    .toHaveText(['Choose a bean…', 'agent.hasError', 'document.isImage']);
  await editor(page).getByLabel('Add Guards').selectOption('document.isImage');
  await editor(page).getByLabel('Bean kind').selectOption('agent');
  await expect(editor(page).getByLabel('Guards 1')).toHaveValue('document.isImage');

  // Final state and both limits are part of the saved definition; an empty limit is left to the application.
  await editor(page).locator('.node-name').filter({ hasText: /^DONE$/ }).click();
  await editor(page).getByLabel('Final state').selectOption('SUCCESS');
  await expect(editor(page).locator('.node-final')).toHaveText('Final · success');
  await editor(page).getByLabel('Automatic transition limit').fill('25');
  await editor(page).getByLabel('Max iterations').fill('7');
  await button('Save draft').click();
  await expect(status).toHaveText('Draft and layout saved.');
  let saved = writes.at(-1)![2] as Definition;
  expect(writes.at(-1)!.slice(0, 2)).toEqual(['PUT', 'mine/versions/1']);
  expect(saved.finalStates).toEqual({ DONE: 'SUCCESS' });
  expect(saved.execution?.maxIterations).toBe(7);
  expect(saved.table.maxImmediateAutoTransitions).toBe(25);
  expect(saved.table.transitions.NEW[0].to.conditions).toEqual(['document.isImage']);
  await editor(page).getByLabel('Automatic transition limit').fill('');
  await editor(page).getByLabel('Max iterations').fill('');
  await editor(page).getByLabel('Final state').selectOption('');
  await button('Save draft').click();
  await expect(status).toHaveText('Draft and layout saved.');
  saved = writes.at(-1)![2] as Definition;
  expect(saved.table.maxImmediateAutoTransitions).toBe(0);
  expect(saved.execution).not.toHaveProperty('maxIterations');
  expect(saved).not.toHaveProperty('finalStates');

  // An open form locks the other flow controls, and edits made meanwhile are not dropped by opening a new flow.
  await button('Copy flow').click();
  await expect(button('Delete flow')).toBeDisabled();
  await expect(button('Rename flow')).toBeDisabled();
  await page.getByLabel('Flow key').fill('lost');
  await editor(page).getByLabel('Max iterations').fill('3');
  await expect(button('Create copy')).toBeDisabled();
  await editor(page).getByLabel('Max iterations').fill('');
  await expect(button('Create copy')).toBeEnabled();
  await button('Cancel').click();

  // Copy and rename read the flow list again.
  await button('Copy flow').click();
  await expect(page.getByLabel('Flow title')).toHaveValue('My agent copy');
  await page.getByLabel('Flow key').fill('mine2');
  await button('Create copy').click();
  await expect(flowSelect).toHaveValue('mine2');
  await expect(status).toContainText('Draft loaded');
  await expect(editor(page).locator('.flow-edge-label[data-id="transition-0"]')).toBeVisible();
  await button('Rename flow').click();
  await expect(page.getByLabel('Flow key')).toBeDisabled();
  await page.getByLabel('Flow title').fill('Second agent');
  await button('Save flow').click();
  await expect(status).toHaveText('Flow renamed.');
  await expect(flowSelect.locator('option')).toHaveText(['ReAct agent', 'My agent', 'Second agent', 'Request pipeline']);

  // Deletion reopens the flow of the page URL; a veto of the application leaves everything in place.
  page.on('dialog', (dialog) => void dialog.accept());
  await button('Delete flow').click();
  await expect(flowSelect).toHaveValue('request');
  await expect(flowSelect.locator('option')).toHaveText(['ReAct agent', 'My agent', 'Request pipeline']);
  await flowSelect.selectOption('mine');
  await expect(status).toContainText('Draft loaded');
  await button('Delete flow').click();
  await expect(status).toHaveText('The default agent cannot be deleted');
  await expect(flowSelect).toHaveValue('mine');
  expect(writes.filter(([, path]) => !String(path).includes('versions')).map(([method, path, body]) =>
    [method, path, (body as { copyOf?: string } | null)?.copyOf])).toEqual([
    ['POST', '', undefined], ['POST', '', undefined], ['POST', '', 'mine'], ['PUT', 'mine2', undefined],
    ['DELETE', 'mine2', undefined], ['DELETE', 'mine', undefined]]);
});
