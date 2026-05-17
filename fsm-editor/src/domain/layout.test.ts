import { describe, expect, it } from 'vitest';
import { autoLayout } from './layout';

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
