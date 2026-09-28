import type { FsmEditorDocument } from './types';

export const GRAPH_PALETTE = [
  { name: 'Blue', ink: '#1d4ed8', fill: '#dbeafe' },
  { name: 'Violet', ink: '#6d28d9', fill: '#ede9fe' },
  { name: 'Teal', ink: '#0f766e', fill: '#ccfbf1' },
  { name: 'Pink', ink: '#be185d', fill: '#fce7f3' },
  { name: 'Orange', ink: '#9a3412', fill: '#ffedd5' },
  { name: 'Sky', ink: '#0369a1', fill: '#e0f2fe' },
  { name: 'Lime', ink: '#3f6212', fill: '#ecfccb' },
  { name: 'Magenta', ink: '#a21caf', fill: '#fae8ff' },
  { name: 'Indigo', ink: '#4338ca', fill: '#e0e7ff' },
  { name: 'Green', ink: '#166534', fill: '#dcfce7' },
  { name: 'Rose', ink: '#9f1239', fill: '#ffe4e6' },
  { name: 'Amber', ink: '#854d0e', fill: '#fef9c3' },
] as const;

export function isStateColor(value: unknown): value is string {
  return GRAPH_PALETTE.some((color) => color.ink === value);
}

export function randomStateColor(used: (string | undefined)[] = []): string {
  const available = GRAPH_PALETTE.filter((color) => !used.includes(color.ink));
  const choices = available.length ? available : GRAPH_PALETTE;
  return choices[Math.floor(Math.random() * choices.length)].ink;
}

/** Assign missing colors when creating/importing a document, never when loading a saved one. */
export function withStateColors(document: FsmEditorDocument): FsmEditorDocument {
  if (document.states.every((state) => state.color !== undefined)) return document;
  const used = document.states.map((state) => state.color);
  return { ...document, states: document.states.map((state) => {
    if (state.color !== undefined) return state;
    const color = randomStateColor(used);
    used.push(color);
    return { ...state, color };
  }) };
}
