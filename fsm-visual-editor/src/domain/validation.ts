import { CODEGEN_STYLES, TIME_UNITS, type FsmEditorDocument, type ValidationIssue } from './types';

const javaIdentifierPattern = /^[A-Za-z_$][A-Za-z0-9_$]*$/;
const qualifiedJavaIdentifierPattern = /^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$/;

export function validateEditorDocument(document: FsmEditorDocument): ValidationIssue[] {
  const issues: ValidationIssue[] = [];

  requireText(document.name, 'name', 'FSM name is required.', issues);
  requireQualifiedIdentifier(document.codegen.packageName, 'codegen.packageName', 'Package name must be a valid Java package.', issues);
  requireIdentifier(document.codegen.className, 'codegen.className', 'Class name must be a valid Java identifier.', issues);
  requireIdentifier(
    document.codegen.factoryMethodName,
    'codegen.factoryMethodName',
    'Factory method name must be a valid Java identifier.',
    issues,
  );
  requireIdentifier(document.codegen.domainType, 'codegen.domainType', 'Domain type must be a valid Java identifier.', issues);
  requireIdentifier(document.codegen.stateType, 'codegen.stateType', 'State type must be a valid Java identifier.', issues);
  requireIdentifier(document.codegen.eventType, 'codegen.eventType', 'Event type must be a valid Java identifier.', issues);
  requireText(document.codegen.initialState, 'codegen.initialState', 'Initial state is required.', issues);

  if (!CODEGEN_STYLES.includes(document.codegen.style)) {
    issues.push({ severity: 'error', path: 'codegen.style', message: 'Code generation style is not supported.' });
  }

  const stateIds = new Set<string>();
  const stateLabels = new Set<string>();

  document.states.forEach((state, index) => {
    const path = `states[${index}]`;
    requireText(state.id, `${path}.id`, 'State id is required.', issues);
    requireText(state.label, `${path}.label`, 'State label is required.', issues);
    requireIdentifier(state.label, `${path}.label`, 'State label must be a valid Java enum constant.', issues);

    if (stateIds.has(state.id)) {
      issues.push({ severity: 'error', path: `${path}.id`, message: `Duplicate state id "${state.id}".` });
    }
    stateIds.add(state.id);

    if (stateLabels.has(state.label)) {
      issues.push({ severity: 'warning', path: `${path}.label`, message: `Duplicate state label "${state.label}".` });
    }
    stateLabels.add(state.label);
  });

  if (document.states.length === 0) {
    issues.push({ severity: 'error', path: 'states', message: 'At least one state is required.' });
  }

  if (document.codegen.initialState && !stateLabels.has(document.codegen.initialState)) {
    issues.push({
      severity: 'error',
      path: 'codegen.initialState',
      message: `Initial state "${document.codegen.initialState}" must match a state label.`,
    });
  }

  const conditionIds = validateBehaviorIds(document.behaviors.conditions, 'behaviors.conditions', issues);
  const actionIds = validateBehaviorIds(document.behaviors.actions, 'behaviors.actions', issues);
  const eventIds = validateBehaviorIds(document.events, 'events', issues, 'Event id must be a Java enum constant.');
  const transitionIds = new Set<string>();
  const hasEventTransition = document.transitions.some((transition) => transition.trigger.kind === 'event');

  if (document.transitions.length > 0 && !hasEventTransition) {
    issues.push({
      severity: 'warning',
      path: 'events',
      message: 'No event transitions are configured; handle(domain, event) cannot trigger this FSM.',
    });
  }

  document.transitions.forEach((transition, index) => {
    const path = `transitions[${index}]`;

    requireText(transition.id, `${path}.id`, 'Transition id is required.', issues);
    if (transitionIds.has(transition.id)) {
      issues.push({ severity: 'error', path: `${path}.id`, message: `Duplicate transition id "${transition.id}".` });
    }
    transitionIds.add(transition.id);

    if (!stateIds.has(transition.from)) {
      issues.push({ severity: 'error', path: `${path}.from`, message: `Unknown source state "${transition.from}".` });
    }

    if (!stateIds.has(transition.to)) {
      issues.push({ severity: 'error', path: `${path}.to`, message: `Unknown target state "${transition.to}".` });
    }

    if (transition.trigger.kind === 'event') {
      if (!eventIds.has(transition.trigger.event)) {
        issues.push({ severity: 'error', path: `${path}.trigger.event`, message: `Unknown event "${transition.trigger.event}".` });
      }
    } else if (!document.autoTransitionEnabled) {
      issues.push({
        severity: 'warning',
        path: `${path}.trigger`,
        message: 'Auto transition will not run after event handling while auto transitions are disabled.',
      });
    }

    transition.conditions.forEach((conditionId) => {
      if (!conditionIds.has(conditionId)) {
        issues.push({ severity: 'error', path: `${path}.conditions`, message: `Unknown guard "${conditionId}".` });
      }
    });

    [...transition.actions, ...transition.postActions].forEach((actionId) => {
      if (!actionIds.has(actionId)) {
        issues.push({ severity: 'error', path: `${path}.actions`, message: `Unknown action "${actionId}".` });
      }
    });

    if (transition.timeout) {
      if (!Number.isInteger(transition.timeout.value) || transition.timeout.value <= 0) {
        issues.push({ severity: 'error', path: `${path}.timeout.value`, message: 'Timeout value must be a positive integer.' });
      }

      if (!TIME_UNITS.includes(transition.timeout.unit)) {
        issues.push({ severity: 'error', path: `${path}.timeout.unit`, message: `Unsupported timeout unit "${transition.timeout.unit}".` });
      }
    }
  });

  return issues;
}

function validateBehaviorIds(
  behaviors: { id: string; label?: string }[],
  path: string,
  issues: ValidationIssue[],
  identifierMessage = 'Behavior id must be a Java identifier.',
): Set<string> {
  const ids = new Set<string>();

  behaviors.forEach((behavior, index) => {
    const itemPath = `${path}[${index}]`;
    requireIdentifier(behavior.id, `${itemPath}.id`, identifierMessage, issues);

    if (ids.has(behavior.id)) {
      issues.push({ severity: 'error', path: `${itemPath}.id`, message: `Duplicate behavior id "${behavior.id}".` });
    }
    ids.add(behavior.id);
  });

  return ids;
}

function requireText(value: string, path: string, message: string, issues: ValidationIssue[]): void {
  if (!value.trim()) {
    issues.push({ severity: 'error', path, message });
  }
}

function requireIdentifier(value: string, path: string, message: string, issues: ValidationIssue[]): void {
  requireText(value, path, message, issues);

  if (value.trim() && !javaIdentifierPattern.test(value)) {
    issues.push({ severity: 'error', path, message });
  }
}

function requireQualifiedIdentifier(value: string, path: string, message: string, issues: ValidationIssue[]): void {
  requireText(value, path, message, issues);

  if (value.trim() && !qualifiedJavaIdentifierPattern.test(value)) {
    issues.push({ severity: 'error', path, message });
  }
}
