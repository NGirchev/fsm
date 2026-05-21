import { describe, expect, it } from 'vitest';
import { sampleDocument, validateEditorDocument } from './index';

describe('validateEditorDocument', () => {
  it('accepts the sample document', () => {
    expect(validateEditorDocument(sampleDocument)).toEqual([]);
  });

  it('reports dangling transitions and unknown behavior references', () => {
    const document = {
      ...sampleDocument,
      transitions: [
        {
          ...sampleDocument.transitions[0],
          from: 'missing-source',
          trigger: { kind: 'event' as const, event: 'MISSING_EVENT' },
          conditions: ['missingGuard'],
          actions: ['missingAction'],
        },
      ],
    };

    const issues = validateEditorDocument(document);

    expect(issues.map((issue) => issue.message)).toEqual(
      expect.arrayContaining([
        'Unknown source state "missing-source".',
        'Unknown event "MISSING_EVENT".',
        'Unknown guard "missingGuard".',
        'Unknown action "missingAction".',
      ]),
    );
  });

  it('warns when an auto transition is configured without auto transitions enabled', () => {
    const document = {
      ...sampleDocument,
      autoTransitionEnabled: false,
      transitions: [{ ...sampleDocument.transitions[0], trigger: { kind: 'auto' as const } }],
    };

    expect(validateEditorDocument(document)).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          severity: 'warning',
          path: 'transitions[0].trigger',
        }),
      ]),
    );
  });

  it('warns when transitions cannot be handled by event', () => {
    const document = {
      ...sampleDocument,
      autoTransitionEnabled: true,
      events: [],
      transitions: [{ ...sampleDocument.transitions[0], trigger: { kind: 'auto' as const } }],
    };

    expect(validateEditorDocument(document)).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          severity: 'warning',
          path: 'events',
          message: 'No event transitions are configured; handle(domain, event) cannot trigger this FSM.',
        }),
      ]),
    );
  });

  it('requires codegen metadata and a valid timeout', () => {
    const document = {
      ...sampleDocument,
      codegen: {
        ...sampleDocument.codegen,
        className: 'bad-class',
        initialState: 'UNKNOWN',
      },
      transitions: [
        {
          ...sampleDocument.transitions[0],
          timeout: { value: 0, unit: 'SECONDS' as const },
        },
      ],
    };

    const issues = validateEditorDocument(document);

    expect(issues.map((issue) => issue.path)).toEqual(
      expect.arrayContaining(['codegen.className', 'codegen.initialState', 'transitions[0].timeout.value']),
    );
  });

  it('rejects qualified names for generated nested types', () => {
    const document = {
      ...sampleDocument,
      codegen: {
        ...sampleDocument.codegen,
        domainType: 'com.acme.Document',
        stateType: 'com.acme.DocumentState',
      },
    };

    expect(validateEditorDocument(document)).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          path: 'codegen.domainType',
          message: 'Domain type must be a valid Java identifier.',
        }),
        expect.objectContaining({
          path: 'codegen.stateType',
          message: 'State type must be a valid Java identifier.',
        }),
      ]),
    );
  });

  it('requires state labels that can be emitted as Java enum constants', () => {
    const document = {
      ...sampleDocument,
      states: [{ ...sampleDocument.states[0], label: 'READY FOR SIGN' }],
    };

    const issues = validateEditorDocument(document);

    expect(issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          path: 'states[0].label',
          message: 'State label must be a valid Java enum constant.',
        }),
      ]),
    );
  });

  it('reports duplicate transitions that match runtime transition identity', () => {
    const firstTransition = sampleDocument.transitions[0];
    const document = {
      ...sampleDocument,
      transitions: [
        firstTransition,
        {
          ...firstTransition,
          id: 'duplicate-id-is-different',
        },
      ],
    };

    expect(validateEditorDocument(document)).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          severity: 'error',
          path: 'transitions[1]',
          message: 'Duplicate transition matches transitions[0].',
        }),
      ]),
    );
  });

  it('allows transitions with the same source, target, and trigger when behavior differs', () => {
    const firstTransition = sampleDocument.transitions[0];
    const document = {
      ...sampleDocument,
      transitions: [
        firstTransition,
        {
          ...firstTransition,
          id: 'same-route-with-guard',
          conditions: [sampleDocument.behaviors.conditions[0].id],
        },
      ],
    };

    expect(validateEditorDocument(document)).not.toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          path: 'transitions[1]',
          message: 'Duplicate transition matches transitions[0].',
        }),
      ]),
    );
  });
});
