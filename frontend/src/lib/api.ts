import { API_URL } from './config';
import type { AuthInfo } from './auth';

export interface CreatedRoom {
  code: string;
  hostKey: string;
}

/** The login is missing or expired: the user must log in again. */
export class AuthError extends Error {}

async function readBody(res: Response): Promise<any> {
  try {
    return await res.json();
  } catch {
    return {};
  }
}

async function authRequest(path: 'login' | 'register', username: string, password: string): Promise<AuthInfo> {
  let res: Response;
  try {
    res = await fetch(`${API_URL}/api/auth/${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
  } catch {
    throw new Error('Cannot reach the server. If it was sleeping, wait a minute and try again.');
  }
  const body = await readBody(res);
  if (!res.ok) throw new Error(body.error ?? 'Something went wrong. Please try again.');
  return body as AuthInfo;
}

export const login = (username: string, password: string) => authRequest('login', username, password);
export const register = (username: string, password: string) => authRequest('register', username, password);

export async function createRoom(token: string): Promise<CreatedRoom> {
  const res = await fetch(`${API_URL}/api/rooms`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status === 401) throw new AuthError('Your login expired. Please log in again.');
  if (!res.ok) throw new Error('Could not create a room. Is the backend running?');
  return res.json();
}

export async function roomExists(code: string): Promise<boolean> {
  const res = await fetch(`${API_URL}/api/rooms/${encodeURIComponent(code)}`);
  if (res.status === 404) return false;
  if (!res.ok) throw new Error('Could not reach the server.');
  return true;
}
