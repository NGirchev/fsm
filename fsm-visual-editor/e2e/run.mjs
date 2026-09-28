import { spawn } from 'node:child_process';
import { mkdtemp, readdir, rm, open } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { createServer } from 'node:net';

// Every run owns its database, project files and servers. Never reuse a developer's app.
const root = resolve(import.meta.dirname, '../..');
const editor = resolve(root, 'fsm-visual-editor');
const temp = await mkdtemp(resolve(tmpdir(), 'fsm-editor-e2e-'));
const container = `fsm-editor-e2e-${process.pid}-${Date.now()}`;
let app;
let tests;
let databaseCreated = false;
let stopping = false;

function command(program, args, options = {}) {
  return new Promise((done, reject) => {
    const child = spawn(program, args, { cwd: root, stdio: 'inherit', ...options });
    let output = '';
    child.stdout?.on('data', (data) => { output += data; });
    child.on('error', reject);
    child.on('exit', (code) => code === 0 ? done(output.trim()) : reject(new Error(`${program} exited ${code}`)));
  });
}

async function freePort() {
  const server = createServer();
  await new Promise((done) => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise((done) => server.close(done));
  return port;
}

async function stop(child) {
  if (!child || child.exitCode !== null || child.signalCode !== null) return;
  await new Promise((done) => {
    const timeout = setTimeout(() => child.kill('SIGKILL'), 5000);
    child.once('exit', () => { clearTimeout(timeout); done(); });
    child.kill('SIGTERM');
  });
}

async function cleanup() {
  if (stopping) return;
  stopping = true;
  await stop(tests);
  await stop(app);
  if (databaseCreated) await command('docker', ['rm', '-f', container]).catch(() => {});
  await rm(temp, { recursive: true, force: true });
}
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, async () => { await cleanup(); process.exit(130); });

try {
  if (!process.env.E2E_APP_JAR) await command('./gradlew', [':fsm-spring-boot-example:bootJar']);
  await command('docker', ['run', '-d', '--rm', '--name', container, '-e', 'POSTGRES_DB=fsm',
    '-e', 'POSTGRES_USER=fsm', '-e', 'POSTGRES_PASSWORD=fsm', '-p', '127.0.0.1::5432', 'postgres:17-alpine']);
  databaseCreated = true;
  const mapping = await command('docker', ['port', container, '5432/tcp'], { stdio: ['ignore', 'pipe', 'inherit'] });
  const dbPort = mapping.split(':').at(-1);
  const appPort = await freePort();
  const vitePort = await freePort();
  const staticPort = await freePort();
  const libs = resolve(root, 'fsm-spring-boot-example/build/libs');
  const jar = process.env.E2E_APP_JAR ?? (await readdir(libs))
    .find((name) => name.endsWith('.jar') && !name.endsWith('-plain.jar'));
  if (!jar) throw new Error('Example bootJar not found');
  const log = await open(resolve(editor, 'e2e-server.log'), 'w');
  app = spawn('java', ['-jar', resolve(libs, jar), `--server.port=${appPort}`, '--server.servlet.context-path=/test',
    `--spring.datasource.url=jdbc:postgresql://127.0.0.1:${dbPort}/fsm`,
    '--spring.datasource.username=fsm', '--spring.datasource.password=fsm'],
  { cwd: temp, stdio: ['ignore', log.fd, log.fd] });
  app.on('error', (error) => console.error(error));
  const embeddedUrl = `http://127.0.0.1:${appPort}/test`;
  const deadline = Date.now() + 90000;
  while (true) {
    if (app.exitCode !== null || Date.now() > deadline) throw new Error('Example failed to start; see e2e-server.log');
    if (await fetch(`${embeddedUrl}/api/flows/order/versions`).then((r) => r.ok).catch(() => false)) break;
    await new Promise((done) => setTimeout(done, 500));
  }
  tests = spawn(process.execPath, [resolve(editor, 'node_modules/@playwright/test/cli.js'), 'test', ...process.argv.slice(2)], {
    cwd: editor, stdio: 'inherit', env: { ...process.env, E2E_EMBEDDED_URL: embeddedUrl,
      E2E_VITE_PORT: String(vitePort), E2E_STATIC_PORT: String(staticPort), FSM_EDITOR_PROJECTS_DIR: resolve(temp, 'projects') },
  });
  process.exitCode = await new Promise((done, reject) => { tests.on('error', reject); tests.on('exit', (code) => done(code ?? 1)); });
  await log.close();
} catch (error) {
  console.error(error);
  process.exitCode = 1;
} finally {
  await cleanup();
}
