import { describe, expect, it, vi } from 'vitest';
import { backendApi, backendUrl, groupFlowsByKind, initialFlowKey } from './backend';

const page = (href: string) => {
  const url = new URL(href);
  return { href: url.href, search: url.search } as Location;
};

describe('backendUrl', () => {
  it('prefers the query parameter over config and drops trailing slashes', () => {
    expect(backendUrl(page('https://editor.test/?backend=https%3A%2F%2Fapi.test%2Ffsm-admin%2F'),
      { backendUrl: 'https://other.test/fsm-admin' })).toBe('https://api.test/fsm-admin');
  });

  it('resolves a configured relative URL against the editor page', () => {
    expect(backendUrl(page('https://apps.test/editor/'), { backendUrl: '/orders/fsm-admin' }))
      .toBe('https://apps.test/orders/fsm-admin');
  });

  it('ignores missing and non-HTTP URLs', () => {
    expect(backendUrl(page('https://editor.test/'), {})).toBeNull();
    expect(backendUrl(page('https://editor.test/?backend=javascript%3Aalert(1)'), {})).toBeNull();
  });
});

describe('flow list', () => {
  const flows = [
    { flowKey: 'react', title: 'ReAct', kind: 'agent' },
    { flowKey: 'request', title: 'Request', kind: 'request' },
    { flowKey: 'plan', title: 'Plan first', kind: 'agent' },
    { flowKey: 'legacy', title: 'Legacy' },
  ];

  it('opens the flow named by the page URL and falls back to the first flow', () => {
    expect(initialFlowKey(flows, 'plan')).toBe('plan');
    expect(initialFlowKey(flows, 'removed')).toBe('react');
    expect(initialFlowKey(flows, null)).toBe('react');
    expect(initialFlowKey([], 'plan')).toBeUndefined();
  });

  it('groups flows by kind in first-seen order and puts flows without a kind under other', () => {
    expect(groupFlowsByKind(flows).map((group) => [group.kind, group.flows.map((flow) => flow.flowKey)])).toEqual([
      ['agent', ['react', 'plan']], ['request', ['request']], ['other', ['legacy']],
    ]);
    expect(groupFlowsByKind([])).toEqual([]);
  });
});

describe('backendApi', () => {
  const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

  it('creates, copies, renames and deletes flows and reports the refusal message of the backend', async () => {
    const created = { flowKey: 'my agent', title: 'Mine', kind: 'agent', description: null, deletable: true };
    const send = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(json(created, 201))
      .mockResolvedValueOnce(json({ ...created, title: 'Renamed' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(json({ message: 'Built-in flows cannot be deleted' }, 409))
      .mockResolvedValueOnce(json({ message: 'This application does not support creating flows' }, 405));
    const api = backendApi('https://api.test/fsm-admin', send);

    await expect(api.createFlow({ flowKey: 'my agent', title: 'Mine', kind: 'agent', copyOf: 'react' })).resolves.toEqual(created);
    await expect(api.renameFlow('my agent', 'Renamed', 'Text')).resolves.toMatchObject({ title: 'Renamed' });
    await expect(api.deleteFlow('my agent')).resolves.toBeUndefined();
    await expect(api.deleteFlow('react')).rejects.toThrow('Built-in flows cannot be deleted');
    await expect(api.createFlow({ flowKey: 'x', title: 'X', kind: 'agent' })).rejects.toThrow('does not support creating flows');

    expect(send.mock.calls.map(([url, init]) => [init?.method, url, init?.body && JSON.parse(String(init.body))])).toEqual([
      ['POST', 'https://api.test/fsm-admin/api/flows', { flowKey: 'my agent', title: 'Mine', kind: 'agent', copyOf: 'react' }],
      ['PUT', 'https://api.test/fsm-admin/api/flows/my%20agent', { title: 'Renamed', description: 'Text' }],
      ['DELETE', 'https://api.test/fsm-admin/api/flows/my%20agent', undefined],
      ['DELETE', 'https://api.test/fsm-admin/api/flows/react', undefined],
      ['POST', 'https://api.test/fsm-admin/api/flows', { flowKey: 'x', title: 'X', kind: 'agent' }],
    ]);
  });

  it('reads through the supplied request function', async () => {
    const send = vi.fn<typeof fetch>().mockResolvedValue(json([{ flowKey: 'order', title: 'Order' }]));

    await expect(backendApi('https://api.test/fsm-admin', send).flows()).resolves.toEqual([{ flowKey: 'order', title: 'Order' }]);
    expect(send).toHaveBeenCalledTimes(1);
    expect(send.mock.calls[0][0]).toBe('https://api.test/fsm-admin/api/flows');
    expect(send.mock.calls[0][1]?.method).toBe('GET');
  });

  it('sends definitions as JSON in one request and encodes flow keys', async () => {
    const send = vi.fn<typeof fetch>().mockResolvedValue(json({ version: 3, status: 'DRAFT', definition: {} }));
    const definition = { initialState: 'NEW', table: { autoTransitionEnabled: false, maxImmediateAutoTransitions: 10, transitions: {} } };

    await backendApi('https://api.test/fsm-admin', send).save('a b', 3, definition);

    expect(send).toHaveBeenCalledTimes(1);
    const [url, init] = send.mock.calls[0];
    expect(url).toBe('https://api.test/fsm-admin/api/flows/a%20b/versions/3');
    expect(init?.method).toBe('PUT');
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json');
    expect(JSON.parse(String(init?.body))).toEqual(definition);
  });

  it('reports server error messages', async () => {
    const send = vi.fn<typeof fetch>().mockResolvedValue(json({ message: 'Session expired' }, 403));

    await expect(backendApi('https://api.test/fsm-admin', send).publish('order', 1)).rejects.toThrow('Session expired');
  });

  it('accepts empty delete responses and describes errors without a body', async () => {
    const send = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response('down', { status: 503 }));
    const api = backendApi('https://api.test/fsm-admin', send);

    await expect(api.deleteDraft('order', 2)).resolves.toBeUndefined();
    await expect(api.versions('order')).rejects.toThrow('Request failed: HTTP 503');
  });

  it('sends cookies by default so the backend session applies across origins', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json([]));
    try {
      await backendApi('https://api.test/fsm-admin').behaviors('order');
      expect(fetchSpy.mock.calls[0][1]?.credentials).toBe('include');
    } finally {
      fetchSpy.mockRestore();
    }
  });
});
