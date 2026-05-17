import { describe, expect, it } from 'vitest';
import { uniqueId } from './ids';

describe('uniqueId', () => {
  it('uses identifier-safe suffixes for duplicate generated ids', () => {
    expect(uniqueId('EVENT', new Set(['EVENT']))).toBe('EVENT_2');
    expect(uniqueId('guardName', new Set(['guardName']))).toBe('guardName_2');
  });

  it('keeps incrementing until the candidate is unused', () => {
    expect(uniqueId('EVENT', new Set(['EVENT', 'EVENT_2', 'EVENT_3']))).toBe('EVENT_4');
  });
});
