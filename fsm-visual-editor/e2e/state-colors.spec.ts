import { expect } from '@playwright/test';
import { test, editor, exportDocument, importDocument, openDraft, standalone, embedded, api, admin } from './helpers';

test('published diagram loads its stored colors unchanged across reloads', async ({ page }) => {
  const response = await page.request.get(`${api}/1`);
  expect(response.ok()).toBe(true);
  const version = await response.json();
  const stored = version.definition.editor.states;
  expect(stored.every((state: { color?: string }) => state.color)).toBe(true);
  await page.goto(`${embedded}/#flow`);
  await admin(page).getByLabel('Version').selectOption('1');
  const first = await exportDocument(page);
  expect(first.states.map((state) => state.color)).toEqual(stored.map((state: { color: string }) => state.color));
  await page.reload();
  await admin(page).getByLabel('Version').selectOption('1');
  expect((await exportDocument(page)).states).toEqual(first.states);
});

for (const mode of ['standalone', 'embedded'] as const) {
  test(`${mode} state color selection persists and does not recolor neighbors`, async ({ page }, testInfo) => {
    if (mode === 'embedded') await openDraft(page);
    else await page.goto(standalone);
    await importDocument(page);
    const ui = editor(page);
    const before = await exportDocument(page);
    expect(before.states.every((state) => state.color)).toBe(true);
    await ui.locator('.react-flow__node[data-id="a"]').click();
    const select = ui.getByRole('combobox', { name: 'State color', exact: true });
    await select.selectOption({ label: 'Pink' });
    await expect(select).toHaveValue('#be185d');
    await expect(ui.locator('.react-flow__node[data-id="a"]')).toHaveCSS('color', 'rgb(190, 24, 93)');
    await expect(ui.locator('.react-flow__minimap-node').first()).toHaveCSS('stroke', 'rgb(190, 24, 93)');
    await page.screenshot({ path: testInfo.outputPath('state-color-menu.png') });
    await ui.getByRole('button', { name: 'Add state', exact: true }).click();
    const added = await exportDocument(page);
    expect(added.states[0].color).toBe('#be185d');
    expect(added.states.slice(1, 3)).toEqual(before.states.slice(1));
    expect(added.states[3].color).toBeTruthy();
    await ui.getByRole('button', { name: 'Delete selected', exact: true }).click();
    const saved = await exportDocument(page);
    expect(saved.states.slice(1)).toEqual(before.states.slice(1));
    if (mode === 'embedded') {
      await admin(page).getByRole('button', { name: 'Save draft', exact: true }).click();
      await expect(admin(page).locator('#flow-message')).toHaveText('Draft and layout saved.');
      const version = await admin(page).getByLabel('Version').inputValue();
      await page.reload();
      await admin(page).getByLabel('Version').selectOption(version);
    } else {
      await page.reload();
    }
    expect((await exportDocument(page)).states).toEqual(saved.states);
    await importDocument(page, saved);
    expect((await exportDocument(page)).states).toEqual(saved.states);
    await editor(page).getByRole('button', { name: 'Fit View', exact: true }).click();
    await editor(page).locator('.react-flow__node[data-id="a"]').click();
    await expect(editor(page).getByRole('combobox', { name: 'State color', exact: true })).toHaveValue('#be185d');
    if (mode === 'embedded') {
      // The host locks version switching while an imported document is dirty.
      if (await admin(page).getByRole('button', { name: 'Discard changes', exact: true }).isEnabled()) {
        await admin(page).getByRole('button', { name: 'Discard changes', exact: true }).click();
      }
      await admin(page).getByLabel('Version').selectOption('1');
      await editor(page).locator('.react-flow__node').first().click();
      await expect(editor(page).getByRole('combobox', { name: 'State color', exact: true })).toBeDisabled();
    }
  });
}
