import type { CSSProperties } from 'react';
import { GRAPH_PALETTE } from './domain/stateColors';
export { GRAPH_PALETTE } from './domain/stateColors';

export const AUTO_COLOR = { ink: '#475569', fill: '#e2e8f0' };

export function graphColor(id: string) {
  let hash = 0;
  for (const character of id) hash = (Math.imul(hash, 31) + character.codePointAt(0)!) | 0;
  return GRAPH_PALETTE[(hash >>> 0) % GRAPH_PALETTE.length];
}

export function colorsForIds(ids: string[]) {
  const colors = new Map<string, (typeof GRAPH_PALETTE)[number]>();
  const used = new Set<number>();
  for (const id of [...new Set(ids)].sort()) {
    let index = GRAPH_PALETTE.indexOf(graphColor(id));
    if (used.size < GRAPH_PALETTE.length) {
      while (used.has(index)) index = (index + 1) % GRAPH_PALETTE.length;
    }
    used.add(index);
    colors.set(id, GRAPH_PALETTE[index]);
  }
  return colors;
}

export function colorVariables(color: { ink: string; fill: string }): CSSProperties {
  return { '--item-ink': color.ink, '--item-fill': color.fill } as CSSProperties;
}
