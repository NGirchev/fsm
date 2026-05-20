import { CODEGEN_STYLES, EDITOR_FORMAT_VERSION, type CodegenStyle, type FsmEditorDocument } from './types';

interface LegacyTransition {
  id: string;
  from: string;
  to: string;
  event?: string | null;
  trigger?: unknown;
  conditions?: string[];
  actions?: string[];
  postActions?: string[];
  timeout?: unknown;
}

interface LegacyDocument {
  formatVersion?: number;
  name?: string;
  autoTransitionEnabled?: boolean;
  codegen?: Partial<FsmEditorDocument['codegen']>;
  states?: unknown[];
  transitions?: unknown[];
  events?: unknown[];
  behaviors?: unknown;
  updatedAt?: string;
}

export function isEditorDocument(value: unknown): value is FsmEditorDocument {
  return normalizeEditorDocument(value) !== null;
}

export function normalizeEditorDocument(value: unknown): FsmEditorDocument | null {
  if (!value || typeof value !== 'object') {
    return null;
  }

  const candidate = value as LegacyDocument;

  if (
    candidate.formatVersion !== 1 &&
    candidate.formatVersion !== EDITOR_FORMAT_VERSION
  ) {
    return null;
  }

  if (!Array.isArray(candidate.states) || !Array.isArray(candidate.transitions)) {
    return null;
  }

  if (!hasValidDocumentMetadata(candidate)) {
    return null;
  }

  if (!candidate.states.every(isState) || !candidate.transitions.every(isLegacyTransition)) {
    return null;
  }

  if (!isOptionalCodegen(candidate.codegen) || !isOptionalBehaviors(candidate.behaviors)) {
    return null;
  }

  const behaviors = candidate.behaviors ?? {};
  const codegen = {
    packageName: candidate.codegen?.packageName ?? 'io.github.ngirchev.fsm.generated',
    className: candidate.codegen?.className ?? 'GeneratedFsmFactory',
    factoryMethodName: candidate.codegen?.factoryMethodName ?? 'create',
    domainType: candidate.codegen?.domainType ?? 'Document',
    stateType: candidate.codegen?.stateType ?? 'DocumentState',
    eventType: normalizeEventType(candidate.codegen?.eventType, candidate.codegen?.stateType ?? 'DocumentState'),
    initialState: candidate.codegen?.initialState ?? '',
    style: normalizeCodegenStyle(candidate.codegen?.style),
  };

  const eventIds = new Set<string>();
  const migratedTransitions = candidate.transitions.map((transition) => {
    const trigger = normalizeTrigger(transition);

    if (trigger.kind === 'event') {
      eventIds.add(trigger.event);
    }

    return {
      ...transition,
      trigger,
      conditions: Array.isArray(transition.conditions) ? transition.conditions : [],
      actions: Array.isArray(transition.actions) ? transition.actions : [],
      postActions: Array.isArray(transition.postActions) ? transition.postActions : [],
    };
  });

  const existingEvents = Array.isArray(candidate.events)
    ? candidate.events.filter(isEventRef).map((event) => {
        eventIds.add(event.id);
        return event;
      })
    : [];
  const missingEvents = [...eventIds]
    .filter((id) => !existingEvents.some((event) => event.id === id))
    .map((id) => ({ id }));

  return {
    ...candidate,
    formatVersion: EDITOR_FORMAT_VERSION,
    name: candidate.name ?? 'Untitled FSM',
    autoTransitionEnabled: candidate.autoTransitionEnabled ?? false,
    codegen,
    states: candidate.states as FsmEditorDocument['states'],
    events: [...existingEvents, ...missingEvents],
    transitions: migratedTransitions as FsmEditorDocument['transitions'],
    behaviors: {
      conditions: behaviors.conditions ?? [],
      actions: behaviors.actions ?? [],
    },
  };
}

function hasValidDocumentMetadata(value: LegacyDocument): boolean {
  return (
    (value.name === undefined || typeof value.name === 'string') &&
    (value.autoTransitionEnabled === undefined || typeof value.autoTransitionEnabled === 'boolean') &&
    (value.updatedAt === undefined || typeof value.updatedAt === 'string') &&
    (value.events === undefined || Array.isArray(value.events))
  );
}

function isState(value: unknown): value is FsmEditorDocument['states'][number] {
  if (!isRecord(value)) {
    return false;
  }

  return (
    typeof value.id === 'string' &&
    typeof value.label === 'string' &&
    isPosition(value.position) &&
    (value.description === undefined || typeof value.description === 'string')
  );
}

function isPosition(value: unknown): value is FsmEditorDocument['states'][number]['position'] {
  return isRecord(value) && Number.isFinite(value.x) && Number.isFinite(value.y);
}

function isLegacyTransition(value: unknown): value is LegacyTransition {
  if (!isRecord(value)) {
    return false;
  }

  return (
    typeof value.id === 'string' &&
    typeof value.from === 'string' &&
    typeof value.to === 'string' &&
    (value.event === undefined || value.event === null || typeof value.event === 'string') &&
    isOptionalStringArray(value.conditions) &&
    isOptionalStringArray(value.actions) &&
    isOptionalStringArray(value.postActions)
  );
}

function isOptionalCodegen(value: unknown): value is Partial<FsmEditorDocument['codegen']> | undefined {
  if (value === undefined) {
    return true;
  }

  if (!isRecord(value)) {
    return false;
  }

  return ['packageName', 'className', 'factoryMethodName', 'domainType', 'stateType', 'eventType', 'initialState', 'style'].every(
    (key) => value[key] === undefined || typeof value[key] === 'string',
  );
}

function isOptionalBehaviors(value: unknown): value is Partial<FsmEditorDocument['behaviors']> | undefined {
  if (value === undefined) {
    return true;
  }

  if (!isRecord(value)) {
    return false;
  }

  return isOptionalBehaviorArray(value.conditions) && isOptionalBehaviorArray(value.actions);
}

function isOptionalBehaviorArray(value: unknown): value is FsmEditorDocument['behaviors']['conditions'] | undefined {
  return value === undefined || (Array.isArray(value) && value.every(isEventRef));
}

function isOptionalStringArray(value: unknown): value is string[] | undefined {
  return value === undefined || (Array.isArray(value) && value.every((item) => typeof item === 'string'));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object');
}

function normalizeTrigger(transition: LegacyTransition): FsmEditorDocument['transitions'][number]['trigger'] {
  if (isTrigger(transition.trigger)) {
    return transition.trigger;
  }

  if (transition.event) {
    return { kind: 'event', event: transition.event };
  }

  return { kind: 'auto' };
}

function isTrigger(value: unknown): value is FsmEditorDocument['transitions'][number]['trigger'] {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const candidate = value as Partial<FsmEditorDocument['transitions'][number]['trigger']>;
  return candidate.kind === 'auto' || (candidate.kind === 'event' && typeof candidate.event === 'string');
}

function isEventRef(value: unknown): value is FsmEditorDocument['events'][number] {
  return Boolean(value && typeof value === 'object' && typeof (value as { id?: unknown }).id === 'string');
}

function normalizeEventType(eventType: string | undefined, stateType: string): string {
  if (eventType && !['String', 'Integer', 'Long', 'Boolean'].includes(eventType)) {
    return eventType;
  }

  if (stateType.endsWith('State')) {
    return `${stateType.slice(0, -'State'.length)}Event`;
  }

  return `${stateType}Event`;
}

function normalizeCodegenStyle(style: string | undefined): CodegenStyle {
  return CODEGEN_STYLES.includes(style as CodegenStyle) ? (style as CodegenStyle) : 'fluent';
}
