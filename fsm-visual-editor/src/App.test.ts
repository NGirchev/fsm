import { describe, expect, it } from 'vitest';
import { addAutoTransition, deleteBehaviorAtIndex, deleteEventAtIndex, transitionLabel } from './App';
import { sampleDocument } from './domain';

describe('deleteEventAtIndex', () => {
  it('removes the event and transitions that use it', () => {
    const deletedEventId = sampleDocument.events[0].id;
    const deletedTransitionIds = sampleDocument.transitions
      .filter((transition) => transition.trigger.kind === 'event' && transition.trigger.event === deletedEventId)
      .map((transition) => transition.id);

    const updated = deleteEventAtIndex(sampleDocument, 0);

    expect(updated.events.map((event) => event.id)).not.toContain(deletedEventId);
    expect(updated.transitions.map((transition) => transition.id)).not.toEqual(expect.arrayContaining(deletedTransitionIds));
    expect(updated.transitions.length).toBe(sampleDocument.transitions.length - deletedTransitionIds.length);
  });

  it('leaves the document unchanged for an unknown index', () => {
    expect(deleteEventAtIndex(sampleDocument, -1)).toBe(sampleDocument);
  });
});

describe('deleteBehaviorAtIndex', () => {
  it('removes a guard and transition guard references', () => {
    const deletedGuardId = sampleDocument.behaviors.conditions[0].id;

    const updated = deleteBehaviorAtIndex(sampleDocument, 'conditions', 0);

    expect(updated.behaviors.conditions.map((condition) => condition.id)).not.toContain(deletedGuardId);
    expect(updated.transitions.flatMap((transition) => transition.conditions)).not.toContain(deletedGuardId);
  });

  it('removes an action and transition action references', () => {
    const deletedActionId = sampleDocument.behaviors.actions[0].id;
    const document = {
      ...sampleDocument,
      transitions: sampleDocument.transitions.map((transition) =>
        transition.id === 'signed-auto'
          ? { ...transition, postActions: [...transition.postActions, deletedActionId] }
          : transition,
      ),
    };

    const updated = deleteBehaviorAtIndex(document, 'actions', 0);

    expect(updated.behaviors.actions.map((action) => action.id)).not.toContain(deletedActionId);
    expect(updated.transitions.flatMap((transition) => transition.actions)).not.toContain(deletedActionId);
    expect(updated.transitions.flatMap((transition) => transition.postActions)).not.toContain(deletedActionId);
  });

  it('leaves the document unchanged for an unknown behavior index', () => {
    expect(deleteBehaviorAtIndex(sampleDocument, 'conditions', -1)).toBe(sampleDocument);
    expect(deleteBehaviorAtIndex(sampleDocument, 'actions', -1)).toBe(sampleDocument);
  });
});

describe('addAutoTransition', () => {
  it('adds an auto transition without creating an event', () => {
    const updated = addAutoTransition(sampleDocument, 'new', 'signed');
    const transition = updated.transitions[updated.transitions.length - 1];

    expect(updated.events).toBe(sampleDocument.events);
    expect(transition).toEqual(
      expect.objectContaining({
        from: 'new',
        to: 'signed',
        trigger: { kind: 'auto' },
        conditions: [],
        actions: [],
        postActions: [],
      }),
    );
  });

  it('does not add the same auto transition twice', () => {
    const updated = addAutoTransition(sampleDocument, 'new', 'signed');
    const duplicateAttempt = addAutoTransition(updated, 'new', 'signed');

    expect(duplicateAttempt).toBe(updated);
    expect(duplicateAttempt.transitions).toHaveLength(updated.transitions.length);
  });
});

describe('transitionLabel', () => {
  it('shows actions and post actions next to the event and guards', () => {
    expect(
      transitionLabel({
        ...sampleDocument.transitions[0],
        trigger: { kind: 'event', event: 'TO_READY' },
        conditions: ['signRequired'],
        actions: ['autoSent'],
        postActions: ['notifyUser'],
      }),
    ).toBe('TO_READY [signRequired] / autoSent post: notifyUser');
  });

  it('keeps timeout visible with action details', () => {
    expect(
      transitionLabel({
        ...sampleDocument.transitions[0],
        trigger: { kind: 'auto' },
        actions: ['autoSent'],
        postActions: ['notifyUser'],
        timeout: { value: 5, unit: 'SECONDS' },
      }),
    ).toBe('auto / autoSent post: notifyUser 5s');
  });
});
