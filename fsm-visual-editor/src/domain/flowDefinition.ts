import { createEmptyDocument } from './sample';
import { normalizeEditorDocument } from './documentGuards';
import { validateEditorDocument } from './validation';
import type { FsmEditorDocument, FsmTransition, TimeoutConfig } from './types';

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
  initialState: string;
  table: {
    autoTransitionEnabled: boolean;
    maxImmediateAutoTransitions: number;
    transitions: Record<string, RuntimeTransition[]>;
  };
  editor?: {
    name: string;
    codegen: FsmEditorDocument['codegen'];
    states: FsmEditorDocument['states'];
    events: FsmEditorDocument['events'];
    behaviors: FsmEditorDocument['behaviors'];
    transitionIds: Record<string, string[]>;
  };
}

export interface FlowVersion {
  flowKey: string;
  version: number;
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
  definition: FlowDefinition;
}

/** Runtime definitions are authoritative; editor metadata only restores presentation. */
export function fromFlowDefinition(definition: FlowDefinition): FsmEditorDocument {
  const empty = createEmptyDocument();
  const metadata = definition.editor && normalizeEditorDocument({
    ...empty, ...definition.editor, transitions: [],
  });
  const entries = Object.entries(definition.table.transitions);
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
  const usedIds = new Set<string>();
  const states = labels.map((label, index) => {
    const saved = metadata?.states.find((state) => state.label === label);
    let id = saved?.id || `state-${index}`;
    while (usedIds.has(id)) id += '-';
    usedIds.add(id);
    return { id, label, position: saved?.position ?? { x: index * 260, y: 100 }, description: saved?.description };
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
  const eventIds = [...new Set([
    ...(metadata?.events.map((event) => event.id) ?? []),
    ...transitions.flatMap((transition) => transition.trigger.kind === 'event' ? [transition.trigger.event] : []),
  ])];
  return {
    ...empty,
    name: metadata?.name ?? 'Order flow',
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
  return {
    initialState: document.codegen.initialState,
    table: {
      autoTransitionEnabled: document.autoTransitionEnabled,
      maxImmediateAutoTransitions: document.maxImmediateAutoTransitions ?? 0,
      transitions,
    },
    editor: {
      name: document.name, codegen: document.codegen, states: document.states, events: document.events,
      behaviors: document.behaviors,
      // JSONB may reorder object keys, so transition IDs are grouped by source too.
      transitionIds: Object.fromEntries(Object.keys(transitions).map((label) => [label,
        document.transitions.filter((transition) => states.get(transition.from) === label).map((transition) => transition.id)])),
    },
  };
}
