import { useCallback, useEffect, useReducer, useRef } from 'react';
import { wsUrl } from '../lib/config';
import { loadHostKey, loadSession, saveSession } from '../lib/storage';
import { formatTime } from '../lib/time';
import type {
  ActionRequest,
  ChatMessage,
  Notice,
  Participant,
  Role,
  SyncState,
} from '../lib/types';

export type Status =
  | 'connecting'
  | 'reconnecting'
  | 'joined'
  | 'closed'
  | 'removed'
  | 'replaced'
  | 'not_found';

export interface RoomState {
  status: Status;
  me: { userId: string } | null;
  participants: Participant[];
  sync: SyncState | null;
  chat: ChatMessage[];
  requests: ActionRequest[];
  notices: Notice[];
  noticeSeq: number;
}

const initialState: RoomState = {
  status: 'connecting',
  me: null,
  participants: [],
  sync: null,
  chat: [],
  requests: [],
  notices: [],
  noticeSeq: 0,
};

type Action =
  | { type: 'ws_status'; status: 'connecting' | 'reconnecting' | 'closed' }
  | { type: 'server'; msg: { type: string; payload: any } }
  | { type: 'dismiss_notice'; id: number };

const FINAL: Status[] = ['removed', 'replaced', 'not_found'];

function addNotice(state: RoomState, kind: Notice['kind'], text: string): RoomState {
  const id = state.noticeSeq + 1;
  const notices = [...state.notices, { id, kind, text }].slice(-4);
  return { ...state, noticeSeq: id, notices };
}

function toSync(p: any, previous: SyncState | null): SyncState {
  return {
    playState: p.playState,
    currentTime: p.currentTime,
    videoId: p.videoId,
    serverTime: p.serverTime,
    cause: p.cause,
    by: p.by ?? null,
    rev: (previous?.rev ?? 0) + 1,
    receivedAt: Date.now(),
  };
}

function describeRequest(r: { action: string; data?: { time?: number } }): string {
  switch (r.action) {
    case 'play':
      return 'play';
    case 'pause':
      return 'pause';
    case 'seek':
      return `seek to ${formatTime(r.data?.time ?? 0)}`;
    case 'change_video':
      return 'change the video';
    default:
      return r.action;
  }
}

function reducer(state: RoomState, action: Action): RoomState {
  switch (action.type) {
    case 'dismiss_notice':
      return { ...state, notices: state.notices.filter((n) => n.id !== action.id) };

    case 'ws_status':
      if (FINAL.includes(state.status)) return state;
      return { ...state, status: action.status };

    case 'server': {
      const { type, payload: p } = action.msg;
      switch (type) {
        case 'joined':
          return {
            ...state,
            status: 'joined',
            me: { userId: p.userId },
            participants: p.participants,
            sync: toSync(p.state, state.sync),
            chat: p.chat ?? [],
            requests: p.requests ?? [],
          };

        case 'sync_state': {
          let next: RoomState = { ...state, sync: toSync(p, state.sync) };
          if (p.cause === 'change_video' && p.by) {
            next = addNotice(next, 'info', `${p.by} changed the video`);
          }
          return next;
        }

        case 'user_joined':
          return addNotice({ ...state, participants: p.participants }, 'info', `${p.username} joined`);

        case 'user_left':
          return addNotice({ ...state, participants: p.participants }, 'info', `${p.username} left`);

        case 'presence_changed':
          return { ...state, participants: p.participants };

        case 'role_assigned': {
          const mine = p.userId === state.me?.userId;
          const text = mine
            ? `You are now a ${String(p.role).toLowerCase()}`
            : `${p.username} is now a ${String(p.role).toLowerCase()}`;
          return addNotice({ ...state, participants: p.participants }, mine ? 'success' : 'info', text);
        }

        case 'participant_removed':
          return addNotice({ ...state, participants: p.participants }, 'info', `${p.username} was removed`);

        case 'host_changed': {
          const mine = p.userId === state.me?.userId;
          return addNotice(
            { ...state, participants: p.participants },
            mine ? 'success' : 'info',
            mine ? 'You are now the host' : `${p.username} is now the host`,
          );
        }

        case 'requests_sync':
          return { ...state, requests: p.requests ?? [] };

        case 'action_requested': {
          if (state.requests.some((r) => r.requestId === p.requestId)) return state;
          return addNotice(
            { ...state, requests: [...state.requests, p as ActionRequest] },
            'info',
            `${p.username} wants to ${describeRequest(p)}`,
          );
        }

        case 'request_sent':
          return addNotice(state, 'info', 'Request sent. Waiting for the host or a moderator.');

        case 'request_resolved': {
          const requests = state.requests.filter((r) => r.requestId !== p.requestId);
          const next = { ...state, requests };
          if (p.userId === state.me?.userId) {
            return addNotice(
              next,
              p.approve ? 'success' : 'error',
              p.approve
                ? `${p.resolvedBy} approved your request`
                : `${p.resolvedBy} rejected your request`,
            );
          }
          return next;
        }

        case 'chat': {
          const chat = [...state.chat, p as ChatMessage].slice(-200);
          return { ...state, chat };
        }

        case 'removed':
          return addNotice({ ...state, status: 'removed' }, 'error', p.reason ?? 'You were removed');

        case 'replaced':
          return { ...state, status: 'replaced' };

        case 'error': {
          if (p.code === 'NOT_FOUND' && state.status !== 'joined') {
            return { ...state, status: 'not_found' };
          }
          return addNotice(state, 'error', p.message ?? 'Something went wrong');
        }

        default:
          return state; // pong and unknown messages
      }
    }
  }
}

/**
 * Owns the WebSocket for one room: connects, joins, reconnects with back-off,
 * keeps the connection alive and turns server messages into React state.
 */
export function useRoom(roomCode: string, username: string) {
  const [state, dispatch] = useReducer(reducer, initialState);
  const wsRef = useRef<WebSocket | null>(null);

  const send = useCallback((type: string, payload: object = {}): boolean => {
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type, payload }));
      return true;
    }
    return false;
  }, []);

  useEffect(() => {
    let disposed = false;
    let fatal = false;
    let attempt = 0;
    let reconnectTimer: number | undefined;
    let pingTimer: number | undefined;

    const connect = () => {
      if (disposed) return;
      dispatch({ type: 'ws_status', status: attempt === 0 ? 'connecting' : 'reconnecting' });

      const ws = new WebSocket(wsUrl());
      wsRef.current = ws;

      ws.onopen = () => {
        attempt = 0;
        const session = loadSession(roomCode);
        ws.send(
          JSON.stringify({
            type: 'join_room',
            payload: {
              roomId: roomCode,
              username,
              hostKey: loadHostKey(roomCode) ?? undefined,
              userId: session?.userId,
              token: session?.token,
            },
          }),
        );
        window.clearInterval(pingTimer);
        pingTimer = window.setInterval(() => {
          if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: 'ping', payload: {} }));
        }, 25_000);
      };

      ws.onmessage = (ev) => {
        let msg: { type: string; payload: any };
        try {
          msg = JSON.parse(ev.data);
        } catch {
          return;
        }
        if (msg.type === 'joined') {
          saveSession(roomCode, { userId: msg.payload.userId, token: msg.payload.token });
        }
        if (msg.type === 'removed' || msg.type === 'replaced') fatal = true;
        if (msg.type === 'error' && msg.payload?.code === 'NOT_FOUND') fatal = true;
        dispatch({ type: 'server', msg });
      };

      ws.onclose = () => {
        window.clearInterval(pingTimer);
        if (disposed) return;
        if (fatal) {
          dispatch({ type: 'ws_status', status: 'closed' });
          return;
        }
        dispatch({ type: 'ws_status', status: 'reconnecting' });
        const delay = Math.min(1000 * 2 ** attempt, 10_000);
        attempt += 1;
        reconnectTimer = window.setTimeout(connect, delay);
      };

      ws.onerror = () => {
        // onclose always follows; reconnect logic lives there
      };
    };

    connect();

    return () => {
      disposed = true;
      window.clearTimeout(reconnectTimer);
      window.clearInterval(pingTimer);
      wsRef.current?.close();
      wsRef.current = null;
    };
  }, [roomCode, username]);

  const dismissNotice = useCallback((id: number) => dispatch({ type: 'dismiss_notice', id }), []);

  const me: Participant | undefined = state.participants.find((p) => p.userId === state.me?.userId);
  const myRole: Role | undefined = me?.role;

  return { state, send, dismissNotice, me, myRole };
}
