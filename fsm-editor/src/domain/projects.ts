import { normalizeEditorDocument } from './documentGuards';
import { slugifyId } from './ids';
import type { FsmEditorDocument } from './types';

export interface SavedProject {
  id: string;
  name: string;
  updatedAt: string | null;
}

export interface ProjectEnvelope extends FsmEditorDocument {
  updatedAt?: string;
}

export function createProjectId(document: FsmEditorDocument, now: Date = new Date()): string {
  const timestamp = now.toISOString().replace(/[-:]/g, '').slice(0, 15).replace('T', '-');
  return `${slugifyId(document.name, 'fsm')}-${timestamp}`;
}

export async function listSavedProjects(): Promise<SavedProject[]> {
  const response = await fetch('/api/projects');

  if (!response.ok) {
    throw new Error(`Failed to load projects: HTTP ${response.status}`);
  }

  return (await response.json()) as SavedProject[];
}

export async function loadSavedProject(projectId: string): Promise<FsmEditorDocument> {
  const response = await fetch(`/api/projects/${projectId}`);

  if (!response.ok) {
    throw new Error(`Failed to load project: HTTP ${response.status}`);
  }

  const parsed = (await response.json()) as unknown;

  const document = normalizeEditorDocument(parsed);

  if (!document) {
    throw new Error('Saved project is not an editor document.');
  }

  return document;
}

export async function saveProject(projectId: string, document: FsmEditorDocument): Promise<void> {
  const envelope: ProjectEnvelope = {
    ...document,
    updatedAt: new Date().toISOString(),
  };
  const response = await fetch(`/api/projects/${projectId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(envelope),
  });

  if (!response.ok) {
    throw new Error(`Failed to save project: HTTP ${response.status}`);
  }
}

export async function deleteSavedProject(projectId: string): Promise<void> {
  const response = await fetch(`/api/projects/${projectId}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error(`Failed to delete project: HTTP ${response.status}`);
  }
}
