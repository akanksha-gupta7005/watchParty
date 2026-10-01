import { API_URL } from './config';

export interface CreatedRoom {
  code: string;
  hostKey: string;
}

export async function createRoom(): Promise<CreatedRoom> {
  const res = await fetch(`${API_URL}/api/rooms`, { method: 'POST' });
  if (!res.ok) throw new Error('Could not create a room. Is the backend running?');
  return res.json();
}

export async function roomExists(code: string): Promise<boolean> {
  const res = await fetch(`${API_URL}/api/rooms/${encodeURIComponent(code)}`);
  if (res.status === 404) return false;
  if (!res.ok) throw new Error('Could not reach the server.');
  return true;
}
