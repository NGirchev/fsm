import type { FsmEditorDocument, FsmTransition } from './types';

export function generateJavaFactory(document: FsmEditorDocument): string {
  const conditionNames = buildIdentifierMap(document.behaviors.conditions.map((behavior) => behavior.id));
  const actionNames = buildIdentifierMap(document.behaviors.actions.map((behavior) => behavior.id));
  const groups = groupTransitions(document.transitions);
  const lines: string[] = [];

  if (document.codegen.packageName.trim()) {
    lines.push(`package ${document.codegen.packageName};`, '');
  }

  lines.push(
    'import io.github.ngirchev.fsm.Action;',
    'import io.github.ngirchev.fsm.FsmFactory;',
    'import io.github.ngirchev.fsm.Guard;',
    'import io.github.ngirchev.fsm.StateContext;',
    'import io.github.ngirchev.fsm.Timeout;',
    'import io.github.ngirchev.fsm.Transition;',
    'import io.github.ngirchev.fsm.impl.extended.ExDomainFsm;',
    'import java.util.concurrent.TimeUnit;',
    '',
    `public final class ${document.codegen.className} {`,
    `    private ${document.codegen.className}() {`,
    '    }',
    '',
  );

  appendStateEnum(lines, document);
  appendEventEnum(lines, document);
  appendDomainDto(lines, document);
  appendBehaviorFields(lines, 'Guard', document.codegen.stateType, conditionNames, 'false');
  appendBehaviorFields(lines, 'Action', document.codegen.stateType, actionNames, '');

  lines.push(
    `    public static ExDomainFsm<${document.codegen.domainType}, ${document.codegen.stateType}, ${document.codegen.eventType}> ${document.codegen.factoryMethodName}() {`,
    `        return FsmFactory.INSTANCE.<${document.codegen.stateType}, ${document.codegen.eventType}>statesWithEvents()`,
  );

  if (document.autoTransitionEnabled) {
    lines.push('                .autoTransitionEnabled(true)');
  }

  groups.forEach((group) => {
    if (group.transitions.length === 1) {
      appendSingleTransition(lines, document, group.transitions[0], conditionNames, actionNames);
    } else {
      appendMultipleTransitionGroup(lines, document, group, conditionNames, actionNames);
    }
  });

  lines.push('                .build()', '                .createDomainFsm();', '    }', '}');

  return `${lines.join('\n')}\n`;
}

interface TransitionGroup {
  from: string;
  trigger: FsmTransition['trigger'];
  transitions: FsmTransition[];
}

function appendBehaviorFields(
  lines: string[],
  kind: 'Guard' | 'Action',
  stateType: string,
  names: Map<string, string>,
  guardDefault: string,
): void {
  names.forEach((javaName) => {
    if (kind === 'Guard') {
      lines.push(`    private static final Guard<StateContext<${stateType}>> ${javaName} = ctx -> ${guardDefault};`);
    } else {
      lines.push(`    private static final Action<StateContext<${stateType}>> ${javaName} = ctx -> { };`);
    }
  });

  if (names.size > 0) {
    lines.push('');
  }
}

function appendStateEnum(lines: string[], document: FsmEditorDocument): void {
  const constants = document.states.map((state) => state.label).join(', ');

  lines.push(`    public enum ${document.codegen.stateType} {`, `        ${constants}`, '    }', '');
}

function appendEventEnum(lines: string[], document: FsmEditorDocument): void {
  lines.push(`    public enum ${document.codegen.eventType} {`);

  if (document.events.length > 0) {
    lines.push(`        ${document.events.map((event) => event.id).join(', ')}`);
  }

  lines.push('    }', '');
}

function appendDomainDto(lines: string[], document: FsmEditorDocument): void {
  lines.push(
    `    public static final class ${document.codegen.domainType} implements StateContext<${document.codegen.stateType}> {`,
    `        private ${document.codegen.stateType} state;`,
    `        private Transition<${document.codegen.stateType}> currentTransition;`,
    '',
    `        public ${document.codegen.domainType}(${document.codegen.stateType} state) {`,
    '            this.state = state;',
    '        }',
    '',
    '        @Override',
    `        public ${document.codegen.stateType} getState() {`,
    '            return state;',
    '        }',
    '',
    '        @Override',
    `        public void setState(${document.codegen.stateType} state) {`,
    '            this.state = state;',
    '        }',
    '',
    '        @Override',
    `        public Transition<${document.codegen.stateType}> getCurrentTransition() {`,
    '            return currentTransition;',
    '        }',
    '',
    '        @Override',
    `        public void setCurrentTransition(Transition<${document.codegen.stateType}> currentTransition) {`,
    '            this.currentTransition = currentTransition;',
    '        }',
    '    }',
    '',
  );
}

function appendSingleTransition(
  lines: string[],
  document: FsmEditorDocument,
  transition: FsmTransition,
  conditionNames: Map<string, string>,
  actionNames: Map<string, string>,
): void {
  const from = stateLiteral(document, transition.from);
  const to = stateLiteral(document, transition.to);
  const start = transition.trigger.kind === 'event'
    ? `                .from(${from}).onEvent(${eventLiteral(document, transition.trigger.event)}).to(${to})`
    : `                .from(${from}).to(${to})`;

  appendTransitionTail(lines, start, document, transition, conditionNames, actionNames, true);
}

function appendMultipleTransitionGroup(
  lines: string[],
  document: FsmEditorDocument,
  group: TransitionGroup,
  conditionNames: Map<string, string>,
  actionNames: Map<string, string>,
): void {
  const from = stateLiteral(document, group.from);
  const fromChain = group.trigger.kind === 'event'
    ? `                .from(${from}).onEvent(${eventLiteral(document, group.trigger.event)}).toMultiple()`
    : `                .from(${from}).toMultiple()`;

  lines.push(fromChain);

  group.transitions.forEach((transition) => {
    appendTransitionTail(
      lines,
      `                .to(${stateLiteral(document, transition.to)})`,
      document,
      transition,
      conditionNames,
      actionNames,
      false,
    );
  });

  lines.push('                .endMultiple()');
}

function appendTransitionTail(
  lines: string[],
  start: string,
  document: FsmEditorDocument,
  transition: FsmTransition,
  conditionNames: Map<string, string>,
  actionNames: Map<string, string>,
  singleTransition: boolean,
): void {
  lines.push(start);

  transition.conditions.forEach((condition) => {
    lines.push(`                .onCondition(${conditionNames.get(condition) ?? toJavaIdentifier(condition)})`);
  });

  transition.actions.forEach((action) => {
    lines.push(`                .action(${actionNames.get(action) ?? toJavaIdentifier(action)})`);
  });

  transition.postActions.forEach((postAction) => {
    lines.push(`                .postAction(${actionNames.get(postAction) ?? toJavaIdentifier(postAction)})`);
  });

  if (transition.timeout) {
    lines.push(`                .timeout(new Timeout(${transition.timeout.value}L, TimeUnit.${transition.timeout.unit}))`);
  }

  lines.push(singleTransition ? '                .end()' : '                .end()');
}

function groupTransitions(transitions: FsmTransition[]): TransitionGroup[] {
  const groups = new Map<string, TransitionGroup>();

  transitions.forEach((transition) => {
    const key = `${transition.from}\u0000${transitionKey(transition)}`;
    const existing = groups.get(key);

    if (existing) {
      existing.transitions.push(transition);
      return;
    }

    groups.set(key, {
      from: transition.from,
      trigger: transition.trigger,
      transitions: [transition],
    });
  });

  return [...groups.values()];
}

function stateLiteral(document: FsmEditorDocument, stateId: string): string {
  const state = document.states.find((candidate) => candidate.id === stateId);
  const label = state?.label ?? stateId;

  if (document.codegen.stateType === 'String') {
    return stringLiteral(label);
  }

  return `${document.codegen.stateType}.${label}`;
}

function eventLiteral(document: FsmEditorDocument, event: string): string {
  return `${document.codegen.eventType}.${event}`;
}

function transitionKey(transition: FsmTransition): string {
  return transition.trigger.kind === 'event' ? `event:${transition.trigger.event}` : 'auto';
}

function stringLiteral(value: string): string {
  return JSON.stringify(value);
}

function buildIdentifierMap(ids: string[]): Map<string, string> {
  const used = new Set<string>();
  const result = new Map<string, string>();

  ids.forEach((id) => {
    const base = toJavaIdentifier(id);
    let candidate = base;
    let suffix = 2;

    while (used.has(candidate)) {
      candidate = `${base}${suffix}`;
      suffix += 1;
    }

    used.add(candidate);
    result.set(id, candidate);
  });

  return result;
}

function toJavaIdentifier(value: string): string {
  const normalized = value
    .trim()
    .replace(/[^A-Za-z0-9_$]+/g, ' ')
    .split(' ')
    .filter(Boolean)
    .map((part, index) => (index === 0 ? part : `${part[0]?.toUpperCase() ?? ''}${part.slice(1)}`))
    .join('');
  const fallback = normalized || 'behavior';

  return /^[A-Za-z_$]/.test(fallback) ? fallback : `_${fallback}`;
}
