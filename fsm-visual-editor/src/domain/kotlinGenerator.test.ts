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
});
