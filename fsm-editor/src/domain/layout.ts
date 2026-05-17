import type { Point } from './types';

export function autoLayout(index: number, total: number): Point {
  const radius = Math.max(220, total * 34);
  const angle = total <= 1 ? 0 : (2 * Math.PI * index) / total - Math.PI / 2;

  return {
    x: Math.round(Math.cos(angle) * radius),
    y: Math.round(Math.sin(angle) * radius),
  };
}
