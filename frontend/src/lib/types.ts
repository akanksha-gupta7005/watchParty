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

// ---- Emoji reactions (the server only knows the keys; the emoji pictures live here) ----
export type ReactionKey = 'heart' | 'laugh' | 'clap' | 'wow' | 'fire' | 'like';

export const REACTIONS: { key: ReactionKey; emoji: string; label: string }[] = [
  { key: 'heart', emoji: '\u2764\uFE0F', label: 'Love' },
  { key: 'laugh', emoji: '\uD83D\uDE02', label: 'Funny' },
  { key: 'clap', emoji: '\uD83D\uDC4F', label: 'Clap' },
  { key: 'wow', emoji: '\uD83D\uDE2E', label: 'Wow' },
  { key: 'fire', emoji: '\uD83D\uDD25', label: 'Fire' },
  { key: 'like', emoji: '\uD83D\uDC4D', label: 'Like' },
];

export interface Reaction {
  id: number;
  userId: string;
  username: string;
  emoji: ReactionKey;
  videoTime: number; // video position (seconds) when it happened
  receivedAt: number;
}
