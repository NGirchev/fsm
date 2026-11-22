/** Color theme of the editor: `light`, `dark`, or `system` (follows the OS setting). */
export type ThemePreference = 'light' | 'dark' | 'system';

export function themePreference(value: unknown): ThemePreference {
  return value === 'light' || value === 'dark' ? value : 'system';
}

/**
 * Applies `?theme=` and keeps `html[data-theme]` current: follows OS changes in `system` mode and takes
 * `{ channel: 'fsm-editor/v1', type: 'theme', theme }` from the embedding page (same origin or `?parentOrigin=`).
 */
export function startTheme() {
  const params = new URLSearchParams(window.location.search);
  const system = window.matchMedia('(prefers-color-scheme: dark)');
  let preference = themePreference(params.get('theme'));
  const apply = () => {
    document.documentElement.dataset.theme = preference === 'system' ? (system.matches ? 'dark' : 'light') : preference;
  };
  system.addEventListener('change', apply);
  window.addEventListener('message', (event) => {
    const fromHost = event.origin === window.location.origin || event.origin === params.get('parentOrigin');
    if (event.source !== window.parent || !fromHost || event.data?.channel !== 'fsm-editor/v1' || event.data.type !== 'theme') return;
    preference = themePreference(event.data.theme);
    apply();
  });
  apply();
}
