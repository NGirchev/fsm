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

export interface Point {
  x: number;
  y: number;
}

export interface FsmState {
  id: string;
  label: string;
  position: Point;
  description?: string;
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
}

export interface FsmEditorDocument {
  formatVersion: typeof EDITOR_FORMAT_VERSION;
  name: string;
  autoTransitionEnabled: boolean;
  codegen: CodegenConfig;
  states: FsmState[];
  events: BehaviorRef[];
  transitions: FsmTransition[];
  behaviors: {
    conditions: BehaviorRef[];
    actions: BehaviorRef[];
  };
}

export type ValidationSeverity = 'error' | 'warning';

export interface ValidationIssue {
  severity: ValidationSeverity;
  path: string;
  message: string;
}
