import { expect } from '@playwright/test';
import { test, editor, fixture, importDocument, openDraft, selectEdge, standalone } from './helpers';
import { sampleDocument } from '../src/domain';

for (const mode of ['standalone', 'embedded'] as const) {
  test(`${mode} contrast, identity colors and responsive layout`, async ({ page }, testInfo) => {
    if (mode === 'embedded') await openDraft(page);
    else await page.goto(standalone);
    const document = {
      ...fixture,
      states: fixture.states.map((state) => state.id === 'c'
        ? { ...state, label: 'A_STATE_WITH_A_VERY_LONG_READABLE_NAME' } : state),
      transitions: [...fixture.transitions,
        { ...fixture.transitions[0], id: 'bc', from: 'b', to: 'c' },
        { ...fixture.transitions[0], id: 'ca', from: 'c', to: 'a', trigger: { kind: 'auto' as const } }],
    };
    await importDocument(page, document);
    const ui = editor(page);
    const node = ui.locator('.react-flow__node[data-id="a"]');
    const event = ui.locator('.flow-edge-label[data-id="ab"]');
    const color = await event.evaluate((el) => getComputedStyle(el).color);
    await expect(ui.locator('.flow-edge-label[data-id="bc"]')).toHaveCSS('color', color);
    await expect(ui.locator('.event-swatch').first()).toHaveCSS('color', color);
    await expect(node.locator('.node-start')).toHaveText('Start');
    await expect(ui.locator('.react-flow__minimap-node')).toHaveCount(3);
    const nodeInk = await node.evaluate((el) => getComputedStyle(el).color);
    await expect(ui.locator('.react-flow__minimap-node').first()).toHaveCSS('stroke', nodeInk);
    await expect(ui.locator('.flow-edge-label[data-id="ca"]')).toHaveClass(/automatic/);
    await expect(ui.locator('.react-flow__edge[data-id="ca"] path').first()).toHaveCSS('stroke-dasharray', '7px, 5px');
    const canvasColor = await ui.locator('.canvas').evaluate((el) => getComputedStyle(el).backgroundColor);
    expect(await ui.locator('.inspector').evaluate((el) => getComputedStyle(el).backgroundColor)).not.toBe(canvasColor);
    await page.screenshot({ path: testInfo.outputPath('overview.png') });
    await node.click();
    await expect(node).toHaveClass(/selected-node/);
    await expect.poll(() => node.evaluate((element) =>
      getComputedStyle(element).boxShadow)).toContain('rgb(0, 229, 255)');
    await page.screenshot({ path: testInfo.outputPath('selected-state.png') });
    const nodeFill = await node.evaluate((el) => getComputedStyle(el).backgroundColor);
    await node.hover();
    await expect(node).toHaveCSS('background-color', nodeFill);
    await selectEdge(page);
    await expect(event).toHaveCSS('color', color);
    await expect(event).toHaveClass(/selected/);
    await page.screenshot({ path: testInfo.outputPath('selected-transition.png') });
    await page.reload();
    await importDocument(page, document);
    await expect(editor(page).locator('.flow-edge-label[data-id="ab"]')).toHaveCSS('color', color);
    await expect(editor(page).locator('.react-flow__minimap-node')).toHaveCount(3);
    await page.setViewportSize({ width: 560, height: 850 });
    await expect(editor(page).locator('.react-flow__minimap')).toHaveCSS('width', '100px');
    expect(await editor(page).locator('.app-shell').evaluate((el) => el.scrollWidth <= el.clientWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('narrow.png') });
    if (mode === 'standalone') {
      await page.setViewportSize({ width: 1600, height: 1000 });
      await importDocument(page, sampleDocument);
      await expect(editor(page).locator('.react-flow__minimap-node')).toHaveCount(sampleDocument.states.length);
      await page.screenshot({ path: testInfo.outputPath('document-fsm.png') });
      await selectEdge(page, sampleDocument.transitions[0].id);
      await page.screenshot({ path: testInfo.outputPath('document-fsm-selected.png') });
    }
  });
}
