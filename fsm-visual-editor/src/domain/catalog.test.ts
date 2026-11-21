import { describe, expect, it } from 'vitest';
import { catalogChoices, catalogFlowKinds, initialFlowKindFilter } from './catalog';
import type { CatalogBehavior } from './types';

const bean = (id: string, flowKind?: string): CatalogBehavior => ({ id, kind: 'guard', description: id, ...(flowKind ? { flowKind } : {}) });
const catalog = [bean('agent.hasError', 'agent'), bean('document.isImage', 'document'), bean('agent.hasToolCall', 'agent'),
  bean('legacyGuard', 'other')];
const ids = (items: CatalogBehavior[]) => items.map((item) => item.id);

describe('catalog filter by flow kind', () => {
  it('lists each kind once and nothing for a catalog without kinds', () => {
    expect(catalogFlowKinds(catalog)).toEqual(['agent', 'document', 'other']);
    expect(catalogFlowKinds([bean('plain')])).toEqual([]);
  });

  it('starts with the kind of the open flow when the catalog has such beans, otherwise with all kinds', () => {
    expect(initialFlowKindFilter(catalog, 'document')).toBe('document');
    expect(initialFlowKindFilter(catalog, 'request')).toBe('');
    expect(initialFlowKindFilter(catalog)).toBe('');
    expect(initialFlowKindFilter([bean('plain')], 'agent')).toBe('');
  });

  it('ignores listeners, which the filter does not apply to', () => {
    const listener: CatalogBehavior = { id: 'agent.audit', kind: 'stateListener', description: 'Audit', flowKind: 'agent' };
    expect(catalogFlowKinds([listener, bean('document.isImage', 'document')])).toEqual(['document']);
    expect(initialFlowKindFilter([listener, bean('document.isImage', 'document')], 'agent')).toBe('');
  });

  it('offers beans of the chosen kind or of every kind, without the ones already chosen', () => {
    expect(ids(catalogChoices(catalog, [], 'agent'))).toEqual(['agent.hasError', 'agent.hasToolCall']);
    expect(ids(catalogChoices(catalog, ['agent.hasError'], 'agent'))).toEqual(['agent.hasToolCall']);
    expect(ids(catalogChoices(catalog, ['agent.hasError'], ''))).toEqual(['document.isImage', 'agent.hasToolCall', 'legacyGuard']);
    expect(catalogChoices(catalog, [], 'request')).toEqual([]);
  });

  it('keeps the bean of the edited row even when it belongs to another kind', () => {
    expect(ids(catalogChoices(catalog, ['document.isImage', 'agent.hasError'], 'agent', 'document.isImage')))
      .toEqual(['document.isImage', 'agent.hasToolCall']);
  });
});
