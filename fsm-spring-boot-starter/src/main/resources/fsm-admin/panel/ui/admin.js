const channel = 'fsm-editor/v1';
const element = (id) => document.getElementById(id);
const frame = /** @type {HTMLIFrameElement} */ (element('flow-editor'));
const versionSelect = /** @type {HTMLSelectElement} */ (element('flow-version'));
const flowSelect = /** @type {HTMLSelectElement} */ (element('flow-key'));
/** @typedef {{ version: number, status: string, definition: { editor?: {name?: string}, table: { transitions: Record<string, {event: string | null}[]> } } }} FlowVersion */
// A host can point this at the same editor deployed on GitHub Pages.
const editorUrl = new URL(frame.dataset.editorUrl || './editor/index.html', window.location.href);
editorUrl.searchParams.set('parentOrigin', window.location.origin);
const apiUrl = new URL('./api/', window.location.href);
let registrations = [], registration, versionsUrl;
let versions = [], catalog = [], selected, currentDocument, savedDocument;
let session = '', ready = false, loaded = false, busy = false, dirty = false, versionsLoaded = false;
let flowMessage = 'Loading flows…';
let pendingSnapshot;

async function request(url, method = 'GET', body) {
  const headers = new Headers();
  if (body !== undefined) headers.set('Content-Type', 'application/json');
  if (method !== 'GET') {
    const csrf = /** @type {{token?: string, headerName?: string}} */ (await request(new URL('csrf', apiUrl)));
    if (csrf.token && csrf.headerName) headers.set(csrf.headerName, csrf.token);
  }
  const response = await fetch(url, { method, credentials: 'same-origin', headers,
    body: body === undefined ? undefined : JSON.stringify(body) });
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message ?? `Request failed: HTTP ${response.status}`);
  }
  return response.status === 204 ? undefined : response.json();
}

function send(message) {
  frame.contentWindow?.postMessage({ channel, session, ...message }, editorUrl.origin);
}

function renderFlow() {
  element('flow-key').disabled = busy || dirty || !registration;
  element('flow-message').textContent = flowMessage + (dirty ? ' Unsaved changes.' : '');
  element('flow-version').replaceChildren(...versions.map((version) =>
    new Option(`${version.definition.editor?.name ?? registration.title} · v${version.version} · ${version.status}`, String(version.version))));
  if (selected) element('flow-version').value = String(selected.version);
  const unavailable = busy || !loaded || !selected;
  element('flow-version').disabled = unavailable || dirty;
  element('create-draft').disabled = busy || dirty || !versionsLoaded || Boolean(selected && !loaded);
  element('save-draft').disabled = unavailable || selected?.status !== 'DRAFT' || !dirty;
  element('publish').disabled = unavailable || selected?.status !== 'DRAFT' || dirty;
  element('activate').disabled = unavailable || selected?.status !== 'ARCHIVED' || dirty;
  element('delete-draft').disabled = unavailable || selected?.status !== 'DRAFT';
  element('discard').hidden = !dirty;
  element('discard').disabled = unavailable;
  element('retry').hidden = Boolean(selected && loaded);
  element('retry').disabled = busy;
  if (ready && loaded) send({ type: 'configure', readOnly: busy || selected?.status !== 'DRAFT' });
}

function loadEditor() {
  if (!ready) return;
  frame.hidden = !selected;
  if (!selected) return;
  send({ type: 'load', ...(currentDocument ? { document: currentDocument } : { definition: selected.definition }),
    catalog, readOnly: busy || selected.status !== 'DRAFT' });
}

function openVersion(version) {
  selected = version;
  currentDocument = undefined;
  savedDocument = undefined;
  dirty = false;
  loaded = false;
  session = crypto.randomUUID();
  loadEditor();
  renderFlow();
}

async function reloadVersions() {
  busy = true;
  versionsLoaded = false;
  renderFlow();
  try {
    [versions, catalog] = await Promise.all([request(versionsUrl), request(versionsUrl.replace(/versions$/, 'behaviors'))]);
    versionsLoaded = true;
    const active = versions.find((item) => item.status === 'ACTIVE') ?? versions[0];
    openVersion(active);
    flowMessage = active ? 'Flow loaded. Create a draft to edit.' : 'No versions yet. Create the first draft.';
  } catch (error) { flowMessage = error.message; }
  finally { busy = false; renderFlow(); }
}

function snapshot() {
  return new Promise((resolve, reject) => {
    const requestId = crypto.randomUUID();
    const timeout = setTimeout(() => {
      pendingSnapshot = undefined;
      reject(new Error('Editor did not respond. Retry the operation.'));
    }, 5000);
    pendingSnapshot = { requestId, resolve, reject, timeout };
    send({ type: 'snapshot', requestId });
  });
}

window.addEventListener('message', (event) => {
  if (event.source !== frame.contentWindow || event.origin !== editorUrl.origin || event.data?.channel !== channel) return;
  const message = event.data;
  if (message.type === 'ready') {
    ready = true;
    loaded = false;
    loadEditor();
    renderFlow();
    return;
  }
  if (message.session !== session) return;
  if (message.type === 'loaded' || message.type === 'change') {
    if (!message.document || message.document.formatVersion !== 2) return;
    currentDocument = message.document;
    if (savedDocument === undefined) savedDocument = JSON.stringify(currentDocument);
    dirty = JSON.stringify(currentDocument) !== savedDocument;
    loaded = true;
    renderFlow();
  } else if (message.type === 'snapshot' && pendingSnapshot?.requestId === message.requestId) {
    clearTimeout(pendingSnapshot.timeout);
    if (message.error) pendingSnapshot.reject(new Error(message.error));
    else pendingSnapshot.resolve(message);
    pendingSnapshot = undefined;
  } else if (message.type === 'error') {
    flowMessage = message.error;
    renderFlow();
  }
});
window.addEventListener('beforeunload', (event) => { if (dirty) event.preventDefault(); });

async function mutate(action) {
  if (busy || !versionsLoaded || (selected && !loaded)) return;
  busy = true;
  renderFlow();
  try {
    const captured = !selected || action === 'publish' || action === 'activate' ? undefined : await snapshot();
    const path = action === 'create' ? versionsUrl : `${versionsUrl}/${selected.version}${action === 'publish' ? '/publish' : action === 'activate' ? '/activate' : ''}`;
    const result = /** @type {FlowVersion} */ (await request(path, action === 'save' ? 'PUT' : 'POST', captured?.definition));
    versions = [result, ...versions.filter((item) => item.version !== result.version)]
      .map((item) => (action === 'publish' || action === 'activate') && item.version !== result.version && item.status === 'ACTIVE'
        ? { ...item, status: 'ARCHIVED' } : item).sort((left, right) => right.version - left.version);
    if (action === 'save') {
      selected = result;
      savedDocument = JSON.stringify(captured.document);
      dirty = JSON.stringify(currentDocument) !== savedDocument;
    } else openVersion(result);
    flowMessage = action === 'publish' ? 'Published. New instances use this version.' :
      action === 'activate' ? 'Active flow changed. New instances use this version.' :
      action === 'save' ? 'Draft and layout saved.' : 'Draft created. You can edit the graph.';
  } catch (error) { flowMessage = error.message; }
  finally { busy = false; renderFlow(); }
}

element('create-draft').onclick = () => void mutate('create');
element('save-draft').onclick = () => void mutate('save');
element('publish').onclick = () => void mutate('publish');
element('activate').onclick = () => void mutate('activate');
element('retry').onclick = () => void (registration ? reloadVersions() : initialize());
versionSelect.onchange = () => {
  const version = versions.find((item) => item.version === Number(versionSelect.value));
  if (version) { openVersion(version); flowMessage = `Opened version ${version.version} (${version.status}).`; renderFlow(); }
};
element('discard').onclick = () => { openVersion(selected); flowMessage = 'Local changes discarded.'; renderFlow(); };
element('delete-draft').onclick = async () => {
  if (busy || selected?.status !== 'DRAFT' || !confirm(`Delete draft v${selected.version}?${dirty ? ' Unsaved changes will also be lost.' : ''}`)) return;
  busy = true;
  renderFlow();
  try {
    await request(`${versionsUrl}/${selected.version}`, 'DELETE');
    versions = versions.filter((item) => item.version !== selected.version);
    openVersion(versions.find((item) => item.status === 'ACTIVE') ?? versions[0]);
    flowMessage = 'Draft deleted.';
  } catch (error) { flowMessage = error.message; }
  finally { busy = false; renderFlow(); }
};


async function selectFlow() {
  registration = registrations.find((item) => item.flowKey === flowSelect.value);
  versionsUrl = new URL('flows/' + encodeURIComponent(registration.flowKey) + '/versions', apiUrl).pathname;
  versions = [];
  catalog = [];
  openVersion(undefined);
  await reloadVersions();
}
element('flow-key').onchange = () => void selectFlow();
async function initialize() {
  busy = true;
  renderFlow();
  try {
    registrations = await request(new URL('flows', apiUrl));
    element('flow-key').replaceChildren(...registrations.map((item) => new Option(item.title, item.flowKey)));
    registration = registrations[0];
    if (!registration) { flowMessage = 'No FSMs registered by this application.'; return; }
    await selectFlow();
  } catch (error) { flowMessage = error.message; }
  finally { busy = false; renderFlow(); }
}
frame.src = editorUrl.href;
void initialize();
