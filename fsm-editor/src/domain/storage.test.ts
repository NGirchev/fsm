import { describe, expect, it } from 'vitest';
import {
  CURRENT_PROJECT_ID_KEY,
  EDITOR_STORAGE_KEY,
  loadCurrentProjectId,
  loadEditorDocument,
  sampleDocument,
  saveCurrentProjectId,
  saveEditorDocument,
} from './index';

describe('editor document storage', () => {
  it('saves and loads an editor document', () => {
    const entries = new Map<string, string>();
    const storage = {
      getItem: (key: string) => entries.get(key) ?? null,
      setItem: (key: string, value: string) => entries.set(key, value),
    };

    saveEditorDocument(sampleDocument, storage);

    expect(loadEditorDocument(storage)).toEqual(sampleDocument);
  });

  it('returns null for missing or invalid saved documents', () => {
    expect(loadEditorDocument({ getItem: () => null })).toBeNull();
    expect(loadEditorDocument({ getItem: () => '{"formatVersion":999}' })).toBeNull();
    expect(loadEditorDocument({ getItem: () => 'not-json' })).toBeNull();
  });

  it('rejects malformed imported array elements instead of normalizing them', () => {
    const malformedTransition = {
      ...sampleDocument,
      transitions: [null],
    };
    const malformedState = {
      ...sampleDocument,
      states: [null],
    };

    expect(loadEditorDocument({ getItem: () => JSON.stringify(malformedTransition) })).toBeNull();
    expect(loadEditorDocument({ getItem: () => JSON.stringify(malformedState) })).toBeNull();
  });

  it('migrates a legacy event field to explicit triggers and event registry', () => {
    const legacyDocument = {
      ...sampleDocument,
      formatVersion: 1,
      events: undefined,
      codegen: { ...sampleDocument.codegen, eventType: 'String' },
      transitions: [
        { ...sampleDocument.transitions[0], event: 'TO_READY', trigger: undefined },
        { ...sampleDocument.transitions[1], event: null, trigger: undefined },
      ],
    };

    const storage = {
      getItem: () => JSON.stringify(legacyDocument),
    };

    const migrated = loadEditorDocument(storage);

    expect(migrated?.formatVersion).toBe(2);
    expect(migrated?.codegen.eventType).toBe('DocumentEvent');
    expect(migrated?.events).toEqual([{ id: 'TO_READY' }]);
    expect(migrated?.transitions[0].trigger).toEqual({ kind: 'event', event: 'TO_READY' });
    expect(migrated?.transitions[1].trigger).toEqual({ kind: 'auto' });
  });

  it('uses a stable storage key', () => {
    expect(EDITOR_STORAGE_KEY).toBe('fsm-editor.current-document');
  });

  it('saves and loads current project id', () => {
    const entries = new Map<string, string>();
    const storage = {
      getItem: (key: string) => entries.get(key) ?? null,
      setItem: (key: string, value: string) => entries.set(key, value),
    };

    saveCurrentProjectId('order-fsm', storage);

    expect(loadCurrentProjectId(storage)).toBe('order-fsm');
    expect(CURRENT_PROJECT_ID_KEY).toBe('fsm-editor.current-project-id');
  });

  it('tolerates unavailable storage APIs', () => {
    expect(loadEditorDocument({ getItem: () => { throw new Error('blocked'); } })).toBeNull();
    expect(loadCurrentProjectId({ getItem: () => { throw new Error('blocked'); } })).toBeNull();

    expect(() => saveEditorDocument(sampleDocument, { setItem: () => { throw new Error('full'); } })).not.toThrow();
    expect(() => saveCurrentProjectId('order-fsm', { setItem: () => { throw new Error('blocked'); } })).not.toThrow();
  });
});
