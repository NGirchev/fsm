export const EDITOR_FORMAT_VERSION = 2 as const;

export const TIME_UNITS = [
  'NANOSECONDS',
  'MICROSECONDS',
  'MILLISECONDS',
  'SECONDS',
  'MINUTES',
  'HOURS',
  'DAYS',
] as const;

export type TimeUnit = (typeof TIME_UNITS)[number];

export const CODEGEN_STYLES = ['fluent', 'builder'] as const;

export type CodegenStyle = (typeof CODEGEN_STYLES)[number];

export interface Point {
  x: number;
  y: number;
}

export const FINAL_OUTCOMES = ['SUCCESS', 'FAILURE', 'LIMIT'] as const;

export type FinalOutcome = (typeof FINAL_OUTCOMES)[number];

export interface FsmState {
  id: string;
  label: string;
  position: Point;
  description?: string;
  color?: string;
  /** Outcome of a run that ends in this state; stored as the runtime definition's `finalStates`. */
  final?: FinalOutcome;
}

export interface TimeoutConfig {
  value: number;
  unit: TimeUnit;
}

export type FsmTransitionTrigger = { kind: 'event'; event: string } | { kind: 'auto' };

export interface FsmTransition {
  id: string;
  from: string;
  to: string;
  trigger: FsmTransitionTrigger;
  conditions: string[];
  actions: string[];
  postActions: string[];
  timeout?: TimeoutConfig;
  autoTransitionEnabled?: boolean;
}

export interface BehaviorRef {
  id: string;
  label?: string;
}

export interface CodegenConfig {
  packageName: string;
  className: string;
  factoryMethodName: string;
  domainType: string;
  stateType: string;
  eventType: string;
  initialState: string;
  style: CodegenStyle;
}

export interface FsmEditorDocument {
  execution?: FlowExecution;
  formatVersion: typeof EDITOR_FORMAT_VERSION;
  name: string;
  autoTransitionEnabled: boolean;
  maxImmediateAutoTransitions?: number;
  codegen: CodegenConfig;
  states: FsmState[];
  events: BehaviorRef[];
  transitions: FsmTransition[];
  behaviors: {
    conditions: BehaviorRef[];
    actions: BehaviorRef[];
  };
}

export interface FlowExecution {
  stateListeners: string[];
  completionListeners: string[];
  /** Host-defined iteration limit; absent means the application default. */
  maxIterations?: number;
}

export interface CatalogBehavior {
  id: string;
  kind: 'guard' | 'action' | 'stateListener' | 'completionListener';
  description: string;
  /** Kind of flow the bean works with; absent when the host does not classify beans. */
  flowKind?: string;
}

export type ValidationSeverity = 'error' | 'warning';

export interface ValidationIssue {
  severity: ValidationSeverity;
  path: string;
  message: string;
}
