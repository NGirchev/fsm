import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  createEmptyDocument,
  createProjectId,
  deleteSavedProject,
  listSavedProjects,
  loadSavedProject,
  sampleDocument,
  saveProject,
} from './index';

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('project ids', () => {
  it('creates stable file-safe project ids from document names and dates', () => {
    const document = {
      ...createEmptyDocument(),
      name: 'Order Approval FSM',
    };

    expect(createProjectId(document, new Date('2026-05-17T10:00:00.000Z'))).toBe('order-approval-fsm-20260517-100000');
  });

  it('loads and normalizes projects through the project API', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ ...sampleDocument, formatVersion: 1 }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const project = await loadSavedProject('document-fsm');

    expect(fetchMock).toHaveBeenCalledWith('/api/projects/document-fsm');
    expect(project.formatVersion).toBe(2);
  });

  it('saves projects with an updated timestamp envelope', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-05-17T12:00:00.000Z'));
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal('fetch', fetchMock);

    await saveProject('document-fsm', sampleDocument);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/document-fsm',
      expect.objectContaining({
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: expect.any(String),
      }),
    );
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      ...sampleDocument,
      updatedAt: '2026-05-17T12:00:00.000Z',
    });

    vi.useRealTimers();
  });

  it('lists and deletes saved projects through the project API', async () => {
    const projects = [{ id: 'document-fsm', name: 'Document FSM', updatedAt: '2026-05-17T12:00:00.000Z' }];
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true, json: async () => projects })
      .mockResolvedValueOnce({ ok: true });
    vi.stubGlobal('fetch', fetchMock);

    await expect(listSavedProjects()).resolves.toEqual(projects);
    await expect(deleteSavedProject('document-fsm')).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/projects');
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/projects/document-fsm', { method: 'DELETE' });
  });

  it('throws when project API responses fail or contain invalid documents', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }));

    await expect(loadSavedProject('broken')).rejects.toThrow('Failed to load project: HTTP 500');

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ formatVersion: 2 }) }));

    await expect(loadSavedProject('broken')).rejects.toThrow('Saved project is not an editor document.');
  });
});
