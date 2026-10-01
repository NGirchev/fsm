import type { CatalogBehavior } from './types';
import type { FlowDefinition } from './flowDefinition';

/** Optional deployment settings from `config.js`, loaded before the editor. */
export interface EditorConfig {
  /** Base URL of the starter API, for example `https://orders.example.com/fsm-admin`. */
  backendUrl?: string;
  /** Replaces the default request function, e.g. to add an Authorization or CSRF header. */
  fetch?: typeof fetch;
}

declare global {
  interface Window {
    fsmEditorConfig?: EditorConfig;
  }
}

export interface FlowRegistration {
  flowKey: string;
  title: string;
}

export type FlowVersionStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

export interface StoredFlowVersion {
  version: number;
  status: FlowVersionStatus;
  definition: FlowDefinition;
}

export type BackendApi = ReturnType<typeof backendApi>;

/** `?backend=` wins over `config.js`; relative URLs resolve against the editor page. */
export function backendUrl(location: Location = window.location, config: EditorConfig | undefined = window.fsmEditorConfig): string | null {
  const value = new URLSearchParams(location.search).get('backend') ?? config?.backendUrl;
  if (!value) return null;
  try {
    const url = new URL(value, location.href);
    return ['http:', 'https:'].includes(url.protocol) ? url.href.replace(/\/+$/, '') : null;
  } catch {
    return null;
  }
}

// Cookies are sent so the backend's own session authentication applies across origins.
const includeCredentials: typeof fetch = (input, init) => fetch(input, { credentials: 'include', ...init });

export function backendApi(baseUrl: string, send: typeof fetch = includeCredentials) {
  const api = `${baseUrl}/api/`;

  async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
    const headers = new Headers();
    if (body !== undefined) headers.set('Content-Type', 'application/json');
    const response = await send(api + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    if (!response.ok) {
      const error = (await response.json().catch(() => null)) as { message?: string } | null;
      throw new Error(error?.message ?? `Request failed: HTTP ${response.status}`);
    }
    return (response.status === 204 ? undefined : await response.json()) as T;
  }

  const versions = (flowKey: string) => `flows/${encodeURIComponent(flowKey)}/versions`;
  return {
    flows: () => request<FlowRegistration[]>('flows'),
    behaviors: (flowKey: string) => request<CatalogBehavior[]>(`flows/${encodeURIComponent(flowKey)}/behaviors`),
    versions: (flowKey: string) => request<StoredFlowVersion[]>(versions(flowKey)),
    create: (flowKey: string, definition?: FlowDefinition) => request<StoredFlowVersion>(versions(flowKey), 'POST', definition),
    save: (flowKey: string, version: number, definition: FlowDefinition) =>
      request<StoredFlowVersion>(`${versions(flowKey)}/${version}`, 'PUT', definition),
    publish: (flowKey: string, version: number) => request<StoredFlowVersion>(`${versions(flowKey)}/${version}/publish`, 'POST'),
    activate: (flowKey: string, version: number) => request<StoredFlowVersion>(`${versions(flowKey)}/${version}/activate`, 'POST'),
    deleteDraft: (flowKey: string, version: number) => request<void>(`${versions(flowKey)}/${version}`, 'DELETE'),
  };
}
