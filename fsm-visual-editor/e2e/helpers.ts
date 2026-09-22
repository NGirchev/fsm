import { test as base, expect, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import type { FsmEditorDocument } from '../src/domain/types';

export const test = base.extend<{ noBrowserErrors: void }>({
  noBrowserErrors: [async ({ page }, use) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await use();
    expect(errors, 'Uncaught browser errors').toEqual([]);
  }, { auto: true }],
});

export const embedded = process.env.E2E_EMBEDDED_URL!;
export const standalone = `http://127.0.0.1:${process.env.E2E_VITE_PORT}`;
export const api = `${embedded}/api/flows/order/versions`;
export const fixture: FsmEditorDocument = {
  formatVersion: 2, name: 'Browser test', autoTransitionEnabled: false,
  codegen: { packageName: 'example', className: 'TestFlow', factoryMethodName: 'create',
    domainType: 'Order', stateType: 'State', eventType: 'Event', initialState: 'A', style: 'fluent' },
  states: [{ id: 'a', label: 'A', position: { x: 0, y: 0 } },
    { id: 'b', label: 'B', position: { x: 300, y: 180 } },
    { id: 'c', label: 'C', position: { x: 600, y: 0 } }],
  events: [{ id: 'GO' }, { id: 'FINISH' }],
  behaviors: { conditions: [{ id: 'canGo' }], actions: [{ id: 'doWork' }] },
  transitions: [{ id: 'ab', from: 'a', to: 'b', trigger: { kind: 'event', event: 'GO' },
    conditions: [], actions: [], postActions: [] }],
};

export async function importDocument(page: Page, document: unknown = fixture) {
  await page.locator('input[type=file]').setInputFiles({ name: 'test.fsm.json', mimeType: 'application/json',
    buffer: Buffer.from(JSON.stringify(document)) });
  await expect(page.locator('.toolbar-title')).toContainText('Imported editor JSON');
  await page.getByRole('button', { name: 'Fit View', exact: true }).click();
}

export async function exportDocument(page: Page): Promise<FsmEditorDocument> {
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Export editor JSON', exact: true }).click();
  return JSON.parse(await readFile((await (await download).path())!, 'utf8'));
}

export async function openDraft(page: Page) {
  await page.goto(`${embedded}/fsm-editor/`);
  await expect(page.getByRole('button', { name: 'Create draft', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(page.getByRole('status').first()).toContainText('Draft created');
  return Number(await page.getByLabel('Order version').inputValue());
}

export async function connect(page: Page, from: string, to: string) {
  await page.locator(`[data-id="${from}"] .source`).hover();
  await page.mouse.down();
  await page.locator(`[data-id="${to}"] .target`).hover();
  await page.mouse.up();
}

export async function selectEdge(page: Page, id = 'ab') {
  // The label is on the curve; Playwright waits for layout/fit animation to settle before clicking.
  await page.locator(`.react-flow__edge[data-id="${id}"] .react-flow__edge-textbg`).click();
  await expect(page.locator('.selected-panel h2')).toHaveText('Transition');
}
