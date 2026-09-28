import { expect, it } from 'vitest';
import { moveItem } from './ordering';
import { fromFlowDefinition, toFlowDefinition } from './flowDefinition';
import { createEmptyDocument } from './sample';
import { normalizeEditorDocument } from './documentGuards';

it('preserves branch and handler priority and execution settings across the runtime JSON round trip', () => {
  const document = createEmptyDocument();
  document.execution = { stateListeners: ['recordHistory'], completionListeners: ['recordCompletion'] };
  document.states.push({ id: 'done', label: 'DONE', position: { x: 200, y: 0 } });
  document.events = [{ id: 'GO' }];
  document.behaviors.actions = [{ id: 'firstAction' }, { id: 'secondAction' }];
  document.transitions = ['first', 'second'].map((id) => ({ id, from: 'initial', to: 'done',
    trigger: { kind: 'event', event: 'GO' }, conditions: [],
    actions: id === 'first' ? ['firstAction', 'secondAction'] : ['secondAction'], postActions: [] }));
  document.transitions = moveItem(document.transitions, 1, -1);
  document.transitions[1].actions = moveItem(document.transitions[1].actions, 1, -1);
  const restored = fromFlowDefinition(JSON.parse(JSON.stringify(toFlowDefinition(document))));
  expect(restored.transitions.map((item) => item.id)).toEqual(['second', 'first']);
  expect(restored.transitions[1].actions).toEqual(['secondAction', 'firstAction']);
  expect(restored.execution).toEqual(document.execution);
  expect(normalizeEditorDocument(restored)?.execution).toEqual(document.execution);
  expect(normalizeEditorDocument({ ...restored, execution: { stateListeners: 'nope' } })).toBeNull();
  expect(moveItem(document.transitions, 0, -1)).toBe(document.transitions);
});
