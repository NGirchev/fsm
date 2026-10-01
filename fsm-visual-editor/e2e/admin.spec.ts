import { expect } from '@playwright/test';
import { test, embedded, editor, backend, standalone, fulfillApi } from './helpers';
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
