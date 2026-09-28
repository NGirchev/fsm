import { useCallback, useEffect, useRef, useState } from 'react';
import { App } from './App';
import { normalizeEditorDocument, type CatalogBehavior, type FsmEditorDocument } from './domain';
import { fromFlowDefinition, toFlowDefinition, type FlowDefinition } from './domain/flowDefinition';

const channel = 'fsm-editor/v1';

function parentOrigin(): string | null {
  if (window.parent === window) return null;
  const value = new URLSearchParams(window.location.search).get('parentOrigin');
  if (!value) return null;
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) && url.origin === value ? value : null;
  } catch { return null; }
}

interface HostedDocument {
  session: string;
  document: FsmEditorDocument;
  catalog: CatalogBehavior[];
}

export function EditorHost() {
  const [origin] = useState(parentOrigin);
  const [hosted, setHosted] = useState<HostedDocument>();
  const [readOnly, setReadOnly] = useState(true);
  const [error, setError] = useState('Waiting for a document from the host…');
  const current = useRef<HostedDocument>();
  const loaded = useRef(false);

  const send = useCallback((message: object) => {
    if (origin) window.parent.postMessage({ channel, ...message }, origin);
  }, [origin]);

  useEffect(() => {
    if (!origin) return;
    const receive = (event: MessageEvent) => {
      if (event.source !== window.parent || event.origin !== origin || event.data?.channel !== channel) return;
      const message = event.data;
      if (message.type === 'load') {
        try {
          const next = readHostDocument(message);
          current.current = next;
          loaded.current = false;
          setReadOnly(next.readOnly);
          setHosted(next);
        } catch (cause) {
          const detail = cause instanceof Error ? cause.message : 'Invalid host document.';
          setError(detail);
          send({ type: 'error', session: message.session, error: detail });
        }
      } else if (message.session === current.current?.session) {
        if (message.type === 'configure' && typeof message.readOnly === 'boolean') setReadOnly(message.readOnly);
        if (message.type === 'snapshot' && typeof message.requestId === 'string' && current.current) {
          // Freeze editing until the host finishes saving (or reports failure).
          setReadOnly(true);
          try {
            send({ type: 'snapshot', session: message.session, requestId: message.requestId,
              document: current.current.document, definition: toFlowDefinition(current.current.document) });
          } catch (cause) {
            send({ type: 'snapshot', session: message.session, requestId: message.requestId,
              error: cause instanceof Error ? cause.message : 'Invalid flow.' });
          }
        }
      }
    };
    window.addEventListener('message', receive);
    send({ type: 'ready' });
    return () => window.removeEventListener('message', receive);
  }, [origin, send]);

  const changed = useCallback((document: FsmEditorDocument) => {
    if (!current.current) return;
    current.current.document = document;
    send({ type: loaded.current ? 'change' : 'loaded', session: current.current.session, document });
    loaded.current = true;
  }, [send]);

  if (!origin) return <App />;
  if (!hosted) return <main className="loading-panel"><p role="status">{error}</p></main>;
  return <App key={hosted.session} embedded={{ initialDocument: hosted.document,
    catalog: hosted.catalog, readOnly, onDocumentChange: changed }} />;
}

function readHostDocument(message: Record<string, unknown>): HostedDocument & { readOnly: boolean } {
  if (typeof message.session !== 'string' || !message.session || typeof message.readOnly !== 'boolean') {
    throw new Error('A document session and readOnly flag are required.');
  }
  const document = normalizeEditorDocument(message.document ?? fromFlowDefinition(message.definition as FlowDefinition));
  if (!document) throw new Error('Unsupported editor document.');
  const catalog = message.catalog ?? [];
  if (!Array.isArray(catalog) || !catalog.every(isCatalogBehavior)) {
    throw new Error('Invalid editor catalog.');
  }
  return { session: message.session, document, catalog, readOnly: message.readOnly };
}

function isCatalogBehavior(value: unknown): value is CatalogBehavior {
  if (!value || typeof value !== 'object') return false;
  const item = value as Partial<CatalogBehavior>;
  return typeof item.id === 'string' && typeof item.description === 'string' &&
    ['guard', 'action', 'stateListener', 'completionListener'].includes(item.kind ?? '');
}
