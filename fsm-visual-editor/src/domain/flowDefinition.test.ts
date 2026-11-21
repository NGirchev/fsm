import { describe, expect, it } from 'vitest';
import { fromFlowDefinition, toFlowDefinition, type FlowDefinition } from './flowDefinition';
import { createEmptyDocument } from './sample';
import { normalizeEditorDocument } from './documentGuards';
import { validateEditorDocument } from './validation';
import { generateJavaFactory } from './javaGenerator';
import { generateKotlinFactory } from './kotlinGenerator';

const definition: FlowDefinition = {
  initialState: 'NEW',
  table: {
    autoTransitionEnabled: true,
    maxImmediateAutoTransitions: 12,
    transitions: {
      NEW: [
        { from: 'NEW', event: 'SUBMIT', to: { state: 'REVIEW', conditions: ['needs.review'], actions: ['audit'], postActions: ['notify'], timeout: { value: 5, unit: 'SECONDS' }, autoTransitionEnabled: false } },
        { from: 'NEW', event: 'SUBMIT', to: { state: 'DONE', conditions: [], actions: [], postActions: [], timeout: null, autoTransitionEnabled: false } },
      ],
      REVIEW: [{ from: 'REVIEW', event: null, to: { state: 'DONE', conditions: [], actions: [], postActions: [], timeout: null, autoTransitionEnabled: true } }],
      DONE: [],
      UNUSED: [],
    },
  },
};

describe('runtime flow editing', () => {
  it('keeps runtime auto settings when an exported document is used for class generation', () => {
    const document = fromFlowDefinition(definition);
    for (const generate of [generateJavaFactory, generateKotlinFactory]) {
      document.codegen.style = 'fluent';
      expect(generate(document)).toContain('.maxImmediateAutoTransitions(12)');
      expect(generate(document)).toContain('.auto()');
      document.codegen.style = 'builder';
      expect(generate(document)).toContain('.maxImmediateAutoTransitions(12)');
      expect(generate(document)).toMatch(/autoTransitionEnabled = true|null, true/);
    }
  });
  it('round trips ordered guarded alternatives, terminal states, eventless and local automatic transitions', () => {
    const document = fromFlowDefinition(definition);
    expect(toFlowDefinition(document).table).toEqual(definition.table);
    expect(document.transitions.map((transition) => transition.trigger.kind)).toEqual(['event', 'event', 'auto']);
    expect(document.behaviors.conditions).toEqual([{ id: 'needs.review' }]);
  });

  it('rejects an imported event transition with a local auto flag before saving', () => {
    const document = fromFlowDefinition(definition);
    document.transitions[0].autoTransitionEnabled = true;
    const imported = normalizeEditorDocument(JSON.parse(JSON.stringify(document)));
    expect(imported).not.toBeNull();
    expect(() => toFlowDefinition(imported!)).toThrow('Only eventless transitions');
  });

  it('lays out a definition without editor positions from the initial state downwards', () => {
    const position = (document: ReturnType<typeof fromFlowDefinition>, label: string) =>
      document.states.find((state) => state.label === label)!.position;
    const document = fromFlowDefinition(definition);

    expect(position(document, 'NEW').y).toBeLessThan(position(document, 'REVIEW').y);
    expect(position(document, 'REVIEW').y).toBeLessThan(position(document, 'DONE').y);
  });

  it('restores exact positions, labels, unused events and IDs after JSON persistence', () => {
    const document = fromFlowDefinition(definition);
    document.name = 'Order approval';
    document.states[0].position = { x: -214.75, y: 870.25 };
    document.states[0].description = 'Incoming order';
    document.events.push({ id: 'FUTURE_EVENT', label: 'Future operation' });
    document.behaviors.actions.push({ id: 'futureAction', label: 'Future handler' });
    document.transitions[0].id = 'custom-transition';
    const saved = JSON.parse(JSON.stringify(toFlowDefinition(document))) as FlowDefinition;
    const restored = fromFlowDefinition(saved);
    expect(restored).toEqual(document);
    expect(normalizeEditorDocument(JSON.parse(JSON.stringify(document)))).toEqual(document);
  });

  it('uses runtime transitions even when the definition changed outside the editor', () => {
    const saved = toFlowDefinition(fromFlowDefinition(definition));
    saved.table.transitions.NEW[0].event = 'CHANGED';
    saved.initialState = 'DONE';
    const restored = fromFlowDefinition(saved);
    expect(restored.codegen.initialState).toBe('DONE');
    expect(restored.transitions[0].trigger).toEqual({ kind: 'event', event: 'CHANGED' });
  });

  it('keeps transition IDs attached to their source after JSONB object-key reordering', () => {
    const original = fromFlowDefinition(definition);
    const saved = toFlowDefinition(original);
    saved.table.transitions = Object.fromEntries(Object.entries(saved.table.transitions).reverse());
    const restored = fromFlowDefinition(saved);
    const byId = Object.fromEntries(restored.transitions.map((transition) => [transition.id, transition.trigger]));
    expect(byId['transition-0']).toEqual({ kind: 'event', event: 'SUBMIT' });
    expect(byId['transition-2']).toEqual({ kind: 'auto' });
    expect(restored).toEqual(original);
  });

  it('preserves per-source priority when transitions from several states are interleaved', () => {
    const document = fromFlowDefinition(definition);
    document.transitions = [document.transitions[0], document.transitions[2], document.transitions[1]];
    const saved = toFlowDefinition(document);
    expect(saved.table).toEqual(definition.table);
    expect(fromFlowDefinition(saved).transitions.map((transition) => transition.id)).toEqual(['transition-0', 'transition-1', 'transition-2']);
  });

  it('rejects ambiguous state names, missing initial states and negative automatic limits', () => {
    const document = fromFlowDefinition(definition);
    document.states[1].label = 'NEW';
    expect(() => toFlowDefinition(document)).toThrow('Duplicate state label');
    document.states[1].label = 'REVIEW';
    document.codegen.initialState = 'MISSING';
    expect(() => toFlowDefinition(document)).toThrow('must match a state label');
    document.codegen.initialState = 'NEW';
    document.maxImmediateAutoTransitions = 0;
    expect(toFlowDefinition(document).table.maxImmediateAutoTransitions).toBe(0);
    document.maxImmediateAutoTransitions = -1;
    expect(() => toFlowDefinition(document)).toThrow('Automatic transition limit');
  });

  it('keeps the order of several transitions between one pair of states that differ only by conditions', () => {
    const branch = (conditions: string[]) => ({ from: 'THINKING', event: null,
      to: { state: 'DONE', conditions, actions: [], postActions: [], timeout: null, autoTransitionEnabled: false } });
    const table = { autoTransitionEnabled: true, maxImmediateAutoTransitions: 0, transitions: {
      THINKING: [branch(['agent.hasError']), branch(['agent.hasFinalAnswer', 'agent.notCancelled']), branch([])], DONE: [] } };
    const document = fromFlowDefinition({ initialState: 'THINKING', table });
    expect(validateEditorDocument(document, true).filter((issue) => issue.severity === 'error')).toEqual([]);
    const saved = JSON.parse(JSON.stringify(toFlowDefinition(document))) as FlowDefinition;
    expect(saved.table).toEqual(table);
    expect(toFlowDefinition(fromFlowDefinition(saved)).table).toEqual(table);
    document.transitions[1].conditions = ['agent.hasError'];
    expect(() => toFlowDefinition(document)).toThrow('Duplicate transition');
  });

  it('stores final states by state name in the definition and not in the editor metadata', () => {
    const marked: FlowDefinition = { ...definition, finalStates: { DONE: 'SUCCESS', UNUSED: 'LIMIT' } };
    const document = fromFlowDefinition(marked);
    expect(document.states.map((state) => [state.label, state.final])).toEqual(
      [['NEW', undefined], ['REVIEW', undefined], ['DONE', 'SUCCESS'], ['UNUSED', 'LIMIT']]);
    document.states[3].label = 'GAVE_UP';
    document.states[1].final = 'FAILURE';
    const saved = JSON.parse(JSON.stringify(toFlowDefinition(document))) as FlowDefinition;
    expect(saved.finalStates).toEqual({ REVIEW: 'FAILURE', DONE: 'SUCCESS', GAVE_UP: 'LIMIT' });
    expect(saved.editor!.states.every((state) => !('final' in state))).toBe(true);
    expect(fromFlowDefinition(saved)).toEqual(document);
    expect(toFlowDefinition(fromFlowDefinition(definition))).not.toHaveProperty('finalStates');
    expect(validateEditorDocument(document, true).filter((issue) => issue.path === 'states[1].final')).toEqual([
      { severity: 'warning', path: 'states[1].final', message: 'Final state "REVIEW" has outgoing transitions and cannot be published.' }]);
  });

  it('keeps the iteration limit in execution, omits it when empty and rejects values below one', () => {
    const limited: FlowDefinition = { ...definition, execution: { stateListeners: [], completionListeners: [], maxIterations: 12 } };
    const document = fromFlowDefinition(limited);
    expect(toFlowDefinition(document).execution).toEqual(limited.execution);
    expect(normalizeEditorDocument(JSON.parse(JSON.stringify(document)))).toEqual(document);
    expect(toFlowDefinition(fromFlowDefinition(definition))).not.toHaveProperty('execution');
    for (const invalid of [0, -1, 1.5]) {
      document.execution = { stateListeners: [], completionListeners: [], maxIterations: invalid };
      expect(() => toFlowDefinition(document)).toThrow('Iteration limit must be an integer of at least 1.');
    }
    expect(normalizeEditorDocument({ ...document, execution: { ...document.execution, maxIterations: 'many' } })).toBeNull();
    expect(normalizeEditorDocument({ ...document, execution: undefined,
      states: [{ ...document.states[0], final: 'DONE' }, ...document.states.slice(1)] })).toBeNull();
  });

  it('allows string states and events, including object prototype names', () => {
    const document = createEmptyDocument();
    document.states[0].label = '__proto__';
    document.codegen.initialState = '__proto__';
    document.events = [{ id: 'submit order' }];
    const saved = toFlowDefinition(document);
    expect(Object.keys(saved.table.transitions)).toEqual(['__proto__']);
    expect(fromFlowDefinition(saved).states[0].label).toBe('__proto__');
  });

  it('falls back to a fresh layout when optional metadata is malformed', () => {
    const saved = { ...definition, editor: { states: 'invalid' } } as unknown as FlowDefinition;
    expect(toFlowDefinition(fromFlowDefinition(saved)).table).toEqual(definition.table);
  });
});
