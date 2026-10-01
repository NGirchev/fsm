// Deployment settings for the FSM editor. Replace this file (for example, mount a ConfigMap) to
// connect the editor to a backend without a `?backend=` query parameter:
//
// window.fsmEditorConfig = {
//   backendUrl: 'https://orders.example.com/fsm-admin',
//   // Authentication belongs to the deployment. By default requests send cookies
//   // (credentials: 'include'); replace fetch to add a token instead:
//   fetch: (input, init) => fetch(input, { ...init, headers: withToken(init?.headers) }),
// };
