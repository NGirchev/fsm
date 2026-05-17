import { normalizeEditorDocument } from './documentGuards';
import type { FsmEditorDocument } from './types';

export const EDITOR_STORAGE_KEY = 'fsm-editor.current-document';
export const CURRENT_PROJECT_ID_KEY = 'fsm-editor.current-project-id';

export function loadEditorDocument(storage: Pick<Storage, 'getItem'> = window.localStorage): FsmEditorDocument | null {
  try {
    const rawDocument = storage.getItem(EDITOR_STORAGE_KEY);

    if (!rawDocument) {
      return null;
    }

    const parsedDocument = JSON.parse(rawDocument) as unknown;
    return normalizeEditorDocument(parsedDocument);
  } catch {
    return null;
  }
}

export function saveEditorDocument(
  document: FsmEditorDocument,
  storage: Pick<Storage, 'setItem'> = window.localStorage,
): void {
  try {
    storage.setItem(EDITOR_STORAGE_KEY, JSON.stringify(document));
  } catch {
    // Local storage can be unavailable or full. Export still remains available.
  }
}

export function loadCurrentProjectId(storage: Pick<Storage, 'getItem'> = window.localStorage): string | null {
  try {
    return storage.getItem(CURRENT_PROJECT_ID_KEY);
  } catch {
    return null;
  }
}

export function saveCurrentProjectId(projectId: string, storage: Pick<Storage, 'setItem'> = window.localStorage): void {
  try {
    storage.setItem(CURRENT_PROJECT_ID_KEY, projectId);
  } catch {
    // Local storage can be unavailable. Filesystem save still uses the in-memory id.
  }
}
