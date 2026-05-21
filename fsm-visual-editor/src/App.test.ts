import { describe, expect, it } from 'vitest';
import { addAutoTransition, deleteEventAtIndex } from './App';
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
