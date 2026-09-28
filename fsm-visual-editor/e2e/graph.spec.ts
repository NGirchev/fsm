import { expect } from '@playwright/test';
import { test, editor, connect, exportDocument, fixture, importDocument, openDraft, selectEdge, standalone, expandSection, admin } from './helpers';

for (const mode of ['standalone', 'embedded'] as const) {
  test.describe(`${mode} graph`, () => {
    test.beforeEach(async ({ page }) => {
      if (mode === 'embedded') await openDraft(page);
      else await page.goto(standalone);
      await importDocument(page);
    });

    test('project settings, initial state and validation', async ({ page }) => {
      await editor(page).getByLabel('Name', { exact: true }).fill('Edited flow');
      await editor(page).getByRole('combobox', { name: 'Initial', exact: true }).selectOption('B');
      await editor(page).getByLabel('Auto transitions', { exact: true }).check();
      if (mode === 'embedded') await editor(page).getByLabel('Automatic transition limit').fill('42');
      else {
        for (const [label, value] of [['Package', 'demo.flow'], ['Factory class', 'OrderFlow'],
          ['Domain', 'MyOrder'], ['State', 'MyState'], ['Event', 'MyEvent']]) {
          await editor(page).getByLabel(label, { exact: true }).fill(value);
        }
        await editor(page).getByRole('combobox', { name: 'Code style', exact: true }).selectOption('builder');
      }
      const doc = await exportDocument(page);
      expect(doc.name).toBe('Edited flow');
      expect(doc.codegen.initialState).toBe('B');
      expect(doc.autoTransitionEnabled).toBe(true);
      if (mode === 'embedded') expect(doc.maxImmediateAutoTransitions).toBe(42);
      else expect(doc.codegen).toMatchObject({ packageName: 'demo.flow', className: 'OrderFlow',
        domainType: 'MyOrder', stateType: 'MyState', eventType: 'MyEvent', style: 'builder' });
      await editor(page).locator('.react-flow__node[data-id="a"]').click();
      await editor(page).getByLabel('Label', { exact: true }).fill('B');
      await expect(editor(page).locator('.validation-panel')).toContainText(/duplicate|unique/i);
    });

    test('disabled automatic transition warning identifies the branch and explains the options', async ({ page }) => {
      await selectEdge(page);
      await editor(page).locator('.selected-panel').getByRole('button', { name: 'Auto', exact: true }).click();
      await editor(page).getByLabel('Run this transition automatically even when global auto transitions are disabled').uncheck();
      await editor(page).locator('.selected-panel').getByRole('button', { name: 'Auto', exact: true }).click();
      expect((await exportDocument(page)).transitions[0].autoTransitionEnabled).toBe(false);
      const warning = editor(page).locator('.validation-panel .validation-issue').filter({ hasText: 'A → B' });
      await expect(warning).toContainText('This automatic transition is disabled');
      await expect(warning).toContainText('enable automatic execution on this transition');
      const showTransition = warning.getByRole('button', { name: 'Show transition' });
      const warningWidth = (await warning.boundingBox())!.width;
      expect((await showTransition.boundingBox())!.width).toBeGreaterThan(warningWidth - 2);
      await editor(page).locator('.react-flow__pane').click({ position: { x: 30, y: 30 } });
      await showTransition.click();
      await expect(editor(page).locator('.selected-panel h2')).toHaveText('Transition');
    });

    test('add, rename, describe, drag and delete states with attached edges', async ({ page }) => {
      await editor(page).locator('.react-flow__node[data-id="a"]').click();
      await editor(page).getByLabel('Label', { exact: true }).fill('RENAMED');
      await editor(page).getByLabel('Description').fill('Line one\n  indented line');
      expect((await exportDocument(page)).codegen.initialState).toBe('RENAMED');
      const node = editor(page).locator('.react-flow__node[data-id="a"]');
      const box = (await node.boundingBox())!;
      await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
      await page.mouse.down();
      await page.mouse.move(box.x + box.width / 2 + 90, box.y + box.height / 2 + 60, { steps: 10 });
      await page.mouse.up();
      const moved = (await exportDocument(page)).states[0];
      expect(moved.position).not.toEqual(fixture.states[0].position);
      expect(moved.description).toBe('Line one\n  indented line');
      await editor(page).getByRole('button', { name: 'Add state', exact: true }).click();
      await expect(editor(page).locator('.react-flow__node')).toHaveCount(4);
      await editor(page).getByRole('button', { name: 'Delete selected', exact: true }).click();
      await expect(editor(page).locator('.react-flow__node')).toHaveCount(3);
      await editor(page).locator('.react-flow__node[data-id="b"]').click();
      await page.keyboard.press('Backspace');
      await expect(editor(page).locator('.react-flow__node')).toHaveCount(2);
      await expect(editor(page).getByRole('button', { name: 'Delete selected', exact: true })).toBeDisabled();
      expect((await exportDocument(page)).transitions).toEqual([]);
    });

    test('connect handles, reject duplicate auto edge, select and delete transition', async ({ page }) => {
      await connect(page, 'b', 'c');
      await expect(editor(page).locator('.react-flow__edge')).toHaveCount(2);
      await connect(page, 'b', 'c');
      await expect(editor(page).locator('.toolbar-title')).toContainText('Auto transition already exists');
      await expect(editor(page).locator('.react-flow__edge')).toHaveCount(2);
      const edge = (await exportDocument(page)).transitions.find((item) => item.from === 'b')!;
      expect(edge.trigger).toEqual({ kind: 'auto' });
      expect(edge.autoTransitionEnabled).toBe(true);
      await selectEdge(page, edge.id);
      await editor(page).getByRole('button', { name: 'Delete selected', exact: true }).click();
      await expect(editor(page).locator('.react-flow__edge')).toHaveCount(1);
      await editor(page).locator('.react-flow__pane').click({ position: { x: 30, y: 30 } });
      await expect(editor(page).getByRole('button', { name: 'Delete selected', exact: true })).toBeDisabled();
    });

    test('transition endpoints, triggers, behavior selection and timeout units', async ({ page }) => {
      await selectEdge(page);
      await editor(page).getByRole('combobox', { name: 'From', exact: true }).selectOption('c');
      await editor(page).getByRole('combobox', { name: 'To', exact: true }).selectOption('a');
      const panel = editor(page).locator('.selected-panel');
      await panel.getByRole('button', { name: 'Auto', exact: true }).click();
      expect((await exportDocument(page)).transitions[0].trigger).toEqual({ kind: 'auto' });
      expect((await exportDocument(page)).transitions[0].autoTransitionEnabled).toBe(true);
      await editor(page).getByLabel('Run this transition automatically even when global auto transitions are disabled').uncheck();
      expect((await exportDocument(page)).transitions[0].autoTransitionEnabled).toBe(false);
      await editor(page).getByLabel('Run this transition automatically even when global auto transitions are disabled').check();
      expect((await exportDocument(page)).transitions[0].autoTransitionEnabled).toBe(true);
      await panel.getByRole('button', { name: 'Event', exact: true }).click();
      await panel.getByRole('combobox', { name: 'Event', exact: true }).selectOption('FINISH');
      const guard = mode === 'embedded' ? 'amountBelowCommissionThreshold' : 'canGo';
      const action = mode === 'embedded' ? 'commissionTwoPercent' : 'doWork';
      for (const [group, name] of [['Guards', guard], ['Actions', action], ['Post actions', action]]) {
        if (mode === 'embedded') await panel.getByLabel(`Add ${group}`, { exact: true }).selectOption(name);
        else await panel.getByRole('group', { name: group, exact: true }).getByLabel(name).check();
      }
      await editor(page).getByLabel('Timeout', { exact: true }).fill('5');
      for (const unit of ['NANOSECONDS', 'MICROSECONDS', 'MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS', 'DAYS']) {
        await editor(page).getByRole('combobox', { name: 'Unit', exact: true }).selectOption(unit);
        await expect(editor(page).getByRole('combobox', { name: 'Unit', exact: true })).toHaveValue(unit);
      }
      await expect(editor(page).getByLabel('Run this transition automatically even when global auto transitions are disabled')).toHaveCount(0);
      const transition = (await exportDocument(page)).transitions[0];
      expect(transition).toMatchObject({ from: 'c', to: 'a', trigger: { kind: 'event', event: 'FINISH' },
        conditions: [guard], actions: [action], postActions: [action], timeout: { value: 5, unit: 'DAYS' } });
      expect(transition.autoTransitionEnabled).toBe(false);
      if (mode === 'embedded') await panel.getByRole('button', { name: `Remove Actions ${action}`, exact: true }).click();
      else await panel.getByRole('group', { name: 'Actions', exact: true }).getByLabel('doWork', { exact: true }).uncheck();
      await editor(page).getByLabel('Timeout', { exact: true }).fill('');
      expect((await exportDocument(page)).transitions[0].timeout).toBeUndefined();
      expect((await exportDocument(page)).transitions[0].actions).toEqual([]);
    });

    test('imported event auto flags block saving or code export until repaired', async ({ page }) => {
      await importDocument(page, { ...fixture,
        transitions: [{ ...fixture.transitions[0], autoTransitionEnabled: true }],
      });
      await expect(editor(page).locator('.validation-panel')).toContainText('Only eventless transitions');
      if (mode === 'standalone') {
        await expect(editor(page).getByRole('button', { name: 'JAVA', exact: true })).toBeDisabled();
        await expect(editor(page).getByRole('button', { name: 'KT', exact: true })).toBeDisabled();
      } else {
        await admin(page).getByRole('button', { name: 'Save draft', exact: true }).click();
        await expect(admin(page).getByRole('status').first()).toContainText('Only eventless transitions');
      }
      await selectEdge(page);
      await editor(page).locator('.selected-panel').getByRole('button', { name: 'Event', exact: true }).click();
      expect((await exportDocument(page)).transitions[0].autoTransitionEnabled).toBe(false);
      await expect(editor(page).locator('.validation-panel')).not.toContainText('Only eventless transitions');
      if (mode === 'standalone') {
        await expect(editor(page).getByRole('button', { name: 'JAVA', exact: true })).toBeEnabled();
        await expect(editor(page).getByRole('button', { name: 'KT', exact: true })).toBeEnabled();
      }
    });

    test('events and behaviors add, rename references and cascade deletion', async ({ page }) => {
      await editor(page).getByRole('button', { name: 'Add event', exact: true }).click();
      await editor(page).getByLabel('Event ID 3', { exact: true }).fill('EXTRA');
      await editor(page).getByRole('button', { name: 'Delete event EXTRA', exact: true }).click();
      await editor(page).getByLabel('Event ID 1', { exact: true }).fill('RENAMED');
      expect((await exportDocument(page)).transitions[0].trigger).toEqual({ kind: 'event', event: 'RENAMED' });
      if (mode === 'embedded') {
        await expect(editor(page).getByRole('button', { name: 'Add guard', exact: true })).toHaveCount(0);
        await selectEdge(page);
        await editor(page).getByLabel('Add Guards', { exact: true }).selectOption('amountBelowCommissionThreshold');
        await editor(page).getByLabel('Add Actions', { exact: true }).selectOption('commissionTwoPercent');
        await editor(page).getByRole('button', { name: 'Remove Guards amountBelowCommissionThreshold', exact: true }).click();
        await editor(page).getByRole('button', { name: 'Remove Actions commissionTwoPercent', exact: true }).click();
        expect((await exportDocument(page)).transitions[0]).toMatchObject({ conditions: [], actions: [], postActions: [] });
        await editor(page).getByRole('button', { name: 'Delete event RENAMED', exact: true }).click();
        await expect(editor(page).locator('.react-flow__edge')).toHaveCount(0);
        return;
      }
      await expandSection(page, 'Behavior');
      await editor(page).getByRole('button', { name: 'Add guard', exact: true }).click();
      await editor(page).getByLabel('Guard ID 2').fill('extraGuard');
      await editor(page).getByRole('button', { name: 'Add action', exact: true }).click();
      await editor(page).getByLabel('Action ID 2').fill('extraAction');
      await selectEdge(page);
      const panel = editor(page).locator('.selected-panel');
      await panel.getByLabel('extraGuard').check();
      await panel.getByRole('group', { name: 'Actions', exact: true }).getByLabel('extraAction').check();
      await panel.getByRole('group', { name: 'Post actions', exact: true }).getByLabel('extraAction').check();
      await editor(page).getByLabel('Guard ID 2').fill('renamedGuard');
      await editor(page).getByLabel('Action ID 2').fill('renamedAction');
      expect((await exportDocument(page)).transitions[0]).toMatchObject({ conditions: ['renamedGuard'],
        actions: ['renamedAction'], postActions: ['renamedAction'] });
      await editor(page).getByRole('button', { name: 'Delete guard renamedGuard', exact: true }).click();
      await editor(page).getByRole('button', { name: 'Delete action renamedAction', exact: true }).click();
      expect((await exportDocument(page)).transitions[0]).toMatchObject({ conditions: [], actions: [], postActions: [] });
      await editor(page).getByRole('button', { name: 'Delete event RENAMED', exact: true }).click();
      await expect(editor(page).locator('.react-flow__edge')).toHaveCount(0);
      await expect(panel).toHaveCount(0);
    });

    test('JSON round trip preserves layout; invalid import preserves document', async ({ page }) => {
      const before = await exportDocument(page);
      await editor(page).locator('.react-flow__node[data-id="a"]').click();
      await editor(page).locator('input[type=file]').setInputFiles({ name: 'bad.json', mimeType: 'application/json', buffer: Buffer.from('{bad') });
      await expect(editor(page).locator('.toolbar-title')).toContainText(/JSON|Unexpected/);
      expect(await exportDocument(page)).toEqual(before);
      await editor(page).locator('input[type=file]').setInputFiles({ name: 'wrong.json', mimeType: 'application/json', buffer: Buffer.from('{}') });
      await expect(editor(page).locator('.toolbar-title')).toContainText('Unsupported JSON');
      expect(await exportDocument(page)).toEqual(before);
      await importDocument(page, before);
      expect(await exportDocument(page)).toEqual(before);
      await expect(editor(page).getByRole('button', { name: 'Delete selected', exact: true })).toBeDisabled();
    });

    test('keyboard editing does not delete selected nodes; event trigger needs an event', async ({ page }) => {
      await editor(page).locator('.react-flow__node[data-id="a"]').click();
      await editor(page).getByLabel('Description').fill('text');
      await editor(page).getByLabel('Description').press('Backspace');
      await expect(editor(page).locator('.react-flow__node')).toHaveCount(3);
      await editor(page).getByRole('button', { name: 'Delete event GO', exact: true }).click();
      await editor(page).getByRole('button', { name: 'Delete event FINISH', exact: true }).click();
      await connect(page, 'b', 'c');
      const transition = (await exportDocument(page)).transitions[0];
      await selectEdge(page, transition.id);
      await expect(editor(page).locator('.selected-panel').getByRole('button', { name: 'Event', exact: true })).toBeDisabled();
      await editor(page).getByRole('button', { name: 'Add event', exact: true }).click();
      await expect(editor(page).locator('.selected-panel').getByRole('button', { name: 'Event', exact: true })).toBeEnabled();
      await selectEdge(page, transition.id);
      await page.keyboard.press('Backspace');
      await expect(editor(page).locator('.react-flow__edge')).toHaveCount(0);
      await expect(editor(page).getByRole('button', { name: 'Delete selected', exact: true })).toBeDisabled();
    });

    test('zoom, fit, pan, minimap and interaction lock', async ({ page }) => {
      const originalStates = (await exportDocument(page)).states;
      const viewport = editor(page).locator('.react-flow__viewport');
      const before = await viewport.getAttribute('style');
      await editor(page).getByRole('button', { name: 'Zoom In', exact: true }).click();
      await expect(viewport).not.toHaveAttribute('style', before!);
      await editor(page).getByRole('button', { name: 'Zoom Out', exact: true }).click();
      await editor(page).getByRole('button', { name: 'Fit View', exact: true }).click();
      await editor(page).getByRole('button', { name: 'Toggle Interactivity', exact: true }).click();
      await expect(editor(page).locator('.react-flow__node').first()).not.toHaveClass(/draggable/);
      await editor(page).getByRole('button', { name: 'Toggle Interactivity', exact: true }).click();
      await expect(editor(page).locator('.react-flow__node').first()).toHaveClass(/draggable/);
      const box = (await editor(page).locator('.react-flow__minimap').boundingBox())!;
      await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
      await page.mouse.down();
      await page.mouse.move(box.x + box.width / 2 + 30, box.y + box.height / 2, { steps: 5 });
      await page.mouse.up();
      expect((await exportDocument(page)).states).toEqual(originalStates);
    });
  });
}
