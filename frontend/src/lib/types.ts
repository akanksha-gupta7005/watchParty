export type Role = 'HOST' | 'MODERATOR' | 'PARTICIPANT' | 'VIEWER';

export interface Participant {
  userId: string;
  username: string;
  role: Role;
  online: boolean;
}

export interface SyncState {
  playState: 'PLAYING' | 'PAUSED';
  currentTime: number; // seconds, as computed by the server at send time
  videoId: string;
  serverTime: number;
  cause: string; // join | play | pause | seek | change_video | heartbeat
  by: string | null;
  rev: number; // increases on every update so identical states are still re-applied
  receivedAt: number; // local clock when the message arrived
}

export interface ChatMessage {
  id: number;
  userId: string;
  username: string;
  text: string;
  ts: number;
}

export type RequestAction = 'play' | 'pause' | 'seek' | 'change_video';

export interface ActionRequest {
  requestId: string;
  userId: string;
  username: string;
  action: RequestAction;
  data: { time?: number; videoId?: string };
  ts: number;
}

export interface Notice {
  id: number;
  kind: 'info' | 'success' | 'error';
  text: string;
}

export function canControl(role: Role | undefined): boolean {
  return role === 'HOST' || role === 'MODERATOR';
}

export const ROLE_LABEL: Record<Role, string> = {
  HOST: 'Host',
  MODERATOR: 'Moderator',
  PARTICIPANT: 'Participant',
  VIEWER: 'Viewer',
};
