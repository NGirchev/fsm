import { useEffect, useState } from 'react';
import { App } from './App';
import { fromFlowDefinition, toFlowDefinition, type FlowVersion } from './domain/flowDefinition';
import type { FsmEditorDocument } from './domain/types';

// Relative to /[context-path]/fsm-editor/, including when the app is deployed under a prefix.
const versionsUrl = new URL('../api/flows/order/versions', window.location.href).pathname;

async function request<T>(url: string, method = 'GET', body?: unknown): Promise<T> {
  const response = await fetch(url, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(error?.message ?? `Request failed: HTTP ${response.status}`);
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}

export function OrderEditor() {
  const [versions, setVersions] = useState<FlowVersion[]>([]);
  const [selected, setSelected] = useState<FlowVersion>();
  const [document, setDocument] = useState<FsmEditorDocument>();
  const [initialDocument, setInitialDocument] = useState<FsmEditorDocument>();
  const [savedDocument, setSavedDocument] = useState('');
  const [generation, setGeneration] = useState(0);
  const [busy, setBusy] = useState(true);
  const [message, setMessage] = useState('Loading order flow…');
  const dirty = document !== undefined && JSON.stringify(document) !== savedDocument;

  function open(version: FlowVersion) {
    const next = fromFlowDefinition(version.definition);
    setSelected(version);
    setDocument(next);
    setInitialDocument(next);
    setSavedDocument(JSON.stringify(next));
    setGeneration((value) => value + 1);
  }

  async function reload() {
    setBusy(true);
    try {
      const items = await request<FlowVersion[]>(versionsUrl);
      setVersions(items);
      const active = items.find((item) => item.status === 'ACTIVE') ?? items[0];
      if (!active) throw new Error('No order flow exists. Initialize the example database first.');
      open(active);
      setMessage('Order flow loaded. Create a draft to edit.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Failed to load order flow');
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => { void reload(); }, []);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (dirty) event.preventDefault(); };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  async function mutate(action: 'create' | 'save' | 'publish') {
    if (!selected || !document) return;
    setBusy(true);
    try {
      const path = action === 'create' ? versionsUrl : `${versionsUrl}/${selected.version}${action === 'publish' ? '/publish' : ''}`;
      const result = await request<FlowVersion>(path, action === 'save' ? 'PUT' : 'POST',
        action === 'publish' ? undefined : toFlowDefinition(document));
      // Reflect the successful mutation before refreshing the list, even if that later read fails.
      setVersions((items) => [result, ...items.filter((item) => item.version !== result.version)]
        .map((item) => action === 'publish' && item.version !== result.version && item.status === 'ACTIVE'
          ? { ...item, status: 'ARCHIVED' as const } : item)
        .sort((left, right) => right.version - left.version));
      if (action === 'save') {
        setSelected(result);
        setSavedDocument(JSON.stringify(document));
      } else {
        open(result);
      }
      setMessage(action === 'publish' ? 'Published. New orders use this version.' : action === 'save' ? 'Draft and layout saved.' : 'Draft created. You can edit the graph.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Request failed');
    } finally {
      setBusy(false);
    }
  }

  async function deleteDraft() {
    if (!selected || selected.status !== 'DRAFT' || !window.confirm(
      `Delete draft v${selected.version}?${dirty ? ' Unsaved changes will also be lost.' : ''}`,
    )) return;
    setBusy(true);
    try {
      await request<void>(`${versionsUrl}/${selected.version}`, 'DELETE');
      const remaining = versions.filter((item) => item.version !== selected.version);
      setVersions(remaining);
      const next = remaining.find((item) => item.status === 'ACTIVE') ?? remaining[0];
      if (next) open(next);
      else { setSelected(undefined); setDocument(undefined); setInitialDocument(undefined); }
      setMessage('Draft deleted.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Failed to delete draft');
    } finally {
      setBusy(false);
    }
  }

  if (!selected || !document || !initialDocument) return <main className="loading-panel">
    <h1>Order flow editor</h1><p role="status">{message}</p>
    <button disabled={busy} onClick={() => void reload()}>Retry</button>
  </main>;

  return <div className="order-editor">
    <div className="order-status" role="status">{message}{dirty ? ' Unsaved changes.' : ''}</div>
    <App key={generation} embedded={{
      initialDocument,
      onDocumentChange: setDocument,
      readOnly: busy || selected.status !== 'DRAFT',
      toolbar: <>
        <label className="version-label">Order version
          <select aria-label="Order version" value={selected.version} disabled={busy || dirty}
            onChange={(event) => {
              const version = versions.find((item) => item.version === Number(event.target.value));
              if (version) { open(version); setMessage(`Opened version ${version.version} (${version.status}).`); }
            }}>
            {versions.map((version) => <option key={version.version} value={version.version}>
              v{version.version} · {version.status}
            </option>)}
          </select>
        </label>
        <button className="text-button" disabled={busy || dirty} onClick={() => void mutate('create')}>Create draft</button>
        <button className="text-button" disabled={busy || selected.status !== 'DRAFT' || !dirty}
          onClick={() => void mutate('save')}>Save draft</button>
        <button className="text-button" disabled={busy || selected.status !== 'DRAFT' || dirty}
          onClick={() => void mutate('publish')}>Publish</button>
        <button className="text-button" disabled={busy || selected.status !== 'DRAFT'}
          onClick={() => void deleteDraft()}>Delete draft</button>
        {dirty && <button className="text-button" disabled={busy} onClick={() => { open(selected); setMessage('Local changes discarded.'); }}>Discard changes</button>}
      </>,
    }} />
  </div>;
}
