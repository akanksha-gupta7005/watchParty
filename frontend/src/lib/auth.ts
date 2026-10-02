// The login lives in sessionStorage: it is cleared when the tab closes, and every browser tab can
// be a different user. That makes it easy to test with two accounts in two tabs.

export interface AuthUser {
  id: number;
  username: string;
}

export interface AuthInfo {
  token: string;
  user: AuthUser;
}

const KEY = 'wp:auth';

export function getAuth(): AuthInfo | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as AuthInfo) : null;
  } catch {
    return null;
  }
}

export function setAuth(info: AuthInfo): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(info));
  } catch {
    /* ignore */
  }
}

export function clearAuth(): void {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}
