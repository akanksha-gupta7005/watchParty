// By default the app talks to its own origin (nginx in Docker, Vite proxy in dev).
// Set VITE_API_URL / VITE_WS_URL at build time when the backend is hosted elsewhere.
const apiEnv = import.meta.env.VITE_API_URL;
const wsEnv = import.meta.env.VITE_WS_URL;

export const API_URL: string = apiEnv ? apiEnv.replace(/\/$/, '') : '';

export function wsUrl(): string {
  if (wsEnv) return wsEnv;
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${window.location.host}/ws`;
}
