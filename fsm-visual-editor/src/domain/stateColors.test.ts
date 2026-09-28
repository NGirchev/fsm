import { afterEach, describe, expect, it, vi } from 'vitest';
import { GRAPH_PALETTE, randomStateColor, withStateColors } from './stateColors';
import { sampleDocument } from './sample';
import { normalizeEditorDocument } from './documentGuards';
import { fromFlowDefinition, toFlowDefinition } from './flowDefinition';

afterEach(() => vi.restoreAllMocks());

describe('persisted state colors', () => {
  it('uses randomness and prefers unused colors', () => {
    const random = vi.spyOn(Math, 'random').mockReturnValue(0);
    expect(randomStateColor()).toBe(GRAPH_PALETTE[0].ink);
    expect(randomStateColor([GRAPH_PALETTE[0].ink])).toBe(GRAPH_PALETTE[1].ink);
    random.mockReturnValue(0.999);
    expect(randomStateColor()).toBe(GRAPH_PALETTE.at(-1)!.ink);
    expect(GRAPH_PALETTE.map((color) => color.ink)).toContain(randomStateColor(GRAPH_PALETTE.map((color) => color.ink)));
  });

  it('initializes legacy documents once without mutating them or reassigning existing colors', () => {
    const document = withStateColors(sampleDocument);
    expect(document.states.every((state) => state.color)).toBe(true);
    expect(new Set(document.states.map((state) => state.color)).size).toBe(document.states.length);
    expect(sampleDocument.states.every((state) => state.color === undefined)).toBe(true);
    expect(withStateColors(document)).toBe(document);
    const extended = withStateColors({ ...document, states: [...document.states,
      { id: 'extra', label: 'EXTRA', position: { x: 0, y: 0 } }] });
    expect(extended.states.slice(0, document.states.length)).toEqual(document.states);
  });

  it('preserves chosen colors through editor JSON and runtime metadata without changing execution', () => {
    const document = withStateColors(sampleDocument);
    document.states[0].color = GRAPH_PALETTE[7].ink;
    expect(normalizeEditorDocument(JSON.parse(JSON.stringify(document)))).toEqual(document);
    const definition = toFlowDefinition(document);
    expect(definition.table).toEqual(toFlowDefinition(sampleDocument).table);
    const restored = fromFlowDefinition(JSON.parse(JSON.stringify(definition)));
    expect(restored.states.map((state) => state.color)).toEqual(document.states.map((state) => state.color));
  });

  it.each([null, 3, '#ffffff', 'url(example)'])('rejects invalid imported color %s', (color) => {
    expect(normalizeEditorDocument({ ...sampleDocument,
      states: [{ ...sampleDocument.states[0], color }] })).toBeNull();
  });
});
