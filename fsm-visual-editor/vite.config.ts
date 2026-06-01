import { defineConfig } from 'vite';
import type { Plugin, ViteDevServer } from 'vite';
import react from '@vitejs/plugin-react';
import { mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import type { IncomingMessage, ServerResponse } from 'node:http';

export default defineConfig({
  base: process.env.VITE_BASE_PATH ?? '/',
  plugins: [react(), projectsApiPlugin()],
});

function projectsApiPlugin(): Plugin {
  const projectsDir = path.resolve(__dirname, 'projects');

  return {
    name: 'fsm-visual-editor-projects-api',
    configureServer(server: ViteDevServer) {
      server.middlewares.use('/api/projects', async (request: IncomingMessage, response: ServerResponse) => {
        try {
          await mkdir(projectsDir, { recursive: true });
          const url = new URL(request.url ?? '/', 'http://localhost');
          const projectId = url.pathname.replace(/^\/+/, '');

          if (request.method === 'GET' && !projectId) {
            const projects = await listProjects(projectsDir);
            sendJson(response, 200, projects);
            return;
          }

          if (request.method === 'GET' && isSafeProjectId(projectId)) {
            const document = await readProject(projectsDir, projectId);
            sendJson(response, 200, document);
            return;
          }

          if (request.method === 'PUT' && isSafeProjectId(projectId)) {
            const document = await readRequestJson(request);
            await writeFile(projectPath(projectsDir, projectId), `${JSON.stringify(document, null, 2)}\n`, 'utf-8');
            sendJson(response, 200, { ok: true });
            return;
          }

          if (request.method === 'DELETE' && isSafeProjectId(projectId)) {
            await rm(projectPath(projectsDir, projectId), { force: true });
            sendJson(response, 200, { ok: true });
            return;
          }

          sendJson(response, 404, { error: 'Not found' });
        } catch (error) {
          const message = error instanceof Error ? error.message : 'Project API failed';
          sendJson(response, 500, { error: message });
        }
      });
    },
  };
}

async function listProjects(projectsDir: string) {
  const entries = await readdir(projectsDir, { withFileTypes: true });
  const projects = await Promise.all(
    entries
      .filter((entry) => entry.isFile() && entry.name.endsWith('.fsm.json'))
      .map(async (entry) => {
        const id = entry.name.slice(0, -'.fsm.json'.length);
        const document = await readProject(projectsDir, id);
        const meta = projectMeta(document);

        return {
          id,
          name: meta.name ?? id,
          updatedAt: meta.updatedAt,
        };
      }),
  );

  return projects.sort((left, right) => (right.updatedAt ?? '').localeCompare(left.updatedAt ?? ''));
}

async function readProject(projectsDir: string, projectId: string) {
  const content = await readFile(projectPath(projectsDir, projectId), 'utf-8');
  return JSON.parse(content) as unknown;
}

function projectMeta(document: unknown): { name: string | null; updatedAt: string | null } {
  if (!document || typeof document !== 'object') {
    return { name: null, updatedAt: null };
  }

  const candidate = document as { name?: unknown; updatedAt?: unknown };

  return {
    name: typeof candidate.name === 'string' ? candidate.name : null,
    updatedAt: typeof candidate.updatedAt === 'string' ? candidate.updatedAt : null,
  };
}

function projectPath(projectsDir: string, projectId: string): string {
  return path.join(projectsDir, `${projectId}.fsm.json`);
}

function isSafeProjectId(projectId: string): boolean {
  return /^[a-z0-9][a-z0-9-]*$/.test(projectId);
}

async function readRequestJson(request: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];

  for await (const chunk of request) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }

  return JSON.parse(Buffer.concat(chunks).toString('utf-8'));
}

function sendJson(response: ServerResponse, statusCode: number, body: unknown): void {
  response.statusCode = statusCode;
  response.setHeader('Content-Type', 'application/json');
  response.end(JSON.stringify(body));
}
