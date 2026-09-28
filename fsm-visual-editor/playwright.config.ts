import { defineConfig } from '@playwright/test';

const port = process.env.E2E_VITE_PORT;
const staticPort = process.env.E2E_STATIC_PORT;
if (!port || !staticPort || !process.env.E2E_EMBEDDED_URL) throw new Error('Use npm run test:e2e to create an isolated environment');

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 30000,
  expect: { timeout: 7000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { browserName: 'chromium', headless: true, viewport: { width: 1600, height: 1000 },
    trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: [
    { command: `npm run dev -- --host 127.0.0.1 --port ${port} --strictPort`,
      url: `http://127.0.0.1:${port}`, reuseExistingServer: false },
    { command: `npm run preview -- --outDir ../fsm-spring-boot-starter/build/editor --host 127.0.0.1 --port ${staticPort} --strictPort`,
      url: `http://127.0.0.1:${staticPort}`, reuseExistingServer: false },
  ],
});
