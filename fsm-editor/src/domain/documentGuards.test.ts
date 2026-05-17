import { describe, expect, it } from 'vitest';
import { normalizeEditorDocument, sampleDocument } from './index';

describe('normalizeEditorDocument', () => {
  it('migrates legacy event fields and registers missing events', () => {
    const migrated = normalizeEditorDocument({
      ...sampleDocument,
      formatVersion: 1,
      events: [{ id: 'EXISTING_EVENT' }],
      transitions: [
        { ...sampleDocument.transitions[0], event: 'LEGACY_EVENT', trigger: undefined },
        { ...sampleDocument.transitions[1], trigger: { kind: 'auto' } },
      ],
    });

    expect(migrated?.formatVersion).toBe(2);
    expect(migrated?.events).toEqual([{ id: 'EXISTING_EVENT' }, { id: 'LEGACY_EVENT' }]);
    expect(migrated?.transitions[0].trigger).toEqual({ kind: 'event', event: 'LEGACY_EVENT' });
    expect(migrated?.transitions[1].trigger).toEqual({ kind: 'auto' });
  });

  it('defaults optional editor fields for older saved documents', () => {
    const migrated = normalizeEditorDocument({
      formatVersion: 1,
      states: sampleDocument.states,
      transitions: [],
    });

    expect(migrated).toEqual(
      expect.objectContaining({
        name: 'Untitled FSM',
        autoTransitionEnabled: false,
        codegen: expect.objectContaining({
          className: 'GeneratedFsmFactory',
          domainType: 'Document',
          stateType: 'DocumentState',
          eventType: 'DocumentEvent',
        }),
        events: [],
        behaviors: { conditions: [], actions: [] },
      }),
    );
  });

  it('rejects malformed nested arrays and metadata', () => {
    expect(normalizeEditorDocument({ ...sampleDocument, events: {} })).toBeNull();
    expect(normalizeEditorDocument({ ...sampleDocument, behaviors: { conditions: [null] } })).toBeNull();
    expect(
      normalizeEditorDocument({ ...sampleDocument, transitions: [{ ...sampleDocument.transitions[0], actions: [1] }] }),
    ).toBeNull();
    expect(
      normalizeEditorDocument({ ...sampleDocument, states: [{ ...sampleDocument.states[0], position: { x: '0', y: 0 } }] }),
    ).toBeNull();
  });
});
