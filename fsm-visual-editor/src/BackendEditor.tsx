import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Server } from 'lucide-react';
import { App } from './App';
import { normalizeEditorDocument, type CatalogBehavior, type FsmEditorDocument } from './domain';
import { backendApi, groupFlowsByKind, initialFlowKey, type FlowRegistration, type StoredFlowVersion } from './domain/backend';
import { fromFlowDefinition, toFlowDefinition } from './domain/flowDefinition';

type Mutation = 'create' | 'save' | 'publish' | 'activate';

const READ_ONLY_HINT = 'Flow loaded. Move states to rearrange them; Create draft saves the layout and allows editing.';

interface OpenedVersion {
  session: string;
  version?: StoredFlowVersion;
  document?: FsmEditorDocument;
}

/** Fields of the flow form; a copy takes its first draft from the open version of the open flow. */
interface FlowForm {
  mode: 'create' | 'copy' | 'rename';
  flowKey: string;
  title: string;
  kind: string;
  description: string;
}

/** Counter of opened versions: the React key that remounts the editor. Not `crypto.randomUUID`, which a page
 * served over plain HTTP from a non-localhost host does not have. */
let openedCount = 0;

function open(version?: StoredFlowVersion): OpenedVersion & { error?: string } {
  const session = String(++openedCount);
  if (!version) return { session };
  try {
    const document = normalizeEditorDocument(fromFlowDefinition(version.definition));
    return document ? { session, version, document } : { session, version, error: 'Unsupported editor document.' };
  } catch (cause) {
    return { session, version, error: cause instanceof Error ? cause.message : 'Invalid flow definition.' };
  }
}

/** Flow and version management for flows exposed by a backend's `/fsm-admin/api`. */
export function BackendEditor({ baseUrl }: { baseUrl: string }) {
  const api = useMemo(() => backendApi(baseUrl, window.fsmEditorConfig?.fetch), [baseUrl]);
  const [registrations, setRegistrations] = useState<FlowRegistration[]>([]);
  const [flowsLoaded, setFlowsLoaded] = useState(false);
  const [flowKey, setFlowKey] = useState<string>();
  const [form, setForm] = useState<FlowForm>();
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
  const flow = registrations.find((item) => item.flowKey === flowKey);

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
    return openVersion(active) ?? (!active ? 'No versions yet. Create the first draft.'
      : active.status === 'DRAFT' ? 'Draft loaded. You can edit the graph.' : READ_ONLY_HINT);
  }), [api, openVersion, run]);

  const selectFlow = useCallback((key?: string) => {
    setFlowKey(key);
    setVersions([]);
    setCatalog([]);
    openVersion(undefined);
    return key ? loadVersions(key) : undefined;
  }, [loadVersions, openVersion]);

  /** Reads the flow list again and returns the key to open: the preferred one when it is listed, else the first. */
  const loadFlows = useCallback(async (preferred?: string | null) => {
    const flows = await api.flows();
    setRegistrations(flows);
    setFlowsLoaded(true);
    return initialFlowKey(flows, preferred);
  }, [api]);

  const initialize = useCallback(() => run(async () => {
    const key = await loadFlows(new URLSearchParams(window.location.search).get('flow'));
    await selectFlow(key);
    return key ? undefined : 'No FSMs registered by this application.';
  }), [loadFlows, run, selectFlow]);

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

  // Creating or copying a flow opens it, which would drop edits made after the form was opened.
  const formBlocked = dirty && form?.mode !== 'rename';

  // Every flow change reads the list again, so the select shows what the backend now has.
  const submitFlow = (event: FormEvent) => {
    event.preventDefault();
    if (busy || !form || formBlocked) return;
    void run(async () => {
      const description = form.description.trim() || undefined;
      if (form.mode === 'rename') {
        await api.renameFlow(flowKey!, form.title, description);
        await loadFlows();
        setForm(undefined);
        return 'Flow renamed.';
      }
      const created = await api.createFlow({ flowKey: form.flowKey, title: form.title, kind: form.kind, description,
        ...(form.mode === 'copy' ? { copyOf: flowKey, copyVersion: selected?.version } : {}) });
      await loadFlows();
      setForm(undefined);
      await selectFlow(created.flowKey);
      return undefined;
    });
  };

  const deleteFlow = () => {
    if (busy || !flow) return;
    if (!window.confirm(`Delete flow "${flow.title}" with all its versions?`)) return;
    void run(async () => {
      await api.deleteFlow(flow.flowKey);
      const key = await loadFlows(new URLSearchParams(window.location.search).get('flow'));
      await selectFlow(key);
      return key ? undefined : 'Flow deleted. No FSMs registered by this application.';
    });
  };

  // Highlight the next step: save edits, publish a saved draft, otherwise start a draft.
  const next: Mutation = selected?.status === 'DRAFT' ? (dirty ? 'save' : 'publish') : 'create';
  const button = (action: Mutation) => `text-button${next === action ? ' primary' : ''}`;
  const status = message + (!dirty ? '' : selected?.status === 'DRAFT' ? ' Unsaved changes.'
    : ' Unsaved changes: Create draft keeps this layout in a new version.');
  const flowOption = (item: FlowRegistration) => <option key={item.flowKey} value={item.flowKey}>{item.title}</option>;
  // One flow change at a time: the open form belongs to the flow it was opened for.
  const flowsLocked = busy || dirty || !flowsLoaded || Boolean(form);
  const editFlow = (mode: FlowForm['mode']) => setForm({ mode, flowKey: mode === 'rename' ? flow!.flowKey : '',
    title: mode === 'create' ? '' : mode === 'copy' ? `${flow!.title} copy` : flow!.title,
    kind: flow?.kind ?? '', description: mode === 'rename' ? flow!.description ?? '' : '' });
  const field = (name: 'flowKey' | 'title' | 'kind' | 'description') => ({
    value: form![name],
    onChange: (event: { target: { value: string } }) => setForm({ ...form!, [name]: event.target.value }),
  });
  return (
    <div className="backend-shell">
      <header className="backend-bar">
        <div className="backend-title">
          <Server size={20} aria-hidden />
          <h1>FSM administration</h1>
        </div>
        <div className="toolbar-group">
          <label>Flow
            <select aria-label="Flow" value={flowKey ?? ''} disabled={busy || dirty || !flowKey || Boolean(form)}
              title={flow?.description ?? undefined} onChange={(event) => void selectFlow(event.target.value)}>
              {registrations.some((item) => item.kind)
                ? groupFlowsByKind(registrations).map((group) =>
                  <optgroup key={group.kind} label={group.kind}>{group.flows.map(flowOption)}</optgroup>)
                : registrations.map(flowOption)}
            </select>
          </label>
          <button type="button" className="text-button" disabled={flowsLocked} onClick={() => editFlow('create')}>New flow</button>
          <button type="button" className="text-button" disabled={flowsLocked || !flow} onClick={() => editFlow('copy')}>Copy flow</button>
          <button type="button" className="text-button" disabled={flowsLocked || !flow} onClick={() => editFlow('rename')}>Rename flow</button>
          <button type="button" className="text-button danger" disabled={flowsLocked || !flow || flow.deletable === false}
            title={flow?.deletable === false ? 'The application does not allow deleting this flow.' : undefined}
            onClick={deleteFlow}>Delete flow</button>
        </div>
        <div className="toolbar-group">
          <label>Version
            <select aria-label="Version" value={selected?.version ?? ''} disabled={unavailable || dirty}
              onChange={(event) => openSelected(Number(event.target.value))}>
              {versions.map((item) => <option key={item.version} value={item.version}>
                {`v${item.version} · ${item.status} · ${item.definition.editor?.name ?? flow?.title}`}
              </option>)}
            </select>
          </label>
          <button type="button" className={button('create')}
            disabled={busy || (dirty && selected?.status === 'DRAFT') || !versionsLoaded || Boolean(selected && !loaded)}
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
        {form && <form className="backend-form" aria-label="Flow details" onSubmit={submitFlow}>
          <label>Key<input aria-label="Flow key" required disabled={form.mode === 'rename'} {...field('flowKey')} /></label>
          <label>Title<input aria-label="Flow title" required {...field('title')} /></label>
          <label>Kind<input aria-label="Flow kind" required disabled={form.mode === 'rename'} list="flow-kinds" {...field('kind')} /></label>
          <datalist id="flow-kinds">
            {groupFlowsByKind(registrations).map((group) => <option key={group.kind} value={group.kind} />)}
          </datalist>
          <label>Description<input aria-label="Flow description" {...field('description')} /></label>
          {form.mode === 'copy' && <span className="backend-copy-source">
            {selected ? `Copies v${selected.version} (${selected.status})` : 'Copies the newest version'}
          </span>}
          <button type="submit" className="text-button primary" disabled={busy || formBlocked}
            title={formBlocked ? 'Save the draft first: opening the new flow would drop its unsaved changes.' : undefined}>
            {form.mode === 'create' ? 'Create flow' : form.mode === 'copy' ? 'Create copy' : 'Save flow'}
          </button>
          <button type="button" className="text-button" onClick={() => setForm(undefined)}>Cancel</button>
        </form>}
        <div className="backend-status">
          <p id="flow-message" role="status" className={failed ? 'status-error' : undefined}>{status}</p>
          <span className="backend-origin" title={baseUrl}>
            Backend {new URL(baseUrl).host}
            {window.parent === window && <> · <a href={window.location.pathname}>Local editor</a></>}
          </span>
        </div>
      </header>
      {opened.document && <App key={opened.session} embedded={{ initialDocument: opened.document, catalog, flowKind: flow?.kind,
        readOnly: busy || selected?.status !== 'DRAFT', movable: !busy, onDocumentChange: changed }} />}
    </div>
  );
}
