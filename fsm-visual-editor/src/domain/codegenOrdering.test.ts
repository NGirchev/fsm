import { describe, expect, it } from 'vitest';
import { orderTransitionsFromInitial } from './codegenOrdering';
import { sampleDocument } from './sample';
import type { FsmEditorDocument, FsmTransition } from './types';

describe('orderTransitionsFromInitial', () => {
  it('orders reachable transitions from the initial state and appends unreachable transitions', () => {
    const document = testDocument([
      transition('b-c', 'b', 'c', 'TO_C'),
      transition('x-y', 'x', 'y', 'TO_Y'),
      transition('a-b', 'a', 'b', 'TO_B'),
    ]);

    expect(orderTransitionsFromInitial(document).map((item) => item.id)).toEqual(['a-b', 'b-c', 'x-y']);
  });

  it('preserves relative order for transitions from the same source', () => {
    const document = testDocument([
      transition('a-c', 'a', 'c', 'TO_C'),
      transition('a-b', 'a', 'b', 'TO_B'),
      transition('b-c', 'b', 'c', 'TO_C'),
    ]);

    expect(orderTransitionsFromInitial(document).map((item) => item.id)).toEqual(['a-c', 'a-b', 'b-c']);
  });

  it('falls back to transition order when the initial state label is missing', () => {
    const document = {
      ...testDocument([
        transition('b-c', 'b', 'c', 'TO_C'),
        transition('a-b', 'a', 'b', 'TO_B'),
      ]),
      codegen: { ...sampleDocument.codegen, initialState: 'UNKNOWN' },
    };

    expect(orderTransitionsFromInitial(document).map((item) => item.id)).toEqual(['b-c', 'a-b']);
  });
});

function testDocument(transitions: FsmTransition[]): FsmEditorDocument {
  return {
    ...sampleDocument,
    states: [
      { id: 'a', label: 'A', position: { x: 0, y: 0 } },
      { id: 'b', label: 'B', position: { x: 10, y: 0 } },
      { id: 'c', label: 'C', position: { x: 20, y: 0 } },
      { id: 'x', label: 'X', position: { x: 30, y: 0 } },
      { id: 'y', label: 'Y', position: { x: 40, y: 0 } },
    ],
    events: [{ id: 'TO_B' }, { id: 'TO_C' }, { id: 'TO_Y' }],
    transitions,
    codegen: { ...sampleDocument.codegen, initialState: 'A' },
  };
}

function transition(id: string, from: string, to: string, event: string): FsmTransition {
  return {
    id,
    from,
    to,
    trigger: { kind: 'event', event },
    conditions: [],
    actions: [],
    postActions: [],
  };
}
