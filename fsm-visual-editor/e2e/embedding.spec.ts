import { expect } from '@playwright/test';
import { createServer } from 'node:http';
import type { AddressInfo } from 'node:net';
import { test, fixture, embedded, api, editor, exportDocument, admin } from './helpers';

const staticEditor = `http://127.0.0.1:${process.env.E2E_STATIC_PORT}/`;

test('one static build runs standalone and in an unrelated cross-origin host without an application API', async ({ page }) => {
  await page.goto(staticEditor);
  await expect(page.getByRole('button', { name: 'New flow', exact: true })).toBeVisible();
  await expect(page.getByLabel('Name', { exact: true })).toBeEnabled();
  const definition = { ...fixture, execution: { stateListeners: [], completionListeners: [] } };
  let host = '';
  const server = createServer((_request, response) => {
    response.setHeader('Content-Type', 'text/html');
    response.end(`
    <!doctype html><html lang="en"><title>Independent host</title><body>
    <iframe title="Universal editor" style="width:100%;height:900px" src="${staticEditor}?parentOrigin=${encodeURIComponent(host)}"></iframe>
    <script>
    const messages = window.messages = [];
    const send = window.send = (message) => document.querySelector('iframe').contentWindow.postMessage(
      { channel: 'fsm-editor/v1', session: 'test', ...message }, '${new URL(staticEditor).origin}');
    addEventListener('message', (event) => {
      if (event.source !== document.querySelector('iframe').contentWindow || event.origin !== '${new URL(staticEditor).origin}') return;
      messages.push(event.data);
      if (event.data.type === 'ready') send({ type: 'load', document: ${JSON.stringify(definition)}, readOnly: false,
        catalog: [] });
    });
    </script></body></html>`);
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  host = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  try {
    const apiCalls: string[] = [];
    page.on('request', (request) => { if (new URL(request.url()).pathname.startsWith('/api/')) apiCalls.push(request.url()); });
    await page.goto(host);
    const view = page.frameLocator('iframe');
    await expect(view.getByLabel('Name', { exact: true })).toHaveValue('Browser test');
    await view.getByLabel('Name', { exact: true }).fill('Independent document');
    await page.evaluate(() => (window as unknown as { send: (data: object) => void }).send({ type: 'snapshot', requestId: 'save' }));
    await expect.poll(() => page.evaluate(() => (window as unknown as { messages: { type: string; definition?: unknown }[] }).messages
      .find((item) => item.type === 'snapshot')?.definition)).toMatchObject({ editor: { name: 'Independent document' } });
    await expect(view.getByLabel('Name', { exact: true })).toBeDisabled();
    // A stale session cannot unlock the current document.
    await page.evaluate(() => (window as unknown as { send: (data: object) => void }).send({ type: 'configure', session: 'stale', readOnly: false }));
    await expect(view.getByLabel('Name', { exact: true })).toBeDisabled();
    await page.evaluate(() => (window as unknown as { send: (data: object) => void }).send({ type: 'configure', readOnly: false }));
    await expect(view.getByLabel('Name', { exact: true })).toBeEnabled();
    // A message from the editor window itself has the wrong source and origin.
    await view.getByLabel('Name', { exact: true }).evaluate(() => window.postMessage({ channel: 'fsm-editor/v1',
      type: 'configure', session: 'test', readOnly: true }, window.location.origin));
    await expect(view.getByLabel('Name', { exact: true })).toBeEnabled();
    await page.evaluate(() => (window as unknown as { send: (data: object) => void }).send({ type: 'load', document: {}, readOnly: false }));
    await expect.poll(() => page.evaluate(() => (window as unknown as { messages: { type: string }[] }).messages.some((item) => item.type === 'error'))).toBe(true);
    await expect(view.getByLabel('Name', { exact: true })).toHaveValue('Independent document');
    expect(apiCalls).toEqual([]);
  } finally { await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve())); }
});

test('example edits, saves and reloads through the cross-origin static editor', async ({ page }) => {
  await page.addInitScript((url) => {
    const observer = new MutationObserver(() => {
      const frame = document.getElementById('flow-editor');
      if (frame) { frame.dataset.editorUrl = url; observer.disconnect(); }
    });
    observer.observe(document, { childList: true, subtree: true });
  }, staticEditor);
  await page.goto(`${embedded}/#flow`);
  await admin(page).getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(admin(page).locator('#flow-message')).toContainText('Draft created');
  await editor(page).getByLabel('Name', { exact: true }).fill('Cross origin');
  await admin(page).getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(admin(page).locator('#flow-message')).toHaveText('Draft and layout saved.');
  const version = await admin(page).getByLabel('Version').inputValue();
  await page.reload();
  await admin(page).getByLabel('Version').selectOption(version);
  expect((await exportDocument(page)).name).toBe('Cross origin');
  await admin(page).getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(admin(page).locator('#flow-message')).toContainText('Published');
  await expect(editor(page).getByLabel('Name', { exact: true })).toBeDisabled();
});

test('orders tab runs the published flow and preserves an unsaved editor draft', async ({ page, request }, testInfo) => {
  const seed = await (await request.get(`${api}/1`)).json();
  seed.definition.execution = { stateListeners: ['recordOrderHistory'], completionListeners: [] };
  const draft = await (await request.post(api, { data: seed.definition })).json();
  expect((await request.post(`${api}/${draft.version}/publish`)).ok()).toBe(true);
  await page.goto(`${embedded}/`);
  await expect(page.getByRole('tab', { name: 'Orders', exact: true })).toHaveAttribute('aria-selected', 'true');
  await page.getByLabel('Amount', { exact: true }).fill('123.45');
  await page.getByRole('button', { name: 'Run flow', exact: true }).click();
  await expect(page.locator('#test-outcome')).toHaveText('Scenario finished');
  await expect(page.locator('[data-field="Amount"]')).toHaveText('123.45');
  await expect(page.locator('[data-field="Flow version"]')).toHaveText(String(draft.version));
  await expect(page.locator('[data-field="State"]')).toHaveText('COMPLETED');
  await expect(page.locator('#order-history')).toContainText('SUBMIT');
  await page.getByRole('tab', { name: 'Flow editor', exact: true }).click();
  await admin(page).getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(admin(page).locator('#flow-message')).toContainText('Draft created');
  await editor(page).getByLabel('Name', { exact: true }).fill('Keep draft across tabs');
  await page.getByRole('tab', { name: 'Orders', exact: true }).click();
  await page.screenshot({ path: testInfo.outputPath('orders.png'), fullPage: true });
  await page.getByRole('tab', { name: 'Flow editor', exact: true }).click();
  await expect(editor(page).getByLabel('Name', { exact: true })).toHaveValue('Keep draft across tabs');
  await expect(admin(page).locator('#flow-message')).toContainText('Unsaved changes');
  await page.screenshot({ path: testInfo.outputPath('embedded-editor.png'), fullPage: true });
});
