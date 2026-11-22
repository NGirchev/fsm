import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import {
  Background,
  ControlButton,
  Controls,
  MarkerType,
  MiniMap,
  ReactFlow,
  type Connection,
  type Edge,
  type EdgeChange,
  type Node,
  type NodeChange,
  useReactFlow,
} from '@xyflow/react';
import {
  AlertCircle,
  Box,
  Boxes,
  ChevronDown,
  ChevronUp,
  FilePlus2,
  FileJson,
  GitBranch,
  GitBranchPlus,
  LocateFixed,
  Map as MapIcon,
  Maximize2,
  Minimize2,
  MousePointerClick,
  Network,
  PanelRightClose,
  PanelRightOpen,
  Plug,
  Plus,
  Play,
  CircleDot,
  SlidersHorizontal,
  Trash2,
  Upload,
  Zap,
} from 'lucide-react';
import { Panel } from './Panel';
import { FlowEdge } from './FlowEdge';
import { AUTO_COLOR, colorVariables, colorsForIds, graphColor } from './graphColors';
import { GRAPH_PALETTE, randomStateColor, withStateColors } from './domain/stateColors';
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
  FINAL_OUTCOMES,
  TIME_UNITS,
  transitionDuplicateKey,
  validateEditorDocument,
  type BehaviorRef,
  type FinalOutcome,
  type FsmEditorDocument,
  type FsmState,
  type FsmTransition,
  type FsmTransitionTrigger,
  type SavedProject,
  type TimeUnit,
} from './domain';
import { createId, slugifyId, uniqueId } from './domain/ids';
import { moveItem } from './domain/ordering';
import { verticalLayout } from './domain/layout';
import { catalogFlowKinds, initialFlowKindFilter } from './domain/catalog';
import { ExecutionSettings } from './ExecutionSettings';
import { CatalogPicker } from './CatalogPicker';
import type { CatalogBehavior } from './domain';

type Selection = { type: 'state' | 'transition'; id: string } | null;
const SAMPLE_PROJECT_ID = 'document-fsm-sample';
const edgeTypes = { flow: FlowEdge };

export interface EmbeddedEditor {
  catalog: CatalogBehavior[];
  /** Kind of the open flow; preselects the bean filter of the transition pickers. */
  flowKind?: string;
  initialDocument: FsmEditorDocument;
  onDocumentChange: (document: FsmEditorDocument) => void;
  /** Locks the flow logic: states, transitions and settings. */
  readOnly: boolean;
  /** Whether states can still be moved and arranged; defaults to `!readOnly`. */
  movable?: boolean;
}

export function App({ embedded }: { embedded?: EmbeddedEditor }) {
  const [document, setDocument] = useState<FsmEditorDocument>(() => embedded?.initialDocument ?? loadEditorDocument() ?? withStateColors(sampleDocument));
  const [currentProjectId, setCurrentProjectId] = useState(() => {
    if (embedded) return '';
    const savedProjectId = loadCurrentProjectId();

    if (savedProjectId) {
      return savedProjectId;
    }

    const loadedDocument = loadEditorDocument();
    return loadedDocument ? createProjectId(loadedDocument) : SAMPLE_PROJECT_ID;
  });
  const [recentProjects, setRecentProjects] = useState<SavedProject[]>([]);
  const [selection, setSelection] = useState<Selection>(null);
  const [flowKindFilter, setFlowKindFilter] = useState(() => initialFlowKindFilter(embedded?.catalog ?? [], embedded?.flowKind));
  const [nodeSizes, setNodeSizes] = useState(() => new Map<string, { width: number; height: number }>());
  const [status, setStatus] = useState('Ready');
  const [expanded, setExpanded] = useState(false);
  const [inspectorHidden, setInspectorHidden] = useState(false);
  const [miniMapHidden, setMiniMapHidden] = useState(false);
  const [fitRequested, setFitRequested] = useState(false);
  const shellRef = useRef<HTMLDivElement>(null);
  const importInputRef = useRef<HTMLInputElement>(null);
  const { fitView, getNode, getZoom, setCenter } = useReactFlow();
  const validationIssues = useMemo(() => validateEditorDocument(document, Boolean(embedded)), [document, Boolean(embedded)]);
  const onDocumentChange = embedded?.onDocumentChange;
  const readOnly = embedded?.readOnly ?? false;
  const movable = embedded?.movable ?? !readOnly;

  useLayoutEffect(() => { onDocumentChange?.(document); }, [document, onDocumentChange]);

  useEffect(() => {
    if (embedded) return;
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
    if (embedded) return;
    void loadRecentProjects(setRecentProjects);
  }, []);

  // React Flow applies new node positions in its own effects, which run before this one.
  useEffect(() => {
    if (!fitRequested) return;
    setFitRequested(false);
    void fitView({ duration: 300, maxZoom: 1 });
  }, [fitRequested, fitView]);

  // Esc leaves the expanded canvas; the browser handles Esc itself while in real fullscreen.
  useEffect(() => {
    if (!expanded) return;
    const page = window.document;
    const onFullscreenChange = () => { if (!page.fullscreenElement) setExpanded(false); };
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape' && !page.fullscreenElement) setExpanded(false); };
    page.addEventListener('fullscreenchange', onFullscreenChange);
    page.addEventListener('keydown', onKeyDown);
    return () => {
      page.removeEventListener('fullscreenchange', onFullscreenChange);
      page.removeEventListener('keydown', onKeyDown);
    };
  }, [expanded]);

  const stateColors = useMemo(() => new Map(document.states.map((state) =>
    [state.id, GRAPH_PALETTE.find((color) => color.ink === state.color) ?? graphColor(state.id)])), [document.states]);
  const eventColors = useMemo(() => colorsForIds(document.events.map((event) => event.id)), [document.events]);
  const nodes = useMemo<Node[]>(
    () =>
      document.states.map((state) => ({
        id: state.id,
        selected: selection?.type === 'state' && selection.id === state.id,
        position: state.position,
        measured: nodeSizes.get(state.id),
        style: colorVariables(stateColors.get(state.id)!),
        data: {
          label: <>
            {state.label === document.codegen.initialState && <span className="node-start"><Play size={11} aria-hidden /> Start</span>}
            <span className="node-name" title={state.label}>{state.label}</span>
            {state.final && <span className="node-final">Final · {state.final.toLowerCase()}</span>}
          </>,
        },
        className: selection?.type === 'state' && selection.id === state.id ? 'selected-node' : undefined,
      })),
    [document.states, document.codegen.initialState, selection, nodeSizes, stateColors],
  );

  const edges = useMemo<Edge[]>(
    () =>
      document.transitions.map((transition) => ({
        id: transition.id,
        type: 'flow',
        selected: selection?.type === 'transition' && selection.id === transition.id,
        source: transition.from,
        target: transition.to,
        label: selection?.type === 'transition' && selection.id === transition.id
          ? transitionLabel(transition)
          : transition.trigger.kind === 'event' ? transition.trigger.event : 'auto',
        data: {
          onSelect: () => setSelection({ type: 'transition', id: transition.id }),
          automatic: transition.trigger.kind === 'auto',
          color: transition.trigger.kind === 'event' ? eventColors.get(transition.trigger.event) ?? graphColor(transition.trigger.event) : AUTO_COLOR,
        },
        markerEnd: { type: MarkerType.ArrowClosed,
          color: transition.trigger.kind === 'event' ? (eventColors.get(transition.trigger.event) ?? graphColor(transition.trigger.event)).ink : AUTO_COLOR.ink },
        className: selection?.type === 'transition' && selection.id === transition.id ? 'selected-edge' : undefined,
      })),
    [document.transitions, selection, eventColors],
  );

  const selectedState = selection?.type === 'state' ? document.states.find((state) => state.id === selection.id) : undefined;
  const selectedTransition =
    selection?.type === 'transition' ? document.transitions.find((transition) => transition.id === selection.id) : undefined;

  const onNodesChange = useCallback((changes: NodeChange[]) => {
    // React Flow needs measured sizes for the minimap, including in read-only mode.
    setNodeSizes((current) => {
      let next = current;
      for (const change of changes) {
        if (change.type !== 'dimensions' || !change.dimensions) continue;
        const previous = current.get(change.id);
        if (previous?.width === change.dimensions.width && previous?.height === change.dimensions.height) continue;
        if (next === current) next = new Map(current);
        next.set(change.id, change.dimensions);
      }
      return next;
    });
    if (!movable) return;
    // A read-only flow keeps its logic: only moves reach the document.
    const applied = readOnly ? changes.filter((change) => change.type === 'position') : changes;
    setSelection((current) => current?.type === 'state' && applied.some((change) => change.type === 'remove' && change.id === current.id)
      ? null : current);
    setDocument((current) => applyNodeChangesToDocument(current, applied));
  }, [movable, readOnly]);

  const onEdgesChange = useCallback((changes: EdgeChange[]) => {
    if (readOnly) return;
    setSelection((current) => current?.type === 'transition' && changes.some((change) => change.type === 'remove' && change.id === current.id)
      ? null : current);
    setDocument((current) => applyEdgeChangesToDocument(current, changes));
  }, [readOnly]);

  const onConnect = useCallback((connection: Connection) => {
    if (readOnly) return;
    const source = connection.source;
    const target = connection.target;

    if (!source || !target) {
      return;
    }

    if (hasTransition(document, newTransition(source, target, { kind: 'auto' }))) {
      setStatus('Auto transition already exists');
      return;
    }

    setDocument((current) => addAutoTransition(current, source, target));
    setStatus('Auto transition added');
  }, [document, readOnly]);

  const addTransition = (from: string, to: string, trigger: FsmTransitionTrigger) => {
    const transition = newTransition(from, to, trigger);
    if (hasTransition(document, transition)) {
      setStatus('This transition already exists');
      return;
    }
    setDocument((current) => ({ ...current, transitions: [...current.transitions, transition] }));
    setStatus('Transition added');
  };

  const toggleExpanded = () => {
    const page = window.document;
    if (expanded) {
      if (page.fullscreenElement) void page.exitFullscreen().catch(() => undefined);
      setExpanded(false);
      return;
    }
    setExpanded(true);
    // An iframe without allow="fullscreen" refuses; the canvas then fills the frame instead.
    if (page.fullscreenEnabled) void shellRef.current?.requestFullscreen().catch(() => undefined);
  };

  const focusState = (id: string) => {
    setSelection({ type: 'state', id });
    const node = getNode(id);
    if (!node) return;
    void setCenter(node.position.x + (node.measured?.width ?? 0) / 2, node.position.y + (node.measured?.height ?? 0) / 2,
      { zoom: Math.max(getZoom(), 1), duration: 300 });
  };

  const focusSelection = () => {
    if (selectedState) focusState(selectedState.id);
    else if (selectedTransition) {
      void fitView({ nodes: [{ id: selectedTransition.from }, { id: selectedTransition.to }], duration: 300, maxZoom: 1.2, padding: 0.3 });
    }
  };

  const arrangeVertically = () => {
    if (!window.confirm('Arrange all states top to bottom? Current positions will be replaced.')) return;
    setDocument((current) => {
      const layout = verticalLayout(current, nodeSizes);
      return { ...current, states: current.states.map((state) => ({ ...state, position: layout.get(state.id)! })) };
    });
    setStatus('States arranged top to bottom');
    setFitRequested(true);
  };

  const addState = () => {
    setDocument((current) => {
      const existingIds = new Set(current.states.map((state) => state.id));
      const label = `STATE_${current.states.length + 1}`;
      const id = uniqueId(slugifyId(label, 'state'), existingIds);
      const state: FsmState = {
        id,
        label,
        color: randomStateColor(current.states.map((candidate) => candidate.color)),
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

  const deleteBehavior = (kind: 'conditions' | 'actions', index: number) => {
    setDocument((current) => deleteBehaviorAtIndex(current, kind, index));
    setStatus(kind === 'conditions' ? 'Guard deleted' : 'Action deleted');
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
        setDocument(withStateColors(importedDocument));
        setSelection(null);
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

  const connectBackend = () => {
    const url = window.prompt('Backend FSM admin URL, for example https://orders.example.com/fsm-admin');
    if (url?.trim()) window.location.search = new URLSearchParams({ backend: url.trim() }).toString();
  };

  const createNewFlow = () => {
    const nextDocument = createEmptyDocument();
    setDocument(withStateColors(nextDocument));
    setCurrentProjectId(createProjectId(nextDocument));
    setSelection(null);
    setStatus('New flow created');
  };

  const openRecentProject = async (projectId: string) => {
    if (!projectId) {
      return;
    }

    if (projectId === SAMPLE_PROJECT_ID) {
      setDocument(withStateColors(sampleDocument));
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

      setDocument(withStateColors(sampleDocument));
      setCurrentProjectId(SAMPLE_PROJECT_ID);
      setSelection(null);
      setStatus('Deleted project and opened example Document FSM');
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Failed to delete project');
    }
  };

  return (
    <div ref={shellRef} className={`app-shell${expanded ? ' expanded' : ''}`}>
      <header className="toolbar">
        <div className="toolbar-title">
          <GitBranchPlus size={22} aria-hidden />
          <div>
            <strong>FSM Visual Editor</strong>
            <span className={/error|failed|unavailable|conflict|invalid|unsupported/i.test(status) ? 'status-error' : undefined}>{status}</span>
          </div>
        </div>
        <div className="toolbar-actions">
          {!embedded && <>
          <div className="toolbar-group">
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
          <button type="button" className="text-button primary" onClick={createNewFlow}>
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
          <button type="button" className="icon-button" onClick={connectBackend}
            title="Connect to a backend" aria-label="Connect to a backend">
            <Plug size={18} />
          </button>
          </div>
          <span className="toolbar-divider" aria-hidden />
          </>}
          <div className="toolbar-group">
          <button type="button" className="text-button" disabled={readOnly} onClick={addState} title="Add state" aria-label="Add state">
            <Plus size={18} />
            Add state
          </button>
          <button
            type="button"
            className="icon-button"
            onClick={() => importInputRef.current?.click()}
            title="Import JSON"
            aria-label="Import JSON"
            disabled={readOnly}
          >
            <Upload size={18} />
          </button>
          <button type="button" className="icon-button" onClick={exportEditorJson} title="Export editor JSON" aria-label="Export editor JSON">
            <FileJson size={18} />
          </button>
          </div>
          {!embedded && <>
          <span className="toolbar-divider" aria-hidden />
          <div className="toolbar-group">
          <button type="button" className="text-button" onClick={exportJava} title="Generate Java class"
            disabled={validationIssues.some((issue) => issue.severity === 'error')}>
            JAVA
          </button>
          <button type="button" className="text-button" onClick={exportKotlin} title="Generate Kotlin class"
            disabled={validationIssues.some((issue) => issue.severity === 'error')}>
            KT
          </button>
          </div>
          </>}
          <span className="toolbar-divider" aria-hidden />
          <div className="toolbar-group">
          <select className="state-jump" aria-label="Go to state" title="Go to state" value=""
            onChange={(event) => { if (event.target.value) focusState(event.target.value); }}>
            <option value="">Go to state…</option>
            {[...document.states].sort((left, right) => left.label.localeCompare(right.label))
              .map((state) => <option key={state.id} value={state.id}>{state.label}</option>)}
          </select>
          <button type="button" className="icon-button" onClick={focusSelection} disabled={!selectedState && !selectedTransition}
            title="Show selected" aria-label="Show selected">
            <LocateFixed size={18} />
          </button>
          <button type="button" className="icon-button" onClick={arrangeVertically} disabled={!movable || document.states.length === 0}
            title="Arrange top to bottom" aria-label="Arrange top to bottom">
            <Network size={18} />
          </button>
          <button type="button" className="icon-button" onClick={() => setInspectorHidden((hidden) => !hidden)}
            title={inspectorHidden ? 'Show side panel' : 'Hide side panel'} aria-label={inspectorHidden ? 'Show side panel' : 'Hide side panel'}>
            {inspectorHidden ? <PanelRightOpen size={18} /> : <PanelRightClose size={18} />}
          </button>
          <button type="button" className="icon-button" onClick={toggleExpanded} aria-pressed={expanded}
            title={expanded ? 'Exit full screen (Esc)' : 'Full screen'} aria-label={expanded ? 'Exit full screen' : 'Full screen'}>
            {expanded ? <Minimize2 size={18} /> : <Maximize2 size={18} />}
          </button>
          </div>
          <span className="toolbar-divider" aria-hidden />
          <div className="toolbar-group">
          <button
            type="button"
            className="icon-button danger"
            onClick={deleteSelection}
            title="Delete selected"
            aria-label="Delete selected"
            disabled={readOnly || !selection}
          >
            <Trash2 size={18} />
          </button>
          </div>
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

      <main className={`workspace${inspectorHidden ? ' inspector-hidden' : ''}`}>
        <section className="canvas" aria-label="FSM graph">
          <ReactFlow
            nodesDraggable={movable}
            nodesConnectable={!readOnly}
            deleteKeyCode={readOnly ? null : 'Backspace'}
            nodes={nodes}
            edges={edges}
            edgeTypes={edgeTypes}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={(_, node) => setSelection({ type: 'state', id: node.id })}
            onEdgeClick={(_, edge) => setSelection({ type: 'transition', id: edge.id })}
            onPaneClick={() => setSelection(null)}
            fitView
            fitViewOptions={{ maxZoom: 1 }}
            defaultEdgeOptions={{ type: 'flow' }}
          >
            <Background gap={24} size={1} color="var(--grid-dot)" />
            {!miniMapHidden && <MiniMap pannable zoomable onClick={(_, position) => void setCenter(position.x, position.y, { zoom: getZoom(), duration: 300 })} nodeColor={(node) => (stateColors.get(node.id) ?? graphColor(node.id)).fill}
              nodeStrokeColor={(node) => (stateColors.get(node.id) ?? graphColor(node.id)).ink} nodeStrokeWidth={3}
              maskColor="rgba(51, 65, 85, 0.15)" />}
            <Controls>
              <ControlButton onClick={() => setMiniMapHidden((hidden) => !hidden)} aria-pressed={!miniMapHidden}
                title={miniMapHidden ? 'Show minimap' : 'Hide minimap'} aria-label={miniMapHidden ? 'Show minimap' : 'Hide minimap'}>
                <MapIcon />
              </ControlButton>
            </Controls>
          </ReactFlow>
        </section>

        <aside className="inspector" hidden={inspectorHidden}>
          <fieldset disabled={readOnly} className="editor-fields">
          <ProjectPanel document={document} setDocument={setDocument} runtime={Boolean(embedded)} />
          <EventPanel document={document} setDocument={setDocument} addEvent={addEvent} deleteEvent={deleteEvent} />
          {embedded && <ExecutionSettings catalog={embedded.catalog} document={document} setDocument={setDocument} />}
          {!embedded && <BehaviorPanel document={document} setDocument={setDocument} addBehavior={addBehavior} deleteBehavior={deleteBehavior} />}
          {!selectedState && !selectedTransition && <section className="panel empty-state">
            <MousePointerClick size={18} aria-hidden />
            <span>Select a state or transition on the canvas to edit it.</span>
          </section>}
          {selectedState && <StateInspector state={selectedState} setDocument={setDocument} runtime={Boolean(embedded)} />}
          {selectedTransition && <TransitionInspector transition={selectedTransition} document={document} setDocument={setDocument}
            catalog={embedded?.catalog} flowKind={flowKindFilter} setFlowKind={setFlowKindFilter} />}
          </fieldset>
          {selectedState && <ConnectionsPanel key={selectedState.id} state={selectedState} document={document} readOnly={readOnly}
            onGoToState={focusState} onSelectTransition={(id) => setSelection({ type: 'transition', id })}
            onAddTransition={addTransition} />}
          <ValidationPanel issues={validationIssues} document={document} onSelectTransition={(id) => setSelection({ type: 'transition', id })} />
        </aside>
      </main>
    </div>
  );
}

function ProjectPanel({
  document,
  setDocument,
  runtime = false,
}: {
  document: FsmEditorDocument;
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
  runtime?: boolean;
}) {
  return (
    <Panel title="Project" className="project-panel" icon={<SlidersHorizontal size={15} aria-hidden />} collapsible defaultOpen>
      <label>
        Name
        <input value={document.name} onChange={(event) => setDocument((current) => ({ ...current, name: event.target.value }))} />
      </label>
      {!runtime && <><label>
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
      </>}
      <div className="field-row">
        {!runtime && <label>
          Event
          <input value={document.codegen.eventType} onChange={(event) => updateCodegen(setDocument, 'eventType', event.target.value)} />
        </label>}
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
      {!runtime && <label>
        Code style
        <select value={document.codegen.style} onChange={(event) => updateCodegen(setDocument, 'style', event.target.value)}>
          <option value="fluent">Fluent chain</option>
          <option value="builder">Builder add calls</option>
        </select>
      </label>}
      {runtime ? <div className="field-row">
        {/* A flow version states its limits from 1; an empty field leaves the application default. */}
        <label>
          Automatic transition limit
          <input type="number" min="1" max="2147483647" placeholder="Default" value={document.maxImmediateAutoTransitions || ''}
            onChange={(event) => setDocument((current) => ({ ...current, maxImmediateAutoTransitions: Number(event.target.value) }))} />
        </label>
        <label>
          Max iterations
          <input type="number" min="1" max="2147483647" placeholder="Default" value={document.execution?.maxIterations ?? ''}
            onChange={(event) => setDocument((current) => {
              const { maxIterations: _previous, ...execution } = current.execution ?? { stateListeners: [], completionListeners: [] };
              return { ...current, execution: event.target.value ? { ...execution, maxIterations: Number(event.target.value) } : execution };
            })} />
        </label>
      </div> : <label>
        Automatic transition limit
        <input type="number" min="0" max="2147483647" value={document.maxImmediateAutoTransitions ?? 0}
          onChange={(event) => setDocument((current) => ({ ...current, maxImmediateAutoTransitions: Number(event.target.value) }))} />
      </label>}
      <label className="toggle-row">
        <input
          type="checkbox"
          checked={document.autoTransitionEnabled}
          onChange={(event) => setDocument((current) => ({ ...current, autoTransitionEnabled: event.target.checked }))}
        />
        Auto transitions
      </label>
    </Panel>
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
    <Panel title="Events" className="events-panel" icon={<Zap size={15} aria-hidden />} count={document.events.length} collapsible defaultOpen
      actions={<button type="button" className="small-button" onClick={addEvent} title="Add event" aria-label="Add event">
        <Plus size={14} />
      </button>}>
      <EventList events={document.events} setDocument={setDocument} deleteEvent={deleteEvent} />
    </Panel>
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
  const colors = colorsForIds(events.map((event) => event.id));
  return (
    <div className="behavior-list">
      {events.length === 0 && <span className="muted">No events</span>}
      {events.map((eventRef, index) => (
        <div className="behavior-row event-row" style={colorVariables(colors.get(eventRef.id)!)} key={`event-${index}`}>
          <span className="event-swatch" aria-hidden><CircleDot size={14} /></span>
          <input
            aria-label={`Event ID ${index + 1}`}
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
  deleteBehavior,
}: {
  document: FsmEditorDocument;
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
  addBehavior: (kind: 'conditions' | 'actions') => void;
  deleteBehavior: (kind: 'conditions' | 'actions', index: number) => void;
}) {
  return (
    <Panel title="Behavior" className="behavior-panel" icon={<Boxes size={15} aria-hidden />} collapsible
      count={document.behaviors.conditions.length + document.behaviors.actions.length}>
      <BehaviorList
        title="Guards"
        kind="conditions"
        behaviors={document.behaviors.conditions}
        setDocument={setDocument}
        addBehavior={addBehavior}
        deleteBehavior={deleteBehavior}
      />
      <BehaviorList
        title="Actions"
        kind="actions"
        behaviors={document.behaviors.actions}
        setDocument={setDocument}
        addBehavior={addBehavior}
        deleteBehavior={deleteBehavior}
      />
    </Panel>
  );
}

function BehaviorList({
  title,
  kind,
  behaviors,
  setDocument,
  addBehavior,
  deleteBehavior,
}: {
  title: string;
  kind: 'conditions' | 'actions';
  behaviors: BehaviorRef[];
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
  addBehavior: (kind: 'conditions' | 'actions') => void;
  deleteBehavior: (kind: 'conditions' | 'actions', index: number) => void;
}) {
  return (
    <div className="behavior-list">
      <div className="section-head">
        <span>{title}</span>
        <button type="button" className="small-button" aria-label={kind === 'conditions' ? 'Add guard' : 'Add action'} onClick={() => addBehavior(kind)}>
          <Plus size={14} />
        </button>
      </div>
      {behaviors.length === 0 && <span className="muted">None</span>}
      {behaviors.map((behavior, index) => (
        <div className="behavior-row" key={`${kind}-${index}`}>
          <input
            aria-label={`${kind === 'conditions' ? 'Guard' : 'Action'} ID ${index + 1}`}
            value={behavior.id}
            onChange={(event) =>
              setDocument((current) => renameBehaviorAtIndex(current, kind, index, event.target.value))
            }
          />
          <button
            type="button"
            className="small-button danger"
            onClick={() => deleteBehavior(kind, index)}
            title={`Delete ${kind === 'conditions' ? 'guard' : 'action'}`}
            aria-label={`Delete ${kind === 'conditions' ? 'guard' : 'action'} ${behavior.id}`}
          >
            <Trash2 size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}

function StateInspector({
  state,
  setDocument,
  runtime,
}: {
  state: FsmState;
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
  runtime: boolean;
}) {
  return (
    <Panel title="State" icon={<Box size={15} aria-hidden />} className="selected-panel">
      <label>
        Color
        <span className="state-color-field">
          <span className="event-swatch" aria-hidden
            style={colorVariables(GRAPH_PALETTE.find((color) => color.ink === state.color) ?? graphColor(state.id))} />
          <select aria-label="State color" value={state.color ?? graphColor(state.id).ink}
            onChange={(event) => setDocument((current) => ({ ...current,
              states: current.states.map((candidate) => candidate.id === state.id
                ? { ...candidate, color: event.target.value } : candidate),
            }))}>
            {GRAPH_PALETTE.map((color) => <option key={color.ink} value={color.ink}>{color.name}</option>)}
          </select>
        </span>
      </label>
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
      {runtime && <label>
        Final state
        <select value={state.final ?? ''}
          onChange={(event) => setDocument((current) => ({ ...current,
            states: current.states.map((candidate) => {
              if (candidate.id !== state.id) return candidate;
              const { final: _previous, ...unmarked } = candidate;
              return event.target.value ? { ...unmarked, final: event.target.value as FinalOutcome } : unmarked;
            }),
          }))}>
          <option value="">Not final</option>
          {FINAL_OUTCOMES.map((outcome) => <option key={outcome} value={outcome}>
            {outcome.charAt(0) + outcome.slice(1).toLowerCase()}
          </option>)}
        </select>
      </label>}
    </Panel>
  );
}

function ConnectionsPanel({
  state,
  document,
  readOnly,
  onGoToState,
  onSelectTransition,
  onAddTransition,
}: {
  state: FsmState;
  document: FsmEditorDocument;
  readOnly: boolean;
  onGoToState: (id: string) => void;
  onSelectTransition: (id: string) => void;
  onAddTransition: (from: string, to: string, trigger: FsmTransitionTrigger) => void;
}) {
  const [chosenTarget, setTarget] = useState(() => document.states.find((candidate) => candidate.id !== state.id)?.id ?? state.id);
  const [chosenTrigger, setTrigger] = useState('auto');
  // A state or event chosen here may be deleted or renamed meanwhile; fall back to what the selects show.
  const target = document.states.some((candidate) => candidate.id === chosenTarget) ? chosenTarget : document.states[0]?.id ?? state.id;
  const trigger = document.events.some((eventRef) => `event:${eventRef.id}` === chosenTrigger) ? chosenTrigger : 'auto';
  const label = (id: string) => document.states.find((candidate) => candidate.id === id)?.label ?? id;
  const triggerLabel = (transition: FsmTransition) => transition.trigger.kind === 'event' ? transition.trigger.event : 'auto';
  const outgoing = document.transitions.filter((transition) => transition.from === state.id);
  const incoming = document.transitions.filter((transition) => transition.to === state.id);
  const row = (transition: FsmTransition, other: string, direction: 'from' | 'to') => (
    <li key={transition.id} className="connection-row">
      {direction === 'from' && <><button type="button" className="link-button" onClick={() => onGoToState(other)}
        title={`Go to ${label(other)}`}>{label(other)}</button><span aria-hidden>→</span></>}
      <button type="button" className="trigger-chip" onClick={() => onSelectTransition(transition.id)}
        title="Edit transition">{triggerLabel(transition)}</button>
      {direction === 'to' && <><span aria-hidden>→</span><button type="button" className="link-button"
        onClick={() => onGoToState(other)} title={`Go to ${label(other)}`}>{label(other)}</button></>}
    </li>
  );

  return (
    <Panel title="Connections" icon={<GitBranch size={15} aria-hidden />} className="connections-panel">
      <section aria-label="Outgoing transitions">
        <h3 className="connection-heading">Outgoing ({outgoing.length})</h3>
        {outgoing.length ? <ul className="connection-list">{outgoing.map((transition) => row(transition, transition.to, 'to'))}</ul>
          : <p className="muted">None</p>}
      </section>
      <section aria-label="Incoming transitions">
        <h3 className="connection-heading">Incoming ({incoming.length})</h3>
        {incoming.length ? <ul className="connection-list">{incoming.map((transition) => row(transition, transition.from, 'from'))}</ul>
          : <p className="muted">None</p>}
      </section>
      <fieldset disabled={readOnly} className="editor-fields add-transition">
        <legend>Add transition</legend>
        <div className="field-row">
          <label>
            To
            <select value={target} onChange={(event) => setTarget(event.target.value)}>
              {document.states.map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.label}</option>)}
            </select>
          </label>
          <label>
            Trigger
            <select value={trigger} onChange={(event) => setTrigger(event.target.value)}>
              <option value="auto">Auto</option>
              {document.events.map((eventRef) => <option key={eventRef.id} value={`event:${eventRef.id}`}>{eventRef.id}</option>)}
            </select>
          </label>
        </div>
        <button type="button" className="text-button" onClick={() => onAddTransition(state.id, target,
          trigger === 'auto' ? { kind: 'auto' } : { kind: 'event', event: trigger.slice('event:'.length) })}>
          <Plus size={16} />
          Add transition
        </button>
      </fieldset>
    </Panel>
  );
}

function TransitionInspector({
  transition,
  document,
  setDocument,
  catalog,
  flowKind,
  setFlowKind,
}: {
  transition: FsmTransition;
  document: FsmEditorDocument;
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
  catalog?: CatalogBehavior[];
  flowKind: string;
  setFlowKind: (kind: string) => void;
}) {
  const flowKinds = catalogFlowKinds(catalog ?? []);
  const updateTransition = (patch: Partial<FsmTransition>) => {
    setDocument((current) => ({
      ...current,
      behaviors: catalog ? {
        conditions: [...current.behaviors.conditions, ...catalog.filter((bean) => bean.kind === 'guard'
          && patch.conditions?.includes(bean.id) && !current.behaviors.conditions.some((item) => item.id === bean.id))
          .map((bean) => ({ id: bean.id, label: bean.description }))],
        actions: [...current.behaviors.actions, ...catalog.filter((bean) => bean.kind === 'action'
          && [...(patch.actions ?? []), ...(patch.postActions ?? [])].includes(bean.id)
          && !current.behaviors.actions.some((item) => item.id === bean.id))
          .map((bean) => ({ id: bean.id, label: bean.description }))],
      } : current.behaviors,
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

  const siblings = document.transitions.filter((item) => item.from === transition.from);

  return (
    <Panel title="Transition" icon={<GitBranch size={15} aria-hidden />} className="selected-panel">
      {siblings.length > 1 && <fieldset aria-label="Branch priority" className="check-group">
        <legend>Branch priority (first matching wins)</legend>
        <ol className="priority-list">
          {siblings.map((item, index) =>
            <li key={item.id} className="priority-row">
              <span className="priority-index">{index + 1}</span>
              <span className="priority-label">
                {document.states.find((state) => state.id === item.to)?.label} · {item.trigger.kind === 'event' ? item.trigger.event : 'Eventless'}
              </span>
              <span className="priority-actions">
                {([-1, 1] as const).map((direction) => <button key={direction} type="button" className="small-button"
                  aria-label={`Move branch ${index + 1} ${direction === -1 ? 'up' : 'down'}`}
                  disabled={index + direction < 0 || index + direction >= siblings.length}
                  onClick={() => setDocument((current) => {
                    const ordered = moveItem(siblings, index, direction);
                    let position = 0;
                    return { ...current, transitions: current.transitions.map((candidate) =>
                      candidate.from === transition.from ? ordered[position++] : candidate) };
                  })}>{direction === -1 ? <ChevronUp size={14} /> : <ChevronDown size={14} />}</button>)}
              </span>
            </li>)}
        </ol>
      </fieldset>}
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
          onClick={() => eventTrigger && updateTransition({ trigger: { kind: 'event', event: eventTrigger }, autoTransitionEnabled: false })}
          disabled={!eventTrigger}
          title={eventTrigger ? undefined : 'Create an event first'}
        >
          Event
        </button>
        <button
          type="button"
          className={transition.trigger.kind === 'auto' ? 'active' : ''}
          onClick={() => {
            if (transition.trigger.kind !== 'auto') updateTransition({ trigger: { kind: 'auto' }, autoTransitionEnabled: true });
          }}
        >
          Auto
        </button>
      </div>
      {transition.trigger.kind === 'event' && (
        <label>
          Event
          <select
            value={transition.trigger.event}
            onChange={(event) => updateTransition({ trigger: { kind: 'event', event: event.target.value }, autoTransitionEnabled: false })}
          >
            {document.events.map((eventRef) => (
              <option key={eventRef.id} value={eventRef.id}>
                {eventRef.id}
              </option>
            ))}
          </select>
        </label>
      )}
      {flowKinds.length > 0 && <label>
        Bean kind
        <select value={flowKind} onChange={(event) => setFlowKind(event.target.value)}>
          <option value="">All kinds</option>
          {flowKinds.map((kind) => <option key={kind} value={kind}>{kind}</option>)}
        </select>
      </label>}
      <BehaviorPicker
        title="Guards"
        flowKind={flowKind}
        catalog={catalog?.filter((bean) => bean.kind === 'guard')}
        options={document.behaviors.conditions}
        selected={transition.conditions}
        onChange={(conditions) => updateTransition({ conditions })}
      />
      {transition.trigger.kind === 'auto' && <label className="toggle-row">
        <input type="checkbox" checked={transition.autoTransitionEnabled ?? false}
          onChange={(event) => updateTransition({ autoTransitionEnabled: event.target.checked })} />
        Run this transition automatically even when global auto transitions are disabled
      </label>}
      <BehaviorPicker
        title="Actions"
        flowKind={flowKind}
        catalog={catalog?.filter((bean) => bean.kind === 'action')}
        options={document.behaviors.actions}
        selected={transition.actions}
        onChange={(actions) => updateTransition({ actions })}
      />
      <BehaviorPicker
        title="Post actions"
        flowKind={flowKind}
        catalog={catalog?.filter((bean) => bean.kind === 'action')}
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
      <p className="muted">Timeout waits inside the current call. Use a queued notification for a background step.</p>
    </Panel>
  );
}

function BehaviorPicker({
  title,
  options,
  selected,
  onChange,
  catalog,
  flowKind,
}: {
  title: string;
  options: BehaviorRef[];
  selected: string[];
  onChange: (next: string[]) => void;
  catalog?: CatalogBehavior[];
  flowKind: string;
}) {
  if (catalog) return <CatalogPicker title={title} options={catalog} selected={selected} onChange={onChange} flowKind={flowKind} />;
  return (
    <fieldset className="check-group">
      <legend>{title}</legend>
      {options.length === 0 && <span className="muted">None</span>}
      {selected.length > 0 && <ol aria-label={`${title} execution order`}>
        {selected.map((id, index) => <li key={id}>{id}
          {([-1, 1] as const).map((direction) => <button type="button" key={direction}
            aria-label={`Move ${title} ${id} ${direction === -1 ? 'up' : 'down'}`}
            disabled={index + direction < 0 || index + direction >= selected.length}
            onClick={() => onChange(moveItem(selected, index, direction))}>{direction === -1 ? 'Up' : 'Down'}</button>)}
        </li>)}
      </ol>}
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

function ValidationPanel({ issues, document, onSelectTransition }: {
  issues: ReturnType<typeof validateEditorDocument>;
  document: FsmEditorDocument;
  onSelectTransition: (id: string) => void;
}) {
  const errorCount = issues.filter((issue) => issue.severity === 'error').length;
  const warningCount = issues.length - errorCount;

  return (
    <Panel title="Validation" icon={<AlertCircle size={15} aria-hidden />} className="validation-panel"
      actions={<span className={`validation-badge ${errorCount > 0 ? 'has-errors' : warningCount > 0 ? 'has-warnings' : ''}`.trim()}>
        {errorCount > 0 ? errorCount : warningCount > 0 ? warningCount : 'OK'}</span>}>
      <p className={errorCount > 0 ? 'error-text' : warningCount > 0 ? 'warning-text' : 'ok-text'}>
        {errorCount > 0 ? `${errorCount} error(s)` : warningCount > 0 ? `${warningCount} warning(s)` : 'No issues'}
      </p>
      {issues.map((issue, index) => {
        const transitionIndex = /^transitions\[(\d+)]/.exec(issue.path);
        const transition = transitionIndex ? document.transitions[Number(transitionIndex[1])] : undefined;
        const source = transition && document.states.find((state) => state.id === transition.from)?.label;
        const target = transition && document.states.find((state) => state.id === transition.to)?.label;
        const disabledAuto = issue.path === `transitions[${transitionIndex?.[1]}].trigger`
          && transition?.trigger.kind === 'auto' && !document.autoTransitionEnabled && !transition.autoTransitionEnabled;
        return <div key={`${issue.path}-${index}`} className={issue.severity === 'error' ? 'error-text validation-issue' : 'warning-text validation-issue'}>
          <p>{transition ? `${source ?? transition.from} → ${target ?? transition.to}: ` : `${issue.path}: `}{issue.message}</p>
          {disabledAuto && <p>If this arrow is intentional, enable automatic execution on this transition or “Auto transitions” in Project. Otherwise delete it or change its trigger to Event.</p>}
          {transition && <button type="button" className="small-button" onClick={() => onSelectTransition(transition.id)}>Show transition</button>}
        </div>;
      })}
    </Panel>
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

export function deleteBehaviorAtIndex(
  document: FsmEditorDocument,
  kind: 'conditions' | 'actions',
  index: number,
): FsmEditorDocument {
  const behaviorId = document.behaviors[kind][index]?.id;

  if (behaviorId === undefined) {
    return document;
  }

  return {
    ...document,
    behaviors: {
      ...document.behaviors,
      [kind]: document.behaviors[kind].filter((_, behaviorIndex) => behaviorIndex !== index),
    },
    transitions: document.transitions.map((transition) => {
      if (kind === 'conditions') {
        return {
          ...transition,
          conditions: transition.conditions.filter((id) => id !== behaviorId),
        };
      }

      return {
        ...transition,
        actions: transition.actions.filter((id) => id !== behaviorId),
        postActions: transition.postActions.filter((id) => id !== behaviorId),
      };
    }),
  };
}

export function newTransition(from: string, to: string, trigger: FsmTransitionTrigger): FsmTransition {
  return {
    id: createId('transition'),
    from,
    to,
    trigger,
    autoTransitionEnabled: trigger.kind === 'auto',
    conditions: [],
    actions: [],
    postActions: [],
  };
}

export function hasTransition(document: FsmEditorDocument, transition: FsmTransition): boolean {
  return document.transitions.some((candidate) => transitionDuplicateKey(candidate) === transitionDuplicateKey(transition));
}

export function addAutoTransition(document: FsmEditorDocument, source: string, target: string): FsmEditorDocument {
  const transition = newTransition(source, target, { kind: 'auto' });
  return hasTransition(document, transition) ? document : { ...document, transitions: [...document.transitions, transition] };
}

function nextEventId(document: FsmEditorDocument): string {
  return uniqueId('EVENT', new Set(document.events.map((eventRef) => eventRef.id)));
}

export function transitionLabel(transition: FsmTransition): string {
  const parts = [transition.trigger.kind === 'event' ? transition.trigger.event : 'auto'];

  transition.conditions.forEach((condition) => parts.push(`[${condition}]`));

  if (transition.actions.length > 0) {
    parts.push(`/ ${transition.actions.join(', ')}`);
  }

  if (transition.postActions.length > 0) {
    parts.push(`post: ${transition.postActions.join(', ')}`);
  }

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
