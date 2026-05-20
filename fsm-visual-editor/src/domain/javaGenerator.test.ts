import { describe, expect, it } from 'vitest';
import { generateJavaFactory, sampleDocument } from './index';

describe('generateJavaFactory', () => {
  it('generates ExDomainFsm factory class with global guard and action lambdas', () => {
    const java = generateJavaFactory(sampleDocument);

    expect(java).toContain('public final class DocumentFsmFactory');
    expect(java).toContain('public enum DocumentState');
    expect(java).toContain('public enum DocumentEvent');
    expect(java).toContain('TO_READY, USER_SIGN, FAILED_EVENT, TO_END');
    expect(java).toContain('public static final class Document implements StateContext<DocumentState>');
    expect(java).toContain('private Transition<DocumentState> currentTransition;');
    expect(java).toContain('private static final Guard<StateContext<DocumentState>> signRequired = ctx -> false;');
    expect(java).toContain('private static final Action<StateContext<DocumentState>> autoSent = ctx -> { };');
    expect(java).toContain('public static ExDomainFsm<Document, DocumentState, DocumentEvent> create()');
    expect(java).toContain('FsmFactory.INSTANCE.<DocumentState, DocumentEvent>statesWithEvents()');
  });

  it('uses toMultiple for multiple transitions with same source and event', () => {
    const java = generateJavaFactory(sampleDocument);

    expect(java).toContain('.from(DocumentState.SIGNED).onEvent(DocumentEvent.TO_END).toMultiple()');
    expect(java).toContain('.to(DocumentState.AUTO_SENT)');
    expect(java).toContain('.onCondition(signRequired)');
    expect(java).toContain('.action(autoSent)');
    expect(java).toContain('.to(DocumentState.DONE)');
    expect(java).toContain('.onCondition(signNotRequired)');
    expect(java).toContain('.endMultiple()');
  });

  it('keeps auto transitions eventless', () => {
    const java = generateJavaFactory({
      ...sampleDocument,
      transitions: [{ ...sampleDocument.transitions[0], trigger: { kind: 'auto' } }],
    });

    expect(java).toContain('.from(DocumentState.NEW).to(DocumentState.READY_FOR_SIGN)');
    expect(java).not.toContain('.from(DocumentState.NEW).onEvent(');
  });

  it('does not invent placeholder events for eventless FSMs', () => {
    const java = generateJavaFactory({
      ...sampleDocument,
      events: [],
      transitions: [{ ...sampleDocument.transitions[0], trigger: { kind: 'auto' } }],
    });

    expect(java).toContain('public enum DocumentEvent {\n    }');
    expect(java).not.toContain('UNUSED');
  });

  it('emits auto transition mode, post actions, and timeouts', () => {
    const java = generateJavaFactory({
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

    expect(java).toContain('.autoTransitionEnabled(true)');
    expect(java).toContain('.action(autoSent)');
    expect(java).toContain('.postAction(autoSent)');
    expect(java).toContain('.timeout(new Timeout(15L, TimeUnit.SECONDS))');
  });

  it('escapes string states when the state type is String', () => {
    const java = generateJavaFactory({
      ...sampleDocument,
      codegen: { ...sampleDocument.codegen, stateType: 'String' },
      states: [{ id: 'quoted', label: 'READY "FOR" SIGN', position: { x: 0, y: 0 } }],
      transitions: [{ ...sampleDocument.transitions[0], from: 'quoted', to: 'quoted' }],
    });

    expect(java).toContain('.from("READY \\"FOR\\" SIGN").onEvent(DocumentEvent.TO_READY).to("READY \\"FOR\\" SIGN")');
  });

  it('generates builder add calls when builder style is selected', () => {
    const java = generateJavaFactory({
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

    expect(java).toContain('import io.github.ngirchev.fsm.impl.extended.ExTransitionTable;');
    expect(java).toContain('import java.util.List;');
    expect(java).toContain('return new ExTransitionTable.Builder<DocumentState, DocumentEvent>()');
    expect(java).toContain('new ExTransition<>(');
    expect(java).toContain('new To<>(');
    expect(java).toContain('List.<Guard<StateContext<DocumentState>>>of()');
    expect(java).toContain('List.<Action<StateContext<DocumentState>>>of(autoSent)');
    expect(java).toContain('new Timeout(15L, TimeUnit.SECONDS)');
    expect(java).toContain('DocumentEvent.TO_READY');
    expect(java).not.toContain('FsmFactory.INSTANCE');
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
    const fluent = generateJavaFactory({ ...document, codegen: { ...document.codegen, style: 'fluent' } });
    const builder = generateJavaFactory({ ...document, codegen: { ...document.codegen, style: 'builder' } });

    expect(fluent).toContain('.from(DocumentState.NEW).onEvent(DocumentEvent.TO_READY).to(DocumentState.READY_FOR_SIGN)');
    expect(fluent).toContain('.onCondition(signRequired)');
    expect(fluent).toContain('.action(autoSent)');
    expect(fluent).toContain('.postAction(autoSent)');
    expect(fluent).toContain('.timeout(new Timeout(15L, TimeUnit.SECONDS))');
    expect(fluent).toContain('.from(DocumentState.READY_FOR_SIGN).to(DocumentState.SIGNED)');
    expect(fluent).toContain('.onCondition(signNotRequired)');
    expect(fluent).not.toContain('.from(DocumentState.READY_FOR_SIGN).onEvent(');

    expect(builder).toContain('DocumentState.NEW');
    expect(builder).toContain('DocumentState.READY_FOR_SIGN');
    expect(builder).toContain('DocumentEvent.TO_READY');
    expect(builder).toContain('List.<Guard<StateContext<DocumentState>>>of(signRequired)');
    expect(builder).toContain('List.<Action<StateContext<DocumentState>>>of(autoSent)');
    expect(builder).toContain('new Timeout(15L, TimeUnit.SECONDS)');
    expect(builder).toContain('DocumentState.READY_FOR_SIGN');
    expect(builder).toContain('DocumentState.SIGNED');
    expect(builder).toContain('List.<Guard<StateContext<DocumentState>>>of(signNotRequired)');
    expect(builder).toContain('null');
  });
});
