const element = (id) => document.getElementById(id);
const ordersUrl = new URL('./api/orders', window.location.href).pathname;
const versionsUrl = new URL('./fsm-admin/api/flows/order/versions', window.location.href).pathname;
let catalog = [];
/** @typedef {{ state: string, commission: number }} OrderSnapshot */
/** @typedef {{ from: string, to: string, event?: string, conditions: string[], actions: string[], postActions: string[], before: OrderSnapshot, after: OrderSnapshot }} TraceStep */
/** @typedef {{ id: number, state: string, flowVersion: number, amount: number, commission: number, trace: TraceStep[] }} OrderResult */
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


async function refreshFlow() {
  try {
    const [versions, behaviors] = await Promise.all([request(versionsUrl), request(versionsUrl.replace(/versions$/, 'behaviors'))]);
    catalog = behaviors;
    const active = versions.find((version) => version.status === 'ACTIVE');
    element('active-flow').textContent = active ? `${active.definition.editor?.name ?? 'Order flow'} · v${active.version}` : 'No active flow';
  } catch (error) { element('active-flow').textContent = error.message; }
}
function showTab() {
  const editor = location.hash === '#flow';
  element('orders-panel').hidden = editor;
  element('editor-panel').hidden = !editor;
  element('orders-tab').setAttribute('aria-selected', String(!editor));
  element('editor-tab').setAttribute('aria-selected', String(editor));
  const panel = element('fsm-admin');
  if (editor && !panel.getAttribute('src')) panel.src = './fsm-admin/';
  if (!editor) void refreshFlow();
}
element('orders-tab').onclick = () => { location.hash = 'orders'; };
element('editor-tab').onclick = () => { location.hash = 'flow'; };
window.addEventListener('hashchange', showTab);
showTab();
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
