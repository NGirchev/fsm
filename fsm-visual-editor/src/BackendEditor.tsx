import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Server } from 'lucide-react';
import { App } from './App';
import { normalizeEditorDocument, type CatalogBehavior, type FsmEditorDocument } from './domain';
import { backendApi, type FlowRegistration, type StoredFlowVersion } from './domain/backend';
import { fromFlowDefinition, toFlowDefinition } from './domain/flowDefinition';

type Mutation = 'create' | 'save' | 'publish' | 'activate';

interface OpenedVersion {
  session: string;
  version?: StoredFlowVersion;
  document?: FsmEditorDocument;
}

function open(version?: StoredFlowVersion): OpenedVersion & { error?: string } {
  const session = crypto.randomUUID();
  if (!version) return { session };
  try {
    const document = normalizeEditorDocument(fromFlowDefinition(version.definition));
    return document ? { session, version, document } : { session, version, error: 'Unsupported editor document.' };
  } catch (cause) {
    return { session, version, error: cause instanceof Error ? cause.message : 'Invalid flow definition.' };
  }
}

/** Version management for flows exposed by a backend's `/fsm-admin/api`. */
export function BackendEditor({ baseUrl }: { baseUrl: string }) {
  const api = useMemo(() => backendApi(baseUrl, window.fsmEditorConfig?.fetch), [baseUrl]);
  const [registrations, setRegistrations] = useState<FlowRegistration[]>([]);
  const [flowKey, setFlowKey] = useState<string>();
  const [versions, setVersions] = useState<StoredFlowVersion[]>([]);
  const [catalog, setCatalog] = useState<CatalogBehavior[]>([]);
  const [versionsLoaded, setVersionsLoaded] = useState(false);
  const [opened, setOpened] = useState<OpenedVersion>(() => open());
  const [loaded, setLoaded] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('Loading flows…');
  const [failed, setFailed] = useState(false);
  const current = useRef<FsmEditorDocument>();
  const saved = useRef<string>();
  const selected = opened.version;

  const openVersion = useCallback((version?: StoredFlowVersion) => {
    const next = open(version);
    current.current = undefined;
    saved.current = undefined;
    setDirty(false);
    setLoaded(false);
    setOpened(next);
    return next.error;
  }, []);

  const run = useCallback(async (work: () => Promise<string | undefined>) => {
    setBusy(true);
    try {
      const result = await work();
      if (result !== undefined) {
        setMessage(result);
        setFailed(false);
      }
    } catch (cause) {
      setMessage(cause instanceof Error ? cause.message : 'Request failed.');
      setFailed(true);
    } finally {
      setBusy(false);
    }
  }, []);

  const loadVersions = useCallback((key: string) => run(async () => {
    setVersionsLoaded(false);
    const [nextVersions, nextCatalog] = await Promise.all([api.versions(key), api.behaviors(key)]);
    setVersions(nextVersions);
    setCatalog(nextCatalog);
    setVersionsLoaded(true);
    const active = nextVersions.find((item) => item.status === 'ACTIVE') ?? nextVersions[0];
    return openVersion(active) ?? (active ? 'Flow loaded. Create a draft to edit.' : 'No versions yet. Create the first draft.');
  }), [api, openVersion, run]);

  const selectFlow = useCallback((key: string) => {
    setFlowKey(key);
    setVersions([]);
    setCatalog([]);
    openVersion(undefined);
    return loadVersions(key);
  }, [loadVersions, openVersion]);

  const initialize = useCallback(() => run(async () => {
    const flows = await api.flows();
    setRegistrations(flows);
    if (!flows[0]) return 'No FSMs registered by this application.';
    await selectFlow(flows[0].flowKey);
    return undefined;
  }), [api, run, selectFlow]);

  useEffect(() => { void initialize(); }, [initialize]);

  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const changed = useCallback((document: FsmEditorDocument) => {
    current.current = document;
    saved.current ??= JSON.stringify(document);
    setDirty(JSON.stringify(document) !== saved.current);
    setLoaded(true);
  }, []);

  const unavailable = busy || !loaded || !selected;

  const mutate = (action: Mutation) => {
    if (busy || !versionsLoaded || !flowKey || (selected && !loaded)) return;
    void run(async () => {
      // Publishing and activation use the stored version; other actions submit the current graph.
      const document = !selected || action === 'publish' || action === 'activate' ? undefined : current.current;
      const definition = document && toFlowDefinition(document);
      const result = action === 'create' ? await api.create(flowKey, definition)
        : action === 'save' ? await api.save(flowKey, selected!.version, definition!)
        : action === 'publish' ? await api.publish(flowKey, selected!.version)
        : await api.activate(flowKey, selected!.version);
      const replaced = action === 'publish' || action === 'activate';
      setVersions((items) => [result, ...items.filter((item) => item.version !== result.version)]
        .map((item) => replaced && item.version !== result.version && item.status === 'ACTIVE'
          ? { ...item, status: 'ARCHIVED' as const } : item)
        .sort((left, right) => right.version - left.version));
      if (action === 'save') {
        setOpened((value) => ({ ...value, version: result }));
        saved.current = JSON.stringify(document);
        setDirty(JSON.stringify(current.current) !== saved.current);
      } else {
        const error = openVersion(result);
        if (error) return error;
      }
      return action === 'publish' ? 'Published. New instances use this version.'
        : action === 'activate' ? 'Active flow changed. New instances use this version.'
        : action === 'save' ? 'Draft and layout saved.' : 'Draft created. You can edit the graph.';
    });
  };

  const deleteDraft = () => {
    if (busy || !flowKey || selected?.status !== 'DRAFT') return;
    if (!window.confirm(`Delete draft v${selected.version}?${dirty ? ' Unsaved changes will also be lost.' : ''}`)) return;
    void run(async () => {
      await api.deleteDraft(flowKey, selected.version);
      const remaining = versions.filter((item) => item.version !== selected.version);
      setVersions(remaining);
      return openVersion(remaining.find((item) => item.status === 'ACTIVE') ?? remaining[0]) ?? 'Draft deleted.';
    });
  };

  const openSelected = (version: number) => {
    const next = versions.find((item) => item.version === version);
    if (next) setMessage(openVersion(next) ?? `Opened version ${next.version} (${next.status}).`);
  };

  // Highlight the next step: save edits, publish a saved draft, otherwise start a draft.
  const next: Mutation = selected?.status === 'DRAFT' ? (dirty ? 'save' : 'publish') : 'create';
  const button = (action: Mutation) => `text-button${next === action ? ' primary' : ''}`;
  const status = message + (dirty ? ' Unsaved changes.' : '');
  return (
    <div className="backend-shell">
      <header className="backend-bar">
        <div className="backend-title">
          <Server size={20} aria-hidden />
          <h1>FSM administration</h1>
        </div>
        <div className="toolbar-group">
          <label>Flow
            <select aria-label="Flow" value={flowKey ?? ''} disabled={busy || dirty || !flowKey}
              onChange={(event) => void selectFlow(event.target.value)}>
              {registrations.map((item) => <option key={item.flowKey} value={item.flowKey}>{item.title}</option>)}
            </select>
          </label>
          <label>Version
            <select aria-label="Version" value={selected?.version ?? ''} disabled={unavailable || dirty}
              onChange={(event) => openSelected(Number(event.target.value))}>
              {versions.map((item) => <option key={item.version} value={item.version}>
                {`v${item.version} · ${item.status} · ${item.definition.editor?.name
                  ?? registrations.find((flow) => flow.flowKey === flowKey)?.title}`}
              </option>)}
            </select>
          </label>
        </div>
        <div className="toolbar-group">
          <button type="button" className={button('create')}
            disabled={busy || dirty || !versionsLoaded || Boolean(selected && !loaded)}
            onClick={() => mutate('create')}>Create draft</button>
          <button type="button" className={button('save')} disabled={unavailable || selected.status !== 'DRAFT' || !dirty}
            onClick={() => mutate('save')}>Save draft</button>
          <button type="button" className={button('publish')} disabled={unavailable || selected.status !== 'DRAFT' || dirty}
            onClick={() => mutate('publish')}>Publish</button>
          <button type="button" className="text-button" disabled={unavailable || selected.status !== 'ARCHIVED' || dirty}
            onClick={() => mutate('activate')}>Make active</button>
          <button type="button" className="text-button" hidden={!dirty} disabled={unavailable}
            onClick={() => { setMessage(openVersion(selected) ?? 'Local changes discarded.'); }}>Discard changes</button>
          <button type="button" className="text-button danger" disabled={unavailable || selected.status !== 'DRAFT'}
            onClick={deleteDraft}>Delete draft</button>
          <button type="button" className="text-button" hidden={Boolean(selected && loaded)} disabled={busy}
            onClick={() => void (flowKey ? loadVersions(flowKey) : initialize())}>Retry</button>
        </div>
        <div className="backend-status">
          <p id="flow-message" role="status" className={failed ? 'status-error' : undefined}>{status}</p>
          <span className="backend-origin" title={baseUrl}>
            Backend {new URL(baseUrl).host}
            {window.parent === window && <> · <a href={window.location.pathname}>Local editor</a></>}
          </span>
        </div>
      </header>
      {opened.document && <App key={opened.session} embedded={{ initialDocument: opened.document, catalog,
        readOnly: busy || selected?.status !== 'DRAFT', onDocumentChange: changed }} />}
    </div>
  );
}
