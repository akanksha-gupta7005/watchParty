// All storage access is wrapped: it can throw in private mode or when blocked.

function read(store: Storage, key: string): string | null {
  try {
    return store.getItem(key);
  } catch {
    return null;
  }
}

function write(store: Storage, key: string, value: string): void {
  try {
    store.setItem(key, value);
  } catch {
    /* ignore */
  }
}

export function loadUsername(): string {
  return read(sessionStorage, 'wp:username') ?? '';
}

export function saveUsername(name: string): void {
  write(sessionStorage, 'wp:username', name);
}

// The host key proves "I created this room" (kept across browser restarts).
export function loadHostKey(code: string): string | null {
  return read(localStorage, `wp:hostKey:${code}`);
}

export function saveHostKey(code: string, key: string): void {
  write(localStorage, `wp:hostKey:${code}`, key);
}

// The session lets a refreshed tab take its old seat (and role) back.
export interface StoredSession {
  userId: string;
  token: string;
}

export function loadSession(code: string): StoredSession | null {
  const raw = read(sessionStorage, `wp:session:${code}`);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredSession;
  } catch {
    return null;
  }
}

export function saveSession(code: string, s: StoredSession): void {
  write(sessionStorage, `wp:session:${code}`, JSON.stringify(s));
}
