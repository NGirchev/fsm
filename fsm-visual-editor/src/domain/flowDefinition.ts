import { createEmptyDocument } from './sample';
import { normalizeEditorDocument } from './documentGuards';
import { validateEditorDocument } from './validation';
import { verticalLayout } from './layout';
import type { FinalOutcome, FsmEditorDocument, FsmTransition, TimeoutConfig } from './types';

export interface RuntimeTransition {
  from: string;
  event: string | null;
  to: {
    state: string;
    conditions: string[];
    actions: string[];
    postActions: string[];
    timeout: TimeoutConfig | null;
    autoTransitionEnabled?: boolean;
  };
}

export interface FlowDefinition {
  execution?: FsmEditorDocument['execution'];
  initialState: string;
  table: {
    autoTransitionEnabled: boolean;
    maxImmediateAutoTransitions: number;
    transitions: Record<string, RuntimeTransition[]>;
  };
  /** States where a run ends, with its outcome. Absent when no state is marked. */
  finalStates?: Record<string, FinalOutcome>;
  editor?: {
    name: string;
    codegen: FsmEditorDocument['codegen'];
    states: FsmEditorDocument['states'];
    events: FsmEditorDocument['events'];
    behaviors: FsmEditorDocument['behaviors'];
    transitionIds: Record<string, string[]>;
  };
}

/** Runtime definitions are authoritative; editor metadata only restores presentation. */
export function fromFlowDefinition(definition: FlowDefinition): FsmEditorDocument {
  const empty = createEmptyDocument();
  const metadata = definition.editor && normalizeEditorDocument({
    ...empty, ...definition.editor, transitions: [],
  });
  const entries = Object.entries(definition.table.transitions);
  if (metadata) {
    const rank = (label: string) => {
      const index = metadata.states.findIndex((state) => state.label === label);
      return index < 0 ? metadata.states.length : index;
    };
    entries.sort(([left], [right]) => rank(left) - rank(right));
  }
  const labels = [definition.initialState];
  // Database JSON objects may reorder keys. Lay out reachable states from the initial state.
  for (let index = 0; index < labels.length; index++) {
    const outgoing = Object.hasOwn(definition.table.transitions, labels[index]) ? definition.table.transitions[labels[index]] : [];
    outgoing.forEach((transition) => {
      if (!labels.includes(transition.to.state)) labels.push(transition.to.state);
    });
  }
  [...new Set([
    ...entries.map(([state]) => state),
    ...entries.flatMap(([, transitions]) => transitions.map((transition) => transition.to.state)),
    definition.initialState,
  ])].forEach((label) => { if (!labels.includes(label)) labels.push(label); });
  if (metadata) {
    const savedLabels = metadata.states.map((state) => state.label).filter((label) => labels.includes(label));
    const ordered = [...savedLabels, ...labels.filter((label) => !savedLabels.includes(label))];
    labels.splice(0, labels.length, ...ordered);
  }
  const usedIds = new Set<string>();
  const finalStates = definition.finalStates ?? {};
  const unplaced = new Set<string>();
  const states = labels.map((label, index) => {
    const saved = metadata?.states.find((state) => state.label === label);
    let id = saved?.id || `state-${index}`;
    while (usedIds.has(id)) id += '-';
    usedIds.add(id);
    if (!saved) unplaced.add(id);
    return { id, label, position: saved?.position ?? { x: 0, y: 0 }, description: saved?.description,
      ...(saved?.color ? { color: saved.color } : {}),
      ...(Object.hasOwn(finalStates, label) ? { final: finalStates[label] } : {}) };
  });
  const stateId = (label: string) => states.find((state) => state.label === label)!.id;
  const transitionIds = new Set<string>();
  const transitions: FsmTransition[] = entries.flatMap(([from, items]) => items.map((transition, index) => {
    const savedId = definition.editor?.transitionIds?.[from]?.[index];
    let id = typeof savedId === 'string' && savedId ? savedId : `transition-${transitionIds.size}`;
    while (transitionIds.has(id)) id += '-';
    transitionIds.add(id);
    return {
      id,
      from: stateId(transition.from),
      to: stateId(transition.to.state),
      trigger: transition.event == null ? { kind: 'auto' } : { kind: 'event', event: transition.event },
      conditions: transition.to.conditions,
      actions: transition.to.actions,
      postActions: transition.to.postActions,
      timeout: transition.to.timeout ?? undefined,
      autoTransitionEnabled: transition.to.autoTransitionEnabled ?? false,
    };
  }));
  // States without a saved position (a definition written by hand or by code) get a top-down layout.
  if (unplaced.size) {
    const layout = verticalLayout({ states, transitions, codegen: { ...empty.codegen, initialState: definition.initialState } });
    states.forEach((state) => { if (unplaced.has(state.id)) state.position = layout.get(state.id)!; });
  }
  const eventIds = [...new Set([
    ...(metadata?.events.map((event) => event.id) ?? []),
    ...transitions.flatMap((transition) => transition.trigger.kind === 'event' ? [transition.trigger.event] : []),
  ])];
  return {
    ...empty,
    execution: definition.execution,
    name: metadata?.name ?? 'Flow',
    codegen: { ...(metadata?.codegen ?? empty.codegen), stateType: 'String', initialState: definition.initialState },
    autoTransitionEnabled: definition.table.autoTransitionEnabled,
    maxImmediateAutoTransitions: definition.table.maxImmediateAutoTransitions ?? 0,
    states, transitions,
    events: eventIds.map((id) => metadata?.events.find((event) => event.id === id) ?? { id }),
    behaviors: {
      conditions: [...new Set([...(metadata?.behaviors.conditions.map((item) => item.id) ?? []),
        ...transitions.flatMap((transition) => transition.conditions)])]
        .map((id) => metadata?.behaviors.conditions.find((item) => item.id === id) ?? { id }),
      actions: [...new Set([...(metadata?.behaviors.actions.map((item) => item.id) ?? []),
        ...transitions.flatMap((transition) => [...transition.actions, ...transition.postActions])])]
        .map((id) => metadata?.behaviors.actions.find((item) => item.id === id) ?? { id }),
    },
  };
}

export function toFlowDefinition(document: FsmEditorDocument): FlowDefinition {
  const errors = validateEditorDocument(document, true).filter((issue) => issue.severity === 'error');
  if (errors.length) throw new Error(errors.map((issue) => issue.message).join(' '));
  const states = new Map(document.states.map((state) => [state.id, state.label]));
  // A null-prototype map also supports state names such as "__proto__".
  const transitions: Record<string, RuntimeTransition[]> = Object.create(null);
  document.states.forEach((state) => { transitions[state.label] = []; });
  document.transitions.forEach((transition) => {
    const from = states.get(transition.from)!;
    transitions[from].push({
      from,
      event: transition.trigger.kind === 'event' ? transition.trigger.event : null,
      to: {
        state: states.get(transition.to)!,
        conditions: transition.conditions,
        actions: transition.actions,
        postActions: transition.postActions,
        timeout: transition.timeout ?? null,
        autoTransitionEnabled: transition.autoTransitionEnabled ?? false,
      },
    });
  });
  const finalStates = document.states.filter((state) => state.final).map((state) => [state.label, state.final!] as const);
  return {
    initialState: document.codegen.initialState,
    ...(document.execution ? { execution: document.execution } : {}),
    ...(finalStates.length ? { finalStates: Object.fromEntries(finalStates) } : {}),
    table: {
      autoTransitionEnabled: document.autoTransitionEnabled,
      maxImmediateAutoTransitions: document.maxImmediateAutoTransitions ?? 0,
      transitions,
    },
    editor: {
      // The final-state marking lives in `finalStates`, the part the backend validates.
      name: document.name, codegen: document.codegen, states: document.states.map(({ final: _final, ...state }) => state),
      events: document.events,
      behaviors: document.behaviors,
      // JSONB may reorder object keys, so transition IDs are grouped by source too.
      transitionIds: Object.fromEntries(Object.keys(transitions).map((label) => [label,
        document.transitions.filter((transition) => states.get(transition.from) === label).map((transition) => transition.id)])),
    },
  };
}
