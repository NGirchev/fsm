import { expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { test, exportDocument, importDocument, standalone } from './helpers';

test.beforeEach(async ({ page }) => { await page.goto(standalone); });

test('new project autosaves, reloads, switches and deletes without deleting the sample', async ({ page, request }) => {
  await expect(page.getByRole('button', { name: 'Delete current project' })).toBeDisabled();
  await page.getByRole('button', { name: 'New flow', exact: true }).click();
  await page.getByLabel('Name', { exact: true }).fill('Persisted browser project');
  await expect(page.locator('.toolbar-title')).toContainText('Saved to projects/');
  const id = await page.getByLabel('Recent projects').inputValue();
  const saved = await exportDocument(page);
  await page.reload();
  expect(await exportDocument(page)).toEqual(saved);
  await page.getByLabel('Recent projects').selectOption('document-fsm-sample');
  await expect(page.getByRole('button', { name: 'Delete current project' })).toBeDisabled();
  await page.getByLabel('Recent projects').selectOption(id);
  await expect(page.getByLabel('Name', { exact: true })).toHaveValue('Persisted browser project');
  await page.getByRole('button', { name: 'Delete current project' }).click();
  await expect(page.locator('.toolbar-title')).toContainText('Deleted project');
  await expect.poll(async () => (await (await request.get(`${standalone}/api/projects`)).json())
    .some((item: { id: string }) => item.id === id)).toBe(false);
});

test('server save failure retains browser copy; load and delete failures preserve project', async ({ page }) => {
  await page.route('**/api/projects/*', (route) => route.fulfill({ status: 503, body: 'Unavailable' }));
  await page.getByRole('button', { name: 'New flow', exact: true }).click();
  await page.getByLabel('Name', { exact: true }).fill('Offline edit');
  await expect(page.locator('.toolbar-title')).toContainText('Saved in browser only');
  await page.reload();
  await expect(page.getByLabel('Name', { exact: true })).toHaveValue('Offline edit');
  await page.getByRole('button', { name: 'Delete current project' }).click();
  await expect(page.locator('.toolbar-title')).toContainText('Failed to delete project');
  await expect(page.getByLabel('Name', { exact: true })).toHaveValue('Offline edit');
  await page.unroute('**/api/projects/*');
  await page.getByLabel('Name', { exact: true }).fill('Saved again');
  await expect(page.locator('.toolbar-title')).toContainText('Saved to projects/');
  const id = await page.getByLabel('Recent projects').inputValue();
  await page.getByLabel('Recent projects').selectOption('document-fsm-sample');
  await page.route(`**/api/projects/${id}`, (route) => route.fulfill({ status: 503 }));
  await page.getByLabel('Recent projects').selectOption(id);
  await expect(page.locator('.toolbar-title')).toContainText('Failed to load project');
  await expect(page.getByRole('button', { name: 'Delete current project' })).toBeDisabled();
});

test('Java and Kotlin downloads reflect both code generation styles', async ({ page }) => {
  await importDocument(page);
  for (const style of ['fluent', 'builder']) {
    await page.getByRole('combobox', { name: 'Code style', exact: true }).selectOption(style);
    for (const [button, extension] of [['JAVA', 'java'], ['KT', 'kt']]) {
      const pending = page.waitForEvent('download');
      await page.getByRole('button', { name: button, exact: true }).click();
      const download = await pending;
      expect(download.suggestedFilename()).toBe(`TestFlow.${extension}`);
      const content = await readFile((await download.path())!, 'utf8');
      expect(content).toContain('TestFlow');
      expect(content).toContain('GO');
      expect(content).toContain('example');
    }
  }
});
