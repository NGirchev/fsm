import { describe, expect, it } from 'vitest';
import { AUTO_COLOR, GRAPH_PALETTE, colorsForIds, graphColor } from './graphColors';

function luminance(hex: string) {
  const channels = hex.slice(1).match(/../g)!.map((part) => {
    const value = parseInt(part, 16) / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
}

describe('graph colors', () => {
  it('keeps text contrast above 4.5:1 for every state, event and automatic transition', () => {
    for (const { ink, fill } of [...GRAPH_PALETTE, AUTO_COLOR]) {
      expect((luminance(fill) + 0.05) / (luminance(ink) + 0.05), ink).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('preserves colors through document serialization and independent ordering', () => {
    const ids = ['new', 'signed', 'TO_END', 'USER_SIGN', 'Событие', ''];
    const before = new Map(ids.map((id) => [id, graphColor(id)]));
    const imported: string[] = JSON.parse(JSON.stringify(ids));
    for (const id of imported.reverse()) expect(graphColor(id)).toEqual(before.get(id));
    expect(new Set(GRAPH_PALETTE.map((color) => color.ink)).size).toBe(12);
  });

  it('resolves palette collisions before reusing colors in larger graphs', () => {
    const ids = Array.from({ length: 12 }, (_, index) => `state-${index}`);
    const colors = colorsForIds(ids);
    expect(new Set([...colors.values()].map((color) => color.ink)).size).toBe(12);
    expect(colorsForIds([...ids].reverse())).toEqual(colors);
    expect(colorsForIds([...ids, 'extra']).size).toBe(13);
  });
});
