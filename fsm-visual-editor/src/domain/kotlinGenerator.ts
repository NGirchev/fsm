import type { CodegenStyle, FsmEditorDocument, FsmTransition } from './types';

export function generateKotlinFactory(document: FsmEditorDocument): string {
  const conditionNames = buildIdentifierMap(document.behaviors.conditions.map((behavior) => behavior.id));
  const actionNames = buildIdentifierMap(document.behaviors.actions.map((behavior) => behavior.id));
  const style = codegenStyle(document);
  const lines: string[] = [];

  if (document.codegen.packageName.trim()) {
    lines.push(`package ${document.codegen.packageName}`, '');
  }

  lines.push(
    'import io.github.ngirchev.fsm.Action',
    'import io.github.ngirchev.fsm.Guard',
    'import io.github.ngirchev.fsm.StateContext',
  );

  if (style === 'fluent') {
    lines.push('import io.github.ngirchev.fsm.FsmFactory');
  } else {
    lines.push(
      'import io.github.ngirchev.fsm.To',
      'import io.github.ngirchev.fsm.impl.extended.ExTransition',
      'import io.github.ngirchev.fsm.impl.extended.ExTransitionTable',
    );
  }

  lines.push(
    'import io.github.ngirchev.fsm.Timeout',
    'import io.github.ngirchev.fsm.Transition',
    'import io.github.ngirchev.fsm.impl.extended.ExDomainFsm',
    'import java.util.concurrent.TimeUnit',
    '',
    `object ${document.codegen.className} {`,
  );

  appendStateEnum(lines, document);
  appendEventEnum(lines, document);
  appendDomainDto(lines, document);
  appendBehaviorFields(lines, 'Guard', document.codegen.stateType, conditionNames, 'false');
  appendBehaviorFields(lines, 'Action', document.codegen.stateType, actionNames, '');

  if (style === 'builder') {
    appendBuilderFactory(lines, document, conditionNames, actionNames);
  } else {
    appendFluentFactory(lines, document, conditionNames, actionNames);
  }

  lines.push('}');

  return `${lines.join('\n')}\n`;
}

function appendFluentFactory(
  lines: string[],
  document: FsmEditorDocument,
  conditionNames: Map<string, string>,
  actionNames: Map<string, string>,
): void {
  const groups = groupTransitions(document.transitions);

  lines.push(
    `    fun ${document.codegen.factoryMethodName}(): ExDomainFsm<${document.codegen.domainType}, ${document.codegen.stateType}, ${document.codegen.eventType}> {`,
    `        return FsmFactory.statesWithEvents<${document.codegen.stateType}, ${document.codegen.eventType}>()`,
  );

  if (document.autoTransitionEnabled) {
    lines.push('            .autoTransitionEnabled(true)');
  }

  groups.forEach((group) => {
    if (group.transitions.length === 1) {
      appendSingleTransition(lines, document, group.transitions[0], conditionNames, actionNames);
    } else {
      appendMultipleTransitionGroup(lines, document, group, conditionNames, actionNames);
    }
  });

  lines.push('            .build()', `            .createDomainFsm<${document.codegen.domainType}>()`, '    }');
}

function appendBuilderFactory(
  lines: string[],
  document: FsmEditorDocument,
  conditionNames: Map<string, string>,
  actionNames: Map<string, string>,
): void {
  lines.push(
    `    fun ${document.codegen.factoryMethodName}(): ExDomainFsm<${document.codegen.domainType}, ${document.codegen.stateType}, ${document.codegen.eventType}> {`,
    `        return ExTransitionTable.Builder<${document.codegen.stateType}, ${document.codegen.eventType}>()`,
  );

  if (document.autoTransitionEnabled) {
    lines.push('            .autoTransitionEnabled(true)');
  }

  document.transitions.forEach((transition) => {
    lines.push(
      '            .add(',
      '                ExTransition(',
      `                    from = ${stateLiteral(document, transition.from)},`,
      '                    to = To(',
      `                        state = ${stateLiteral(document, transition.to)},`,
      `                        conditions = ${kotlinList(transition.conditions, conditionNames, toKotlinIdentifier)},`,
      `                        actions = ${kotlinList(transition.actions, actionNames, toKotlinIdentifier)},`,
      `                        postActions = ${kotlinList(transition.postActions, actionNames, toKotlinIdentifier)},`,
      `                        timeout = ${transition.timeout ? `Timeout(${transition.timeout.value}L, TimeUnit.${transition.timeout.unit})` : 'null'},`,
      '                    ),',
      `                    onEvent = ${transition.trigger.kind === 'event' ? eventLiteral(document, transition.trigger.event) : 'null'},`,
      '                ),',
      '            )',
    );
  });

  lines.push('            .build()', `            .createDomainFsm<${document.codegen.domainType}>()`, '    }');
}

interface TransitionGroup {
  from: string;
  trigger: FsmTransition['trigger'];
  transitions: FsmTransition[];
}

function appendStateEnum(lines: string[], document: FsmEditorDocument): void {
  lines.push(`    enum class ${document.codegen.stateType} {`);
  document.states.forEach((state, index) => {
    const suffix = index === document.states.length - 1 ? '' : ',';
    lines.push(`        ${state.label}${suffix}`);
  });
  lines.push('    }', '');
}

function appendEventEnum(lines: string[], document: FsmEditorDocument): void {
  lines.push(`    enum class ${document.codegen.eventType} {`);

  document.events.forEach((event, index) => {
    const suffix = index === document.events.length - 1 ? '' : ',';
    lines.push(`        ${event.id}${suffix}`);
  });

  lines.push('    }', '');
}

function appendDomainDto(lines: string[], document: FsmEditorDocument): void {
  lines.push(
    `    data class ${document.codegen.domainType}(`,
    `        override var state: ${document.codegen.stateType} = ${document.codegen.stateType}.${document.codegen.initialState},`,
    `        override var currentTransition: Transition<${document.codegen.stateType}>? = null`,
    `    ) : StateContext<${document.codegen.stateType}>`,
    '',
  );
}

function appendBehaviorFields(
  lines: string[],
  kind: 'Guard' | 'Action',
  stateType: string,
  names: Map<string, string>,
  guardDefault: string,
): void {
  names.forEach((kotlinName) => {
    if (kind === 'Guard') {
      lines.push(`    private val ${kotlinName}: Guard<StateContext<${stateType}>> = Guard { ${guardDefault} }`);
    } else {
      lines.push(`    private val ${kotlinName}: Action<StateContext<${stateType}>> = Action { }`);
    }
  });

  if (names.size > 0) {
    lines.push('');
  }
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
    ? `            .from(${from}).onEvent(${eventLiteral(document, transition.trigger.event)}).to(${to})`
    : `            .from(${from}).to(${to})`;

  appendTransitionTail(lines, start, transition, conditionNames, actionNames);
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
    ? `            .from(${from}).onEvent(${eventLiteral(document, group.trigger.event)}).toMultiple()`
    : `            .from(${from}).toMultiple()`;

  lines.push(fromChain);

  group.transitions.forEach((transition) => {
    appendTransitionTail(
      lines,
      `            .to(${stateLiteral(document, transition.to)})`,
      transition,
      conditionNames,
      actionNames,
    );
  });

  lines.push('            .endMultiple()');
}

function appendTransitionTail(
  lines: string[],
  start: string,
  transition: FsmTransition,
  conditionNames: Map<string, string>,
  actionNames: Map<string, string>,
): void {
  lines.push(start);

  transition.conditions.forEach((condition) => {
    lines.push(`            .onCondition(${conditionNames.get(condition) ?? toKotlinIdentifier(condition)})`);
  });

  transition.actions.forEach((action) => {
    lines.push(`            .action(${actionNames.get(action) ?? toKotlinIdentifier(action)})`);
  });

  transition.postActions.forEach((postAction) => {
    lines.push(`            .postAction(${actionNames.get(postAction) ?? toKotlinIdentifier(postAction)})`);
  });

  if (transition.timeout) {
    lines.push(`            .timeout(Timeout(${transition.timeout.value}L, TimeUnit.${transition.timeout.unit}))`);
  }

  lines.push('            .end()');
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

function kotlinList(ids: string[], names: Map<string, string>, fallback: (value: string) => string): string {
  if (ids.length === 0) {
    return 'emptyList()';
  }

  return `listOf(${ids.map((id) => names.get(id) ?? fallback(id)).join(', ')})`;
}

function codegenStyle(document: FsmEditorDocument): CodegenStyle {
  return document.codegen.style ?? 'fluent';
}

function buildIdentifierMap(ids: string[]): Map<string, string> {
  const used = new Set<string>();
  const result = new Map<string, string>();

  ids.forEach((id) => {
    const base = toKotlinIdentifier(id);
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

function toKotlinIdentifier(value: string): string {
  const normalized = value
    .trim()
    .replace(/[^A-Za-z0-9_]+/g, ' ')
    .split(' ')
    .filter(Boolean)
    .map((part, index) => (index === 0 ? part : `${part[0]?.toUpperCase() ?? ''}${part.slice(1)}`))
    .join('');
  const fallback = normalized || 'behavior';

  return /^[A-Za-z_]/.test(fallback) ? fallback : `_${fallback}`;
}
