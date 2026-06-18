import { describe, expect, it } from 'vitest';
import { generateKotlinFactory, sampleDocument } from './index';

describe('generateKotlinFactory', () => {
  it('generates self-contained ExDomainFsm factory object with named guards and actions', () => {
    const kotlin = generateKotlinFactory(sampleDocument);

    expect(kotlin).toContain('object DocumentFsmFactory');
    expect(kotlin).toContain('enum class DocumentState');
    expect(kotlin).toContain('enum class DocumentEvent');
    expect(kotlin).toContain('TO_READY,');
    expect(kotlin).toContain('data class Document(');
    expect(kotlin).toContain(') : StateContext<DocumentState>');
    expect(kotlin).toContain('import io.github.ngirchev.fsm.NamedAction');
    expect(kotlin).toContain('import io.github.ngirchev.fsm.NamedGuard');
    expect(kotlin).toContain(
      'private val signRequired: Guard<StateContext<DocumentState>> = NamedGuard("signRequired") { false }',
    );
    expect(kotlin).toContain(
      'private val autoSent: Action<StateContext<DocumentState>> = NamedAction("autoSent") { }',
    );
    expect(kotlin).toContain('fun create(): ExDomainFsm<Document, DocumentState, DocumentEvent>');
    expect(kotlin).toContain('FsmFactory.statesWithEvents<DocumentState, DocumentEvent>()');
  });

  it('uses toMultiple for multiple transitions with same source and event', () => {
    const kotlin = generateKotlinFactory(sampleDocument);

    expect(kotlin).toContain('.from(DocumentState.SIGNED).onEvent(DocumentEvent.TO_END).toMultiple()');
    expect(kotlin).toContain('.to(DocumentState.AUTO_SENT)');
    expect(kotlin).toContain('.onCondition(signRequired)');
    expect(kotlin).toContain('.action(autoSent)');
    expect(kotlin).toContain('.to(DocumentState.DONE)');
    expect(kotlin).toContain('.onCondition(signNotRequired)');
    expect(kotlin).toContain('.endMultiple()');
  });

  it('keeps auto transitions eventless', () => {
    const kotlin = generateKotlinFactory({
      ...sampleDocument,
      transitions: [{ ...sampleDocument.transitions[0], trigger: { kind: 'auto' } }],
    });

    expect(kotlin).toContain('.from(DocumentState.NEW).to(DocumentState.READY_FOR_SIGN)');
    expect(kotlin).not.toContain('.from(DocumentState.NEW).onEvent(');
  });

  it('does not invent placeholder events for eventless FSMs', () => {
    const kotlin = generateKotlinFactory({
      ...sampleDocument,
      events: [],
      transitions: [{ ...sampleDocument.transitions[0], trigger: { kind: 'auto' } }],
    });

    expect(kotlin).toContain('enum class DocumentEvent {\n    }');
    expect(kotlin).not.toContain('UNUSED');
  });

  it('emits auto transition mode, post actions, and timeouts', () => {
    const kotlin = generateKotlinFactory({
      ...sampleDocument,
      autoTransitionEnabled: true,
      transitions: [
        {
          ...sampleDocument.transitions[0],
          actions: ['autoSent'],
          postActions: ['autoSent'],
          timeout: { value: 15, unit: 'SECONDS' },
        },
      ],
    });

    expect(kotlin).toContain('.autoTransitionEnabled(true)');
    expect(kotlin).toContain('.action(autoSent)');
    expect(kotlin).toContain('.postAction(autoSent)');
    expect(kotlin).toContain('.timeout(Timeout(15L, TimeUnit.SECONDS))');
  });

  it('escapes string states when the state type is String', () => {
    const kotlin = generateKotlinFactory({
      ...sampleDocument,
      codegen: { ...sampleDocument.codegen, stateType: 'String' },
      states: [{ id: 'quoted', label: 'READY "FOR" SIGN', position: { x: 0, y: 0 } }],
      transitions: [{ ...sampleDocument.transitions[0], from: 'quoted', to: 'quoted' }],
    });

    expect(kotlin).toContain('.from("READY \\"FOR\\" SIGN").onEvent(DocumentEvent.TO_READY).to("READY \\"FOR\\" SIGN")');
  });

  it('generates builder add calls when builder style is selected', () => {
    const kotlin = generateKotlinFactory({
      ...sampleDocument,
      codegen: { ...sampleDocument.codegen, style: 'builder' },
      transitions: [
        {
          ...sampleDocument.transitions[0],
          actions: ['autoSent'],
          postActions: ['autoSent'],
          timeout: { value: 15, unit: 'SECONDS' },
        },
      ],
    });

    expect(kotlin).toContain('import io.github.ngirchev.fsm.impl.extended.ExTransitionTable');
    expect(kotlin).toContain('return ExTransitionTable.Builder<DocumentState, DocumentEvent>()');
    expect(kotlin).toContain('ExTransition(');
    expect(kotlin).toContain('state = DocumentState.READY_FOR_SIGN');
    expect(kotlin).toContain('conditions = emptyList()');
    expect(kotlin).toContain('actions = listOf(autoSent)');
    expect(kotlin).toContain('postActions = listOf(autoSent)');
    expect(kotlin).toContain('timeout = Timeout(15L, TimeUnit.SECONDS)');
    expect(kotlin).toContain('onEvent = DocumentEvent.TO_READY');
    expect(kotlin).not.toContain('FsmFactory.statesWithEvents');
  });

  it('preserves transition semantics in fluent and builder styles', () => {
    const document = {
      ...sampleDocument,
      transitions: [
        {
          ...sampleDocument.transitions[0],
          conditions: ['signRequired'],
          actions: ['autoSent'],
          postActions: ['autoSent'],
          timeout: { value: 15, unit: 'SECONDS' as const },
        },
        {
          ...sampleDocument.transitions[1],
          trigger: { kind: 'auto' as const },
          conditions: ['signNotRequired'],
          actions: [],
          postActions: ['autoSent'],
        },
      ],
    };
    const fluent = generateKotlinFactory({ ...document, codegen: { ...document.codegen, style: 'fluent' } });
    const builder = generateKotlinFactory({ ...document, codegen: { ...document.codegen, style: 'builder' } });

    expect(fluent).toContain('.from(DocumentState.NEW).onEvent(DocumentEvent.TO_READY).to(DocumentState.READY_FOR_SIGN)');
    expect(fluent).toContain('.onCondition(signRequired)');
    expect(fluent).toContain('.action(autoSent)');
    expect(fluent).toContain('.postAction(autoSent)');
    expect(fluent).toContain('.timeout(Timeout(15L, TimeUnit.SECONDS))');
    expect(fluent).toContain('.from(DocumentState.READY_FOR_SIGN).to(DocumentState.SIGNED)');
    expect(fluent).toContain('.onCondition(signNotRequired)');
    expect(fluent).not.toContain('.from(DocumentState.READY_FOR_SIGN).onEvent(');

    expect(builder).toContain('from = DocumentState.NEW');
    expect(builder).toContain('state = DocumentState.READY_FOR_SIGN');
    expect(builder).toContain('onEvent = DocumentEvent.TO_READY');
    expect(builder).toContain('conditions = listOf(signRequired)');
    expect(builder).toContain('actions = listOf(autoSent)');
    expect(builder).toContain('postActions = listOf(autoSent)');
    expect(builder).toContain('timeout = Timeout(15L, TimeUnit.SECONDS)');
    expect(builder).toContain('from = DocumentState.READY_FOR_SIGN');
    expect(builder).toContain('state = DocumentState.SIGNED');
    expect(builder).toContain('conditions = listOf(signNotRequired)');
    expect(builder).toContain('onEvent = null');
  });

  it('orders transitions from initial state and leaves unreachable transitions at end', () => {
    const document = {
      ...sampleDocument,
      states: [
        { id: 'state-a', label: 'A', position: { x: 0, y: 0 } },
        { id: 'state-b', label: 'B', position: { x: 10, y: 0 } },
        { id: 'state-c', label: 'C', position: { x: 20, y: 0 } },
        { id: 'state-x', label: 'X', position: { x: 30, y: 0 } },
        { id: 'state-y', label: 'Y', position: { x: 40, y: 0 } },
      ],
      events: [{ id: 'TO_B' }, { id: 'TO_C' }, { id: 'TO_Y' }],
      transitions: [
        {
          id: 'out-of-order-reachable-b-c',
          from: 'state-b',
          to: 'state-c',
          trigger: { kind: 'event' as const, event: 'TO_C' },
          conditions: [],
          actions: [],
          postActions: [],
        },
        {
          id: 'out-of-order-unreachable-x-y',
          from: 'state-x',
          to: 'state-y',
          trigger: { kind: 'event' as const, event: 'TO_Y' },
          conditions: [],
          actions: [],
          postActions: [],
        },
        {
          id: 'out-of-order-reachable-a-b',
          from: 'state-a',
          to: 'state-b',
          trigger: { kind: 'event' as const, event: 'TO_B' },
          conditions: [],
          actions: [],
          postActions: [],
        },
      ],
      codegen: { ...sampleDocument.codegen, initialState: 'A' },
    };

    const fluent = generateKotlinFactory({ ...document, codegen: { ...document.codegen, style: 'fluent' } });
    const builder = generateKotlinFactory({ ...document, codegen: { ...document.codegen, style: 'builder' } });

    const fluentStart = fluent.indexOf('FsmFactory.statesWithEvents');
    const builderStart = builder.indexOf('ExTransitionTable.Builder');

    const fluentAtoB = fluent.indexOf('.from(DocumentState.A).onEvent(DocumentEvent.TO_B).to(DocumentState.B)', fluentStart);
    const fluentBtoC = fluent.indexOf('.from(DocumentState.B).onEvent(DocumentEvent.TO_C).to(DocumentState.C)', fluentStart);
    const fluentXtoY = fluent.indexOf('.from(DocumentState.X).onEvent(DocumentEvent.TO_Y).to(DocumentState.Y)', fluentStart);

    expect(fluentAtoB).toBeGreaterThan(-1);
    expect(fluentBtoC).toBeGreaterThan(-1);
    expect(fluentXtoY).toBeGreaterThan(-1);
    expect(fluentAtoB).toBeLessThan(fluentBtoC);
    expect(fluentBtoC).toBeLessThan(fluentXtoY);

    const builderAtoB = builder.indexOf('onEvent = DocumentEvent.TO_B', builderStart);
    const builderBtoC = builder.indexOf('onEvent = DocumentEvent.TO_C', builderStart);
    const builderXtoY = builder.indexOf('onEvent = DocumentEvent.TO_Y', builderStart);

    expect(builderAtoB).toBeGreaterThan(-1);
    expect(builderBtoC).toBeGreaterThan(-1);
    expect(builderXtoY).toBeGreaterThan(-1);
    expect(builderAtoB).toBeLessThan(builderBtoC);
    expect(builderBtoC).toBeLessThan(builderXtoY);
  });
});
