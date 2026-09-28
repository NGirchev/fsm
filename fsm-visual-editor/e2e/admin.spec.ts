import { expect } from '@playwright/test';
import { test, embedded, editor } from './helpers';

test('standalone admin creates the first draft, sends CSRF and keeps registered flows separate', async ({ page }) => {
  // HTTP fixtures isolate panel behavior; real registration/lifecycle/security are covered by JVM tests.
  const initial = { initialState: 'NEW', table: { autoTransitionEnabled: false, transitions: { NEW: [] } } };
  type Version = { version: number; status: string; definition: unknown };
  const versions: Record<string, Version[]> = { first: [], second: [] };
  const writes: string[] = [];
  await page.route(`${embedded}/fsm-admin/api/**`, async (route) => {
    const path = new URL(route.request().url()).pathname.split('/api/')[1];
    if (path === 'csrf') return route.fulfill({ json: { headerName: 'X-HOST-CSRF', token: 'panel-test-token' } });
    if (path === 'flows') return route.fulfill({ json: [
      { flowKey: 'first', title: 'First flow' }, { flowKey: 'second', title: 'Second flow' },
    ] });
    const [, key, resource, number] = path.split('/');
    if (resource === 'behaviors') return route.fulfill({ json: [] });
    const method = route.request().method();
    if (method === 'GET') return route.fulfill({ json: versions[key] });
    expect(route.request().headers()['x-host-csrf']).toBe('panel-test-token');
    writes.push(`${method} ${key}`);
    const definition = route.request().postDataJSON() ?? initial;
    if (method === 'POST') {
      const version = { version: versions[key].length + 1, status: 'DRAFT', definition };
      versions[key].push(version);
      return route.fulfill({ status: 201, json: version });
    }
    if (method === 'PUT') {
      const version = versions[key].find((item) => item.version === Number(number))!;
      version.definition = definition;
      return route.fulfill({ json: version });
    }
    throw new Error(`Unexpected request: ${method} ${path}`);
  });
  await page.goto(`${embedded}/fsm-admin/`);
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

test('failed CSRF refresh preserves the draft and never sends a mutation', async ({ page }) => {
  await page.goto(`${embedded}/fsm-admin/`);
  await page.getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Draft created');
  await editor(page).getByLabel('Name', { exact: true }).fill('Keep after session expiry');
  let mutations = 0;
  page.on('request', (request) => { if (request.method() === 'PUT') mutations++; });
  await page.route(`${embedded}/fsm-admin/api/csrf`, (route) => route.fulfill({ status: 403, json: { message: 'Session expired' } }));
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Session expired');
  await expect(page.getByRole('status')).toContainText('Unsaved changes');
  await expect(editor(page).getByLabel('Name', { exact: true })).toBeEnabled();
  expect(mutations).toBe(0);
});
