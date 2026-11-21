import { describe, expect, it } from 'vitest';
import { autoLayout, verticalLayout } from './layout';
import { sampleDocument } from './sample';

describe('autoLayout', () => {
  it('keeps a single state on the right side of the canvas', () => {
    expect(autoLayout(0, 1)).toEqual({ x: 220, y: 0 });
  });

  it('spreads multiple states around a stable circle', () => {
    expect([autoLayout(0, 4), autoLayout(1, 4), autoLayout(2, 4), autoLayout(3, 4)]).toEqual([
      { x: 0, y: -220 },
      { x: 220, y: 0 },
      { x: 0, y: 220 },
      { x: -220, y: 0 },
    ]);
  });
});

describe('verticalLayout', () => {
  const states = ['A', 'B', 'C', 'D'].map((label) => ({ id: label.toLowerCase(), label, position: { x: 0, y: 0 } }));
  const transition = (from: string, to: string) => ({ id: `${from}-${to}`, from, to, trigger: { kind: 'auto' as const },
    conditions: [], actions: [], postActions: [] });

  it('places each step of a chain below the previous one, starting from the initial state', () => {
    const positions = verticalLayout({ states, codegen: { ...sampleDocument.codegen, initialState: 'A' },
      transitions: [transition('a', 'b'), transition('b', 'c'), transition('c', 'd')] });

    const ys = ['a', 'b', 'c', 'd'].map((id) => positions.get(id)!.y);
    expect(ys).toEqual([...ys].sort((left, right) => left - right));
    expect(new Set(ys).size).toBe(4);
    expect(new Set(['a', 'b', 'c', 'd'].map((id) => positions.get(id)!.x)).size).toBe(1);
  });

  it('keeps the initial state on top when a later state loops back to it', () => {
    const positions = verticalLayout({ states, codegen: { ...sampleDocument.codegen, initialState: 'B' },
      transitions: [transition('b', 'c'), transition('c', 'a'), transition('a', 'b'), transition('c', 'd')] });

    const top = Math.min(...[...positions.values()].map((point) => point.y));
    expect(positions.get('b')!.y).toBe(top);
  });

  it('puts branches side by side on the same layer', () => {
    const positions = verticalLayout({ states, codegen: { ...sampleDocument.codegen, initialState: 'A' },
      transitions: [transition('a', 'b'), transition('a', 'c')] });

    expect(positions.get('b')!.y).toBe(positions.get('c')!.y);
    expect(positions.get('b')!.x).not.toBe(positions.get('c')!.x);
    expect(positions.get('d')).toBeDefined();
  });
});
