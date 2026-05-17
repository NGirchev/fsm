import { describe, expect, it } from 'vitest';
import { createEmptyDocument, validateEditorDocument } from './index';

describe('createEmptyDocument', () => {
  it('creates a valid starter flow that is not the sample document', () => {
    const document = createEmptyDocument();

    expect(document.name).toBe('Untitled FSM');
    expect(document.states).toEqual([{ id: 'initial', label: 'INITIAL', position: { x: 0, y: 0 } }]);
    expect(document.events).toEqual([]);
    expect(document.transitions).toEqual([]);
    expect(document.behaviors).toEqual({ conditions: [], actions: [] });
    expect(validateEditorDocument(document)).toEqual([]);
  });
});
