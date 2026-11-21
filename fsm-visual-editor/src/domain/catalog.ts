import type { CatalogBehavior } from './types';

/** Distinct flow kinds of the guard and action beans, the ones the filter applies to, in first-seen order. */
export function catalogFlowKinds(catalog: CatalogBehavior[]): string[] {
  return [...new Set(catalog.flatMap((item) =>
    (item.flowKind && (item.kind === 'guard' || item.kind === 'action') ? [item.flowKind] : [])))];
}

/** The filter to start with: the open flow's kind when the catalog has such guards or actions, otherwise all kinds (`''`). */
export function initialFlowKindFilter(catalog: CatalogBehavior[], flowKind?: string): string {
  return flowKind && catalogFlowKinds(catalog).includes(flowKind) ? flowKind : '';
}

/**
 * Beans offered by one picker row: of the filtered kind (`''` for all) and not chosen in another row.
 * The row's own bean stays whatever its kind, so a filter never hides a stored choice.
 */
export function catalogChoices(options: CatalogBehavior[], selected: string[], flowKind: string, current?: string): CatalogBehavior[] {
  return options.filter((item) => item.id === current
    || (!selected.includes(item.id) && (!flowKind || item.flowKind === flowKind)));
}
