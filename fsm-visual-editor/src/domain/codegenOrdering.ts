import type { FsmEditorDocument, FsmTransition } from './types';

export function orderTransitionsFromInitial(document: FsmEditorDocument): FsmTransition[] {
  const initialState = document.states.find((state) => state.label === document.codegen.initialState);

  if (!initialState) {
    return [...document.transitions];
  }

  const transitionsBySource = new Map<string, Array<{ index: number; transition: FsmTransition }>>();

  document.transitions.forEach((transition, index) => {
    const transitions = transitionsBySource.get(transition.from) ?? [];
    transitions.push({ index, transition });
    transitionsBySource.set(transition.from, transitions);
  });

  const result: FsmTransition[] = [];
  const emittedIndexes = new Set<number>();
  const visitedStates = new Set<string>();
  const queuedStates = new Set<string>([initialState.id]);
  const queue = [initialState.id];

  while (queue.length > 0) {
    const source = queue.shift()!;
    queuedStates.delete(source);

    if (visitedStates.has(source)) {
      continue;
    }

    visitedStates.add(source);

    for (const entry of transitionsBySource.get(source) ?? []) {
      result.push(entry.transition);
      emittedIndexes.add(entry.index);

      if (!visitedStates.has(entry.transition.to) && !queuedStates.has(entry.transition.to)) {
        queue.push(entry.transition.to);
        queuedStates.add(entry.transition.to);
      }
    }
  }

  document.transitions.forEach((transition, index) => {
    if (!emittedIndexes.has(index)) {
      result.push(transition);
    }
  });

  return result;
}
