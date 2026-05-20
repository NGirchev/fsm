import { describe, expect, it } from 'vitest';
import { generateKotlinFactory, sampleDocument } from './index';

describe('generateKotlinFactory', () => {
  it('generates self-contained ExDomainFsm factory object', () => {
    const kotlin = generateKotlinFactory(sampleDocument);

    expect(kotlin).toContain('object DocumentFsmFactory');
    expect(kotlin).toContain('enum class DocumentState');
    expect(kotlin).toContain('enum class DocumentEvent');
    expect(kotlin).toContain('TO_READY,');
    expect(kotlin).toContain('data class Document(');
    expect(kotlin).toContain(') : StateContext<DocumentState>');
    expect(kotlin).toContain('private val signRequired: Guard<StateContext<DocumentState>> = Guard { false }');
    expect(kotlin).toContain('private val autoSent: Action<StateContext<DocumentState>> = Action { }');
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
});
