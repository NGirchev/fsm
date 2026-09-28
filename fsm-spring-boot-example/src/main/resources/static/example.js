const channel = 'fsm-editor/v1';
const element = (id) => document.getElementById(id);
const frame = /** @type {HTMLIFrameElement} */ (element('flow-editor'));
const versionSelect = /** @type {HTMLSelectElement} */ (element('flow-version'));
/** @typedef {{ state: string, commission: number }} OrderSnapshot */
/** @typedef {{ from: string, to: string, event?: string, conditions: string[], actions: string[], postActions: string[], before: OrderSnapshot, after: OrderSnapshot }} TraceStep */
/** @typedef {{ id: number, state: string, flowVersion: number, amount: number, commission: number, trace: TraceStep[] }} OrderResult */
/** @typedef {{ version: number, status: string, definition: { editor?: {name?: string}, table: { transitions: Record<string, {event: string | null}[]> } } }} FlowVersion */
// A host can point this at the same editor deployed on GitHub Pages.
const editorUrl = new URL(frame.dataset.editorUrl || './fsm-editor/', window.location.href);
editorUrl.searchParams.set('parentOrigin', window.location.origin);
const versionsUrl = new URL('./api/flows/order/versions', window.location.href).pathname;
const ordersUrl = new URL('./api/orders', window.location.href).pathname;
let versions = [], catalog = [], selected, currentDocument, savedDocument;
let session = '', ready = false, loaded = false, busy = false, dirty = false;
let flowMessage = 'Loading order flow…';
let pendingSnapshot;

async function request(url, method = 'GET', body) {
  const response = await fetch(url, { method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
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
  const active = versions.find((version) => version.status === 'ACTIVE');
  element('active-flow').textContent = active ? `${active.definition.editor?.name ?? 'Order flow'} · v${active.version}` : 'No active flow loaded';
  element('flow-message').textContent = flowMessage + (dirty ? ' Unsaved changes.' : '');
  element('flow-version').replaceChildren(...versions.map((version) =>
    new Option(`${version.definition.editor?.name ?? 'Order flow'} · v${version.version} · ${version.status}`, String(version.version))));
  if (selected) element('flow-version').value = String(selected.version);
  const unavailable = busy || !loaded || !selected;
  element('flow-version').disabled = unavailable || dirty;
  element('create-draft').disabled = unavailable || dirty;
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
  if (!ready || !selected) return;
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
  renderFlow();
  try {
    [versions, catalog] = await Promise.all([request(versionsUrl), request(versionsUrl.replace(/versions$/, 'behaviors'))]);
    const active = versions.find((item) => item.status === 'ACTIVE') ?? versions[0];
    if (!active) { flowMessage = 'No order flow exists. Initialize the example database first.'; return; }
    openVersion(active);
    flowMessage = 'Order flow loaded. Create a draft to edit.';
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
  if (busy || !loaded || !selected) return;
  busy = true;
  renderFlow();
  try {
    const captured = action === 'publish' || action === 'activate' ? undefined : await snapshot();
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
    flowMessage = action === 'publish' ? 'Published. New orders use this version.' :
      action === 'activate' ? 'Active flow changed. New orders use this version.' :
      action === 'save' ? 'Draft and layout saved.' : 'Draft created. You can edit the graph.';
  } catch (error) { flowMessage = error.message; }
  finally { busy = false; renderFlow(); }
}

element('create-draft').onclick = () => void mutate('create');
element('save-draft').onclick = () => void mutate('save');
element('publish').onclick = () => void mutate('publish');
element('activate').onclick = () => void mutate('activate');
element('retry').onclick = () => void reloadVersions();
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

function showTab() {
  const editor = location.hash === '#flow';
  element('orders-panel').hidden = editor;
  element('editor-panel').hidden = !editor;
  element('orders-tab').setAttribute('aria-selected', String(!editor));
  element('editor-tab').setAttribute('aria-selected', String(editor));
  if (editor && !frame.getAttribute('src')) frame.src = editorUrl.href;
}
element('orders-tab').onclick = () => { location.hash = 'orders'; };
element('editor-tab').onclick = () => { location.hash = 'flow'; };
window.addEventListener('hashchange', showTab);
showTab();
void reloadVersions();

let orderBusy = false;
function setOrderBusy(value) {
  orderBusy = value;
  element('orders-panel').querySelectorAll('button, input, select').forEach((control) => { control.disabled = value; });
}

async function showOrder(next) {
  const order = next;
  localStorage.setItem('fsm-example:last-order-id', String(order.id));
  element('order-detail').hidden = false;
  element('order-title').textContent = `Order #${order.id}`;
  element('order-fields').replaceChildren();
  for (const [label, value] of [['State', order.state], ['Flow version', order.flowVersion],
    ['Amount', order.amount], ['Commission', order.commission]]) {
    const term = document.createElement('dt'), detail = document.createElement('dd');
    term.textContent = label;
    detail.textContent = String(value);
    detail.dataset.field = label;
    element('order-fields').append(term, detail);
  }
  element('order-history').replaceChildren();
  const history = await request(`${ordersUrl}/${order.id}/history`);
  for (const entry of history) {
    const row = document.createElement('tr');
    for (const key of ['kind', 'fromState', 'toState', 'event', 'source', 'requestId']) {
      const cell = document.createElement('td');
      cell.textContent = entry[key] ?? '—';
      row.append(cell);
    }
    element('order-history').append(row);
  }
}

async function orderOperation(operation) {
  if (orderBusy) return;
  setOrderBusy(true);
  try { element('order-message').textContent = await operation(); }
  catch (error) { element('order-message').textContent = error.message; }
  finally { setOrderBusy(false); }
}
element('run-flow').onsubmit = (event) => {
  event.preventDefault();
  const data = new FormData(event.target);
  void orderOperation(async () => {
    return runTestOrder({ amount: data.get('amount') });
  });
};
const lastOrderId = localStorage.getItem('fsm-example:last-order-id');
if (lastOrderId) void orderOperation(async () => {
  await showOrder(await request(`${ordersUrl}/${lastOrderId}`));
  return 'Last order loaded with its saved history.';
});

function traceStep(title, description, className = '') {
  const row = document.createElement('li');
  row.className = className;
  const heading = document.createElement('h3'), text = document.createElement('p');
  heading.textContent = title;
  text.textContent = description;
  row.append(heading, text);
  element('test-steps').append(row);
  return row;
}

/** @param {OrderResult} result */
function appendTrace(result) {
  for (const step of result.trace ?? []) {
    const row = traceStep(`${step.from} → ${step.to}`, step.event ? `Event: ${step.event}` : 'Automatic transition');
    for (const [label, names] of [['Guards passed', step.conditions], ['Actions executed', step.actions], ['Post actions executed', step.postActions]]) {
      if (!names.length) continue;
      const detail = document.createElement('p');
      detail.textContent = `${label}: ${names.map((name) => {
        const behavior = catalog.find((item) => item.id === name);
        return behavior ? `${name} — ${behavior.description}` : name;
      }).join('; ')}`;
      row.append(detail);
    }
    const changes = [];
    for (const [key, label] of [['commission', 'Commission']]) {
      if (step.before[key] !== step.after[key]) changes.push(`${label}: ${step.before[key]} → ${step.after[key]}`);
    }
    const effect = document.createElement('p');
    effect.className = 'step-effect';
    effect.textContent = changes.length ? changes.join(' · ') : 'State changed without changing commission.';
    row.append(effect);
  }
}

async function runTestOrder(input) {
  const sequence = ['SUBMIT', 'FINISH'];
  element('test-result').hidden = false;
  element('test-steps').replaceChildren();
  element('test-title').textContent = 'Starting test order…';
  element('test-summary').textContent = '';
  element('test-outcome').textContent = 'Running';
  /** @type {OrderResult | undefined} */
  let current;
  let sending = 'Start flow';
  try {
    current = /** @type {OrderResult} */ (await request(ordersUrl, 'POST', input));
    element('test-title').textContent = `Order #${current.id} · flow v${current.flowVersion}`;
    const initial = current.trace?.[0]?.before.state ?? current.state;
    traceStep(`Order created · ${initial}`, `Amount: ${current.amount}`);
    appendTrace(current);
    for (const event of sequence) {
      sending = event;
      element('order-message').textContent = `Sending ${event}…`;
      current = /** @type {OrderResult} */ (await request(`${ordersUrl}/${current.id}/events`, 'POST', { event, source: 'example-test', requestId: crypto.randomUUID() }));
      appendTrace(current);
    }
    element('test-outcome').textContent = 'Scenario finished';
    element('test-summary').textContent = `Final state: ${current.state} · Commission: ${current.commission}`;
  } catch (error) {
    element('test-outcome').textContent = 'Stopped';
    traceStep(`${sending} failed`, `${error.message}. This step was not confirmed; earlier confirmed steps are shown above.`, 'step-failed');
    element('test-summary').textContent = current ? `Last confirmed state: ${current.state}` : 'Order creation was not confirmed.';
  }
  if (current) await showOrder(current);
  return element('test-outcome').textContent;
}
