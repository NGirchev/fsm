import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import {
  Background,
  Controls,
  MarkerType,
  MiniMap,
  ReactFlow,
  type Connection,
  type Edge,
  type EdgeChange,
  type Node,
  type NodeChange,
} from '@xyflow/react';
import {
  AlertCircle,
  FilePlus2,
  FileCode2,
  FileJson,
  GitBranchPlus,
  Plus,
  Trash2,
  Upload,
} from 'lucide-react';
import {
  createEmptyDocument,
  createProjectId,
  deleteSavedProject,
  generateJavaFactory,
  generateKotlinFactory,
  listSavedProjects,
  loadCurrentProjectId,
  loadEditorDocument,
  loadSavedProject,
  normalizeEditorDocument,
  sampleDocument,
  saveCurrentProjectId,
  saveEditorDocument,
  saveProject,
  TIME_UNITS,
  transitionDuplicateKey,
  validateEditorDocument,
  type BehaviorRef,
  type FsmEditorDocument,
  type FsmState,
  type FsmTransition,
  type SavedProject,
  type TimeUnit,
} from './domain';
import { createId, slugifyId, uniqueId } from './domain/ids';

type Selection = { type: 'state' | 'transition'; id: string } | null;
const SAMPLE_PROJECT_ID = 'document-fsm-sample';

export function App() {
  const [document, setDocument] = useState<FsmEditorDocument>(() => loadEditorDocument() ?? sampleDocument);
  const [currentProjectId, setCurrentProjectId] = useState(() => {
    const savedProjectId = loadCurrentProjectId();

    if (savedProjectId) {
      return savedProjectId;
    }

    const loadedDocument = loadEditorDocument();
    return loadedDocument ? createProjectId(loadedDocument) : SAMPLE_PROJECT_ID;
  });
  const [recentProjects, setRecentProjects] = useState<SavedProject[]>([]);
  const [selection, setSelection] = useState<Selection>(null);
  const [status, setStatus] = useState('Ready');
  const importInputRef = useRef<HTMLInputElement>(null);
  const validationIssues = useMemo(() => validateEditorDocument(document), [document]);

  useEffect(() => {
    saveEditorDocument(document);
    saveCurrentProjectId(currentProjectId);

    const timeoutId = window.setTimeout(() => {
      void saveProject(currentProjectId, document)
        .then(() => loadRecentProjects(setRecentProjects))
        .then(() => setStatus(`Saved to projects/${currentProjectId}.fsm.json`))
        .catch(() => setStatus('Saved in browser only; project API is unavailable'));
    }, 500);

    return () => window.clearTimeout(timeoutId);
  }, [currentProjectId, document]);

  useEffect(() => {
    void loadRecentProjects(setRecentProjects);
  }, []);

  const nodes = useMemo<Node[]>(
    () =>
      document.states.map((state) => ({
        id: state.id,
        position: state.position,
        data: {
          label: state.label,
        },
        className: selection?.type === 'state' && selection.id === state.id ? 'selected-node' : undefined,
      })),
    [document.states, selection],
  );

  const edges = useMemo<Edge[]>(
    () =>
      document.transitions.map((transition) => ({
        id: transition.id,
        source: transition.from,
        target: transition.to,
        label: transitionLabel(transition),
        markerEnd: { type: MarkerType.ArrowClosed },
        className: selection?.type === 'transition' && selection.id === transition.id ? 'selected-edge' : undefined,
      })),
    [document.transitions, selection],
  );

  const selectedState = selection?.type === 'state' ? document.states.find((state) => state.id === selection.id) : undefined;
  const selectedTransition =
    selection?.type === 'transition' ? document.transitions.find((transition) => transition.id === selection.id) : undefined;

  const onNodesChange = useCallback((changes: NodeChange[]) => {
    setDocument((current) => applyNodeChangesToDocument(current, changes));
  }, []);

  const onEdgesChange = useCallback((changes: EdgeChange[]) => {
    setDocument((current) => applyEdgeChangesToDocument(current, changes));
  }, []);

  const onConnect = useCallback((connection: Connection) => {
    const source = connection.source;
    const target = connection.target;

    if (!source || !target) {
      return;
    }

    if (hasAutoTransition(document, source, target)) {
      setStatus('Auto transition already exists');
      return;
    }

    setDocument((current) => addAutoTransition(current, source, target));
    setStatus('Auto transition added');
  }, [document]);

  const addState = () => {
    setDocument((current) => {
      const existingIds = new Set(current.states.map((state) => state.id));
      const label = `STATE_${current.states.length + 1}`;
      const id = uniqueId(slugifyId(label, 'state'), existingIds);
      const state: FsmState = {
        id,
        label,
        position: { x: 80 + current.states.length * 40, y: 80 + current.states.length * 30 },
      };

      setSelection({ type: 'state', id });
      return {
        ...current,
        states: [...current.states, state],
      };
    });
  };

  const addBehavior = (kind: 'conditions' | 'actions') => {
    const label = kind === 'conditions' ? 'guardName' : 'actionName';
    setDocument((current) => {
      const existingIds = new Set(current.behaviors[kind].map((behavior) => behavior.id));
      const id = uniqueId(label, existingIds);

      return {
        ...current,
        behaviors: {
          ...current.behaviors,
          [kind]: [...current.behaviors[kind], { id }],
        },
      };
    });
  };

  const addEvent = () => {
    setDocument((current) => ({
      ...current,
      events: [...current.events, { id: nextEventId(current) }],
    }));
  };

  const deleteEvent = (index: number) => {
    setDocument((current) => {
      const eventId = current.events[index]?.id;

      if (eventId === undefined) {
        return current;
      }

      const removedTransitionIds = current.transitions
        .filter((transition) => transition.trigger.kind === 'event' && transition.trigger.event === eventId)
        .map((transition) => transition.id);

      if (selection?.type === 'transition' && removedTransitionIds.includes(selection.id)) {
        setSelection(null);
      }

      return deleteEventAtIndex(current, index);
    });
    setStatus('Event deleted');
  };

  const deleteSelection = () => {
    if (!selection) {
      return;
    }

    setDocument((current) => {
      if (selection.type === 'state') {
        return {
          ...current,
          states: current.states.filter((state) => state.id !== selection.id),
          transitions: current.transitions.filter(
            (transition) => transition.from !== selection.id && transition.to !== selection.id,
          ),
        };
      }

      return {
        ...current,
        transitions: current.transitions.filter((transition) => transition.id !== selection.id),
      };
    });
    setSelection(null);
    setStatus('Selection deleted');
  };

  const importFile = async (file: File) => {
    try {
      const parsed = JSON.parse(await file.text()) as unknown;

      const importedDocument = normalizeEditorDocument(parsed);

      if (importedDocument) {
        setDocument(importedDocument);
        setCurrentProjectId(createProjectId(importedDocument));
        setStatus(`Imported editor JSON: ${file.name}`);
        return;
      }

      setStatus('Unsupported JSON file. Import the editor .fsm.json format.');
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Failed to import JSON');
    } finally {
      if (importInputRef.current) {
        importInputRef.current.value = '';
      }
    }
  };

  const exportEditorJson = () => downloadJson(`${fileBaseName(document.name)}.fsm.json`, document);
  const exportJava = () => {
    downloadText(`${document.codegen.className}.java`, generateJavaFactory(document), 'text/x-java-source;charset=utf-8');
  };
  const exportKotlin = () => {
    downloadText(`${document.codegen.className}.kt`, generateKotlinFactory(document), 'text/x-kotlin;charset=utf-8');
  };

  const createNewFlow = () => {
    const nextDocument = createEmptyDocument();
    setDocument(nextDocument);
    setCurrentProjectId(createProjectId(nextDocument));
    setSelection(null);
    setStatus('New flow created');
  };

  const openRecentProject = async (projectId: string) => {
    if (!projectId) {
      return;
    }

    if (projectId === SAMPLE_PROJECT_ID) {
      setDocument(sampleDocument);
      setCurrentProjectId(SAMPLE_PROJECT_ID);
      setSelection(null);
      setStatus('Opened example Document FSM');
      return;
    }

    try {
      const savedDocument = await loadSavedProject(projectId);
      setDocument(savedDocument);
      setCurrentProjectId(projectId);
      setSelection(null);
      setStatus(`Opened projects/${projectId}.fsm.json`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Failed to open saved project');
    }
  };

  const deleteCurrentProject = async () => {
    if (currentProjectId === SAMPLE_PROJECT_ID) {
      setStatus('The built-in example cannot be deleted');
      return;
    }

    try {
      await deleteSavedProject(currentProjectId);
      const refreshedProjects = await listSavedProjects();
      setRecentProjects(refreshedProjects);

      const nextProject = refreshedProjects[0];
      if (nextProject) {
        await openRecentProject(nextProject.id);
        setStatus(`Deleted project and opened ${nextProject.name}`);
        return;
      }

      setDocument(sampleDocument);
      setCurrentProjectId(SAMPLE_PROJECT_ID);
      setSelection(null);
      setStatus('Deleted project and opened example Document FSM');
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Failed to delete project');
    }
  };

  return (
    <div className="app-shell">
      <header className="toolbar">
        <div className="toolbar-title">
          <GitBranchPlus size={22} aria-hidden />
          <div>
            <strong>FSM Visual Editor</strong>
            <span>{status}</span>
          </div>
        </div>
        <div className="toolbar-actions">
          <select
            className="recent-select"
            value={currentProjectId}
            onChange={(event) => void openRecentProject(event.target.value)}
            title="Recent projects"
            aria-label="Recent projects"
          >
            <option value={currentProjectId}>Current: {document.name}</option>
            {currentProjectId !== SAMPLE_PROJECT_ID && <option value={SAMPLE_PROJECT_ID}>Example: Document FSM</option>}
            {recentProjects
              .filter((project) => project.id !== currentProjectId && project.id !== SAMPLE_PROJECT_ID)
              .map((project) => (
                <option key={project.id} value={project.id}>
                  {project.name}
                </option>
              ))}
          </select>
          <button type="button" className="text-button" onClick={createNewFlow}>
            <FilePlus2 size={17} />
            New flow
          </button>
          <button
            type="button"
            className="icon-button danger"
            onClick={() => void deleteCurrentProject()}
            title="Delete current project"
            aria-label="Delete current project"
            disabled={currentProjectId === SAMPLE_PROJECT_ID}
          >
            <Trash2 size={18} />
          </button>
          <button type="button" className="icon-button" onClick={addState} title="Add state" aria-label="Add state">
            <Plus size={18} />
          </button>
          <button
            type="button"
            className="icon-button"
            onClick={() => importInputRef.current?.click()}
            title="Import JSON"
            aria-label="Import JSON"
          >
            <Upload size={18} />
          </button>
          <button type="button" className="icon-button" onClick={exportEditorJson} title="Export editor JSON" aria-label="Export editor JSON">
            <FileJson size={18} />
          </button>
          <button type="button" className="icon-button" onClick={exportJava} title="Generate Java class" aria-label="Generate Java class">
            <FileCode2 size={18} />
          </button>
          <button type="button" className="text-button" onClick={exportKotlin} title="Generate Kotlin class">
            KT
          </button>
          <button
            type="button"
            className="icon-button danger"
            onClick={deleteSelection}
            title="Delete selected"
            aria-label="Delete selected"
            disabled={!selection}
          >
            <Trash2 size={18} />
          </button>
          <input
            ref={importInputRef}
            type="file"
            accept="application/json,.json"
            hidden
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) {
                void importFile(file);
              }
            }}
          />
        </div>
      </header>

      <main className="workspace">
        <section className="canvas" aria-label="FSM graph">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={(_, node) => setSelection({ type: 'state', id: node.id })}
            onEdgeClick={(_, edge) => setSelection({ type: 'transition', id: edge.id })}
            onPaneClick={() => setSelection(null)}
            fitView
            defaultEdgeOptions={{ type: 'smoothstep' }}
          >
            <Background gap={20} size={1} />
            <MiniMap pannable zoomable />
            <Controls />
          </ReactFlow>
        </section>

        <aside className="inspector">
          <ProjectPanel document={document} setDocument={setDocument} />
          <EventPanel document={document} setDocument={setDocument} addEvent={addEvent} deleteEvent={deleteEvent} />
          <BehaviorPanel document={document} setDocument={setDocument} addBehavior={addBehavior} />
          {selectedState && <StateInspector state={selectedState} document={document} setDocument={setDocument} />}
          {selectedTransition && <TransitionInspector transition={selectedTransition} document={document} setDocument={setDocument} />}
          <ValidationPanel issues={validationIssues} />
        </aside>
      </main>
    </div>
  );
}

function ProjectPanel({
  document,
  setDocument,
}: {
  document: FsmEditorDocument;
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
}) {
  return (
    <section className="panel">
      <h2>Project</h2>
      <label>
        Name
        <input value={document.name} onChange={(event) => setDocument((current) => ({ ...current, name: event.target.value }))} />
      </label>
      <label>
        Package
        <input
          value={document.codegen.packageName}
          onChange={(event) => updateCodegen(setDocument, 'packageName', event.target.value)}
        />
      </label>
      <label>
        Factory class
        <input value={document.codegen.className} onChange={(event) => updateCodegen(setDocument, 'className', event.target.value)} />
      </label>
      <div className="field-row">
        <label>
          Domain
          <input value={document.codegen.domainType} onChange={(event) => updateCodegen(setDocument, 'domainType', event.target.value)} />
        </label>
        <label>
          State
          <input value={document.codegen.stateType} onChange={(event) => updateCodegen(setDocument, 'stateType', event.target.value)} />
        </label>
      </div>
      <div className="field-row">
        <label>
          Event
          <input value={document.codegen.eventType} onChange={(event) => updateCodegen(setDocument, 'eventType', event.target.value)} />
        </label>
        <label>
          Initial
          <select
            value={document.codegen.initialState}
            onChange={(event) => updateCodegen(setDocument, 'initialState', event.target.value)}
          >
            {document.states.map((state) => (
              <option key={state.id} value={state.label}>
                {state.label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label>
        Code style
        <select value={document.codegen.style} onChange={(event) => updateCodegen(setDocument, 'style', event.target.value)}>
          <option value="fluent">Fluent chain</option>
          <option value="builder">Builder add calls</option>
        </select>
      </label>
      <label className="toggle-row">
        <input
          type="checkbox"
          checked={document.autoTransitionEnabled}
          onChange={(event) => setDocument((current) => ({ ...current, autoTransitionEnabled: event.target.checked }))}
        />
        Auto transitions
      </label>
    </section>
  );
}

function EventPanel({
  document,
  setDocument,
  addEvent,
  deleteEvent,
}: {
  document: FsmEditorDocument;
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
  addEvent: () => void;
  deleteEvent: (index: number) => void;
}) {
  return (
    <section className="panel">
      <div className="section-head">
        <h2>Events</h2>
        <button type="button" className="small-button" onClick={addEvent} title="Add event" aria-label="Add event">
          <Plus size={14} />
        </button>
      </div>
      <EventList events={document.events} setDocument={setDocument} deleteEvent={deleteEvent} />
    </section>
  );
}

function EventList({
  events,
  setDocument,
  deleteEvent,
}: {
  events: BehaviorRef[];
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
  deleteEvent: (index: number) => void;
}) {
  return (
    <div className="behavior-list">
      {events.length === 0 && <span className="muted">No events</span>}
      {events.map((eventRef, index) => (
        <div className="behavior-row" key={`event-${index}`}>
          <input
            value={eventRef.id}
            onChange={(event) => setDocument((current) => renameEventAtIndex(current, index, event.target.value))}
          />
          <button
            type="button"
            className="small-button danger"
            onClick={() => deleteEvent(index)}
            title="Delete event"
            aria-label={`Delete event ${eventRef.id}`}
          >
            <Trash2 size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}

function BehaviorPanel({
  document,
  setDocument,
  addBehavior,
}: {
  document: FsmEditorDocument;
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
  addBehavior: (kind: 'conditions' | 'actions') => void;
}) {
  return (
    <section className="panel">
      <h2>Behavior</h2>
      <BehaviorList title="Guards" kind="conditions" behaviors={document.behaviors.conditions} setDocument={setDocument} addBehavior={addBehavior} />
      <BehaviorList title="Actions" kind="actions" behaviors={document.behaviors.actions} setDocument={setDocument} addBehavior={addBehavior} />
    </section>
  );
}

function BehaviorList({
  title,
  kind,
  behaviors,
  setDocument,
  addBehavior,
}: {
  title: string;
  kind: 'conditions' | 'actions';
  behaviors: BehaviorRef[];
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
  addBehavior: (kind: 'conditions' | 'actions') => void;
}) {
  return (
    <div className="behavior-list">
      <div className="section-head">
        <span>{title}</span>
        <button type="button" className="small-button" onClick={() => addBehavior(kind)}>
          <Plus size={14} />
        </button>
      </div>
      {behaviors.map((behavior, index) => (
        <input
          key={`${kind}-${index}`}
          value={behavior.id}
          onChange={(event) =>
            setDocument((current) => renameBehaviorAtIndex(current, kind, index, event.target.value))
          }
        />
      ))}
    </div>
  );
}

function StateInspector({
  state,
  document,
  setDocument,
}: {
  state: FsmState;
  document: FsmEditorDocument;
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
}) {
  return (
    <section className="panel selected-panel">
      <h2>State</h2>
      <label>
        Label
        <input
          value={state.label}
          onChange={(event) =>
            setDocument((current) => ({
              ...current,
              states: current.states.map((candidate) =>
                candidate.id === state.id ? { ...candidate, label: event.target.value } : candidate,
              ),
              codegen:
                current.codegen.initialState === state.label
                  ? { ...current.codegen, initialState: event.target.value }
                  : current.codegen,
            }))
          }
        />
      </label>
      <label>
        Description
        <textarea
          value={state.description ?? ''}
          onChange={(event) =>
            setDocument((current) => ({
              ...current,
              states: current.states.map((candidate) =>
                candidate.id === state.id ? { ...candidate, description: event.target.value } : candidate,
              ),
            }))
          }
        />
      </label>
      <p className="muted">Incoming: {document.transitions.filter((transition) => transition.to === state.id).length}</p>
      <p className="muted">Outgoing: {document.transitions.filter((transition) => transition.from === state.id).length}</p>
    </section>
  );
}

function TransitionInspector({
  transition,
  document,
  setDocument,
}: {
  transition: FsmTransition;
  document: FsmEditorDocument;
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
}) {
  const updateTransition = (patch: Partial<FsmTransition>) => {
    setDocument((current) => ({
      ...current,
      transitions: current.transitions.map((candidate) => (candidate.id === transition.id ? { ...candidate, ...patch } : candidate)),
    }));
  };
  let eventTrigger = document.events[0]?.id;

  if (transition.trigger.kind === 'event') {
    const currentEvent = transition.trigger.event;

    if (document.events.some((eventRef) => eventRef.id === currentEvent)) {
      eventTrigger = currentEvent;
    }
  }

  return (
    <section className="panel selected-panel">
      <h2>Transition</h2>
      <div className="field-row">
        <label>
          From
          <select value={transition.from} onChange={(event) => updateTransition({ from: event.target.value })}>
            {document.states.map((state) => (
              <option key={state.id} value={state.id}>
                {state.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          To
          <select value={transition.to} onChange={(event) => updateTransition({ to: event.target.value })}>
            {document.states.map((state) => (
              <option key={state.id} value={state.id}>
                {state.label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="segmented-row" role="group" aria-label="Transition trigger">
        <button
          type="button"
          className={transition.trigger.kind === 'event' ? 'active' : ''}
          onClick={() => eventTrigger && updateTransition({ trigger: { kind: 'event', event: eventTrigger } })}
          disabled={!eventTrigger}
          title={eventTrigger ? undefined : 'Create an event first'}
        >
          Event
        </button>
        <button
          type="button"
          className={transition.trigger.kind === 'auto' ? 'active' : ''}
          onClick={() => updateTransition({ trigger: { kind: 'auto' } })}
        >
          Auto
        </button>
      </div>
      {transition.trigger.kind === 'event' && (
        <label>
          Event
          <select
            value={transition.trigger.event}
            onChange={(event) => updateTransition({ trigger: { kind: 'event', event: event.target.value } })}
          >
            {document.events.map((eventRef) => (
              <option key={eventRef.id} value={eventRef.id}>
                {eventRef.id}
              </option>
            ))}
          </select>
        </label>
      )}
      <BehaviorPicker
        title="Guards"
        options={document.behaviors.conditions}
        selected={transition.conditions}
        onChange={(conditions) => updateTransition({ conditions })}
      />
      <BehaviorPicker
        title="Actions"
        options={document.behaviors.actions}
        selected={transition.actions}
        onChange={(actions) => updateTransition({ actions })}
      />
      <BehaviorPicker
        title="Post actions"
        options={document.behaviors.actions}
        selected={transition.postActions}
        onChange={(postActions) => updateTransition({ postActions })}
      />
      <div className="field-row">
        <label>
          Timeout
          <input
            type="number"
            min="1"
            value={transition.timeout?.value ?? ''}
            onChange={(event) =>
              updateTransition({
                timeout: event.target.value
                  ? { value: Number(event.target.value), unit: transition.timeout?.unit ?? 'SECONDS' }
                  : undefined,
              })
            }
          />
        </label>
        <label>
          Unit
          <select
            value={transition.timeout?.unit ?? 'SECONDS'}
            onChange={(event) =>
              updateTransition({
                timeout: {
                  value: transition.timeout?.value ?? 1,
                  unit: event.target.value as TimeUnit,
                },
              })
            }
          >
            {TIME_UNITS.map((unit) => (
              <option key={unit} value={unit}>
                {unit}
              </option>
            ))}
          </select>
        </label>
      </div>
    </section>
  );
}

function BehaviorPicker({
  title,
  options,
  selected,
  onChange,
}: {
  title: string;
  options: BehaviorRef[];
  selected: string[];
  onChange: (next: string[]) => void;
}) {
  return (
    <fieldset className="check-group">
      <legend>{title}</legend>
      {options.length === 0 && <span className="muted">None</span>}
      {options.map((option) => (
        <label key={option.id} className="toggle-row">
          <input
            type="checkbox"
            checked={selected.includes(option.id)}
            onChange={(event) => {
              if (event.target.checked) {
                onChange([...selected, option.id]);
                return;
              }

              onChange(selected.filter((id) => id !== option.id));
            }}
          />
          {option.id}
        </label>
      ))}
    </fieldset>
  );
}

function ValidationPanel({ issues }: { issues: ReturnType<typeof validateEditorDocument> }) {
  const errorCount = issues.filter((issue) => issue.severity === 'error').length;

  return (
    <section className="panel validation-panel">
      <h2>
        <AlertCircle size={16} />
        Validation
      </h2>
      <p className={errorCount > 0 ? 'error-text' : 'ok-text'}>{errorCount > 0 ? `${errorCount} error(s)` : 'No errors'}</p>
      {issues.map((issue, index) => (
        <p key={`${issue.path}-${index}`} className={issue.severity === 'error' ? 'error-text' : 'warning-text'}>
          {issue.path}: {issue.message}
        </p>
      ))}
    </section>
  );
}

function updateCodegen(
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>,
  key: keyof FsmEditorDocument['codegen'],
  value: string,
) {
  setDocument((current) => ({
    ...current,
    codegen: {
      ...current.codegen,
      [key]: value,
    },
  }));
}

async function loadRecentProjects(setRecentProjects: Dispatch<SetStateAction<SavedProject[]>>): Promise<void> {
  try {
    setRecentProjects(await listSavedProjects());
  } catch {
    setRecentProjects([]);
  }
}

function applyNodeChangesToDocument(document: FsmEditorDocument, changes: NodeChange[]): FsmEditorDocument {
  let next = document;

  changes.forEach((change) => {
    if (change.type === 'position' && change.position) {
      next = {
        ...next,
        states: next.states.map((state) => (state.id === change.id ? { ...state, position: change.position! } : state)),
      };
    }

    if (change.type === 'remove') {
      next = {
        ...next,
        states: next.states.filter((state) => state.id !== change.id),
        transitions: next.transitions.filter((transition) => transition.from !== change.id && transition.to !== change.id),
      };
    }
  });

  return next;
}

function applyEdgeChangesToDocument(document: FsmEditorDocument, changes: EdgeChange[]): FsmEditorDocument {
  const removedIds = changes.filter((change) => change.type === 'remove').map((change) => change.id);

  if (removedIds.length === 0) {
    return document;
  }

  return {
    ...document,
    transitions: document.transitions.filter((transition) => !removedIds.includes(transition.id)),
  };
}

function renameBehaviorAtIndex(
  document: FsmEditorDocument,
  kind: 'conditions' | 'actions',
  index: number,
  newId: string,
): FsmEditorDocument {
  const oldId = document.behaviors[kind][index]?.id;

  if (oldId === undefined) {
    return document;
  }

  const behaviors = document.behaviors[kind].map((behavior, behaviorIndex) =>
    behaviorIndex === index ? { ...behavior, id: newId } : behavior,
  );

  return {
    ...document,
    behaviors: {
      ...document.behaviors,
      [kind]: behaviors,
    },
    transitions: document.transitions.map((transition) => {
      if (kind === 'conditions') {
        return {
          ...transition,
          conditions: transition.conditions.map((id) => (id === oldId ? newId : id)),
        };
      }

      return {
        ...transition,
        actions: transition.actions.map((id) => (id === oldId ? newId : id)),
        postActions: transition.postActions.map((id) => (id === oldId ? newId : id)),
      };
    }),
  };
}

function renameEventAtIndex(document: FsmEditorDocument, index: number, newId: string): FsmEditorDocument {
  const oldId = document.events[index]?.id;

  if (oldId === undefined) {
    return document;
  }

  return {
    ...document,
    events: document.events.map((eventRef, eventIndex) => (eventIndex === index ? { ...eventRef, id: newId } : eventRef)),
    transitions: document.transitions.map((transition) =>
      transition.trigger.kind === 'event' && transition.trigger.event === oldId
        ? { ...transition, trigger: { kind: 'event', event: newId } }
        : transition,
    ),
  };
}

export function deleteEventAtIndex(document: FsmEditorDocument, index: number): FsmEditorDocument {
  const eventId = document.events[index]?.id;

  if (eventId === undefined) {
    return document;
  }

  return {
    ...document,
    events: document.events.filter((_, eventIndex) => eventIndex !== index),
    transitions: document.transitions.filter(
      (transition) => transition.trigger.kind !== 'event' || transition.trigger.event !== eventId,
    ),
  };
}

export function addAutoTransition(document: FsmEditorDocument, source: string, target: string): FsmEditorDocument {
  const transition: FsmTransition = {
    id: createId('transition'),
    from: source,
    to: target,
    trigger: { kind: 'auto' },
    conditions: [],
    actions: [],
    postActions: [],
  };

  if (document.transitions.some((candidate) => transitionDuplicateKey(candidate) === transitionDuplicateKey(transition))) {
    return document;
  }

  return {
    ...document,
    transitions: [...document.transitions, transition],
  };
}

function hasAutoTransition(document: FsmEditorDocument, source: string, target: string): boolean {
  const candidate: FsmTransition = {
    id: '',
    from: source,
    to: target,
    trigger: { kind: 'auto' },
    conditions: [],
    actions: [],
    postActions: [],
  };

  return document.transitions.some((transition) => transitionDuplicateKey(transition) === transitionDuplicateKey(candidate));
}

function nextEventId(document: FsmEditorDocument): string {
  return uniqueId('EVENT', new Set(document.events.map((eventRef) => eventRef.id)));
}

function transitionLabel(transition: FsmTransition): string {
  const parts = [transition.trigger.kind === 'event' ? transition.trigger.event : 'auto'];

  transition.conditions.forEach((condition) => parts.push(`[${condition}]`));

  if (transition.timeout) {
    parts.push(`${transition.timeout.value}${shortTimeUnit(transition.timeout.unit)}`);
  }

  return parts.join(' ');
}

function shortTimeUnit(unit: TimeUnit): string {
  switch (unit) {
    case 'NANOSECONDS':
      return 'ns';
    case 'MICROSECONDS':
      return 'us';
    case 'MILLISECONDS':
      return 'ms';
    case 'SECONDS':
      return 's';
    case 'MINUTES':
      return 'm';
    case 'HOURS':
      return 'h';
    case 'DAYS':
      return 'd';
  }
}

function fileBaseName(value: string): string {
  return slugifyId(value, 'fsm');
}

function downloadJson(fileName: string, value: unknown): void {
  downloadText(fileName, `${JSON.stringify(value, null, 2)}\n`, 'application/json;charset=utf-8');
}

function downloadText(fileName: string, text: string, type: string): void {
  const blob = new Blob([text], { type });
  const href = URL.createObjectURL(blob);
  const anchor = document.createElement('a');

  anchor.href = href;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(href);
}
