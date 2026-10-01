import { useCallback, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Chat from '../components/Chat';
import ParticipantList from '../components/ParticipantList';
import RequestPanel from '../components/RequestPanel';
import RequestsPanel from '../components/RequestsPanel';
import Toasts from '../components/Toasts';
import VideoUrlInput from '../components/VideoUrlInput';
import YouTubePlayer from '../components/YouTubePlayer';
import { useRoom } from '../hooks/useRoom';
import { loadUsername, saveUsername } from '../lib/storage';
import { canControl, ROLE_LABEL } from '../lib/types';
import type { Role } from '../lib/types';

export default function Room() {
  const { code = '' } = useParams();
  const roomCode = code.toUpperCase();
  const [username, setUsername] = useState(loadUsername());

  if (!username) {
    return (
      <NamePrompt
        roomCode={roomCode}
        onSubmit={(n) => {
          saveUsername(n);
          setUsername(n);
        }}
      />
    );
  }
  return <RoomView roomCode={roomCode} username={username} />;
}

function NamePrompt({ roomCode, onSubmit }: { roomCode: string; onSubmit: (name: string) => void }) {
  const [name, setName] = useState('');
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const n = name.trim().replace(/\s+/g, ' ');
    if (n && n.length <= 24) onSubmit(n);
  };
  return (
    <div className="home">
      <div className="card home-card">
        <h2>Join room {roomCode}</h2>
        <form onSubmit={submit} className="stack">
          <label className="field">
            Your name
            <input value={name} maxLength={24} autoFocus onChange={(e) => setName(e.target.value)} />
          </label>
          <button className="btn primary big" type="submit" disabled={!name.trim()}>
            Join
          </button>
        </form>
      </div>
    </div>
  );
}

function Message({ title, text, children }: { title: string; text: string; children?: React.ReactNode }) {
  return (
    <div className="home">
      <div className="card home-card center">
        <h2>{title}</h2>
        <p className="muted">{text}</p>
        {children}
      </div>
    </div>
  );
}

function RoomView({ roomCode, username }: { roomCode: string; username: string }) {
  const navigate = useNavigate();
  const { state, send, dismissNotice, myRole } = useRoom(roomCode, username);
  const [copied, setCopied] = useState(false);

  const control = canControl(myRole);
  const iAmHost = myRole === 'HOST';

  const sendPlayback = useCallback(
    (type: 'play' | 'pause' | 'seek', payload: { time: number }) => {
      send(type, payload);
    },
    [send],
  );

  const copyInvite = async () => {
    const link = `${window.location.origin}/room/${roomCode}`;
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      window.prompt('Copy this invite link:', link);
    }
  };

  const leave = () => {
    send('leave_room');
    navigate('/');
  };

  // ---- Terminal states ----
  if (state.status === 'not_found') {
    return (
      <Message title="Room not found" text={`There is no room with the code ${roomCode}.`}>
        <Link className="btn primary" to="/">
          Back to home
        </Link>
      </Message>
    );
  }
  if (state.status === 'removed') {
    return (
      <Message title="You were removed" text="The host removed you from this room.">
        <Link className="btn primary" to="/">
          Back to home
        </Link>
      </Message>
    );
  }
  if (state.status === 'replaced') {
    return (
      <Message
        title="Opened in another tab"
        text="This room is now active in another tab or window. Only one tab can be connected at a time."
      >
        <button className="btn primary" onClick={() => window.location.reload()}>
          Use this tab instead
        </button>
      </Message>
    );
  }

  // ---- Connecting ----
  if (!state.me || !state.sync) {
    return (
      <Message
        title={state.status === 'reconnecting' ? 'Reconnecting...' : 'Connecting...'}
        text="Setting up your seat in the room."
      />
    );
  }

  const myId = state.me.userId;

  return (
    <div className="room">
      <header className="topbar">
        <Link to="/" className="brand" onClick={() => send('leave_room')}>
          Watch Party
        </Link>
        <div className="topbar-mid">
          {/* <span className="code-chip" title="Room code">
            {roomCode}
          </span> */}
          <button className="btn small" onClick={copyInvite}>
            {copied ? 'Copied!' : 'Copy invite link'}
          </button>
        </div>
        <div className="topbar-right">
          <span className="muted">{username}</span>
          {myRole && <span className={`badge badge-${myRole.toLowerCase()}`}>{ROLE_LABEL[myRole as Role]}</span>}
          <button className="btn small" onClick={leave}>
            Leave
          </button>
        </div>
      </header>

      {state.status !== 'joined' && (
        <div className="banner">Connection lost. Reconnecting, your seat is being held for you...</div>
      )}

      <main className="layout">
        <section className="col-main">
          <YouTubePlayer sync={state.sync} canControl={control} onCommand={sendPlayback} />

          {control && (
            <div className="card">
              <h3>Change video</h3>
              <VideoUrlInput buttonText="Play for everyone" onSubmit={(videoId) => send('change_video', { videoId })} />
            </div>
          )}
        </section>

        <aside className="col-side">
          <ParticipantList
            participants={state.participants}
            meId={myId}
            iAmHost={iAmHost}
            onAssignRole={(userId, role) => send('assign_role', { userId, role })}
            onRemove={(userId) => send('remove_participant', { userId })}
            onTransferHost={(userId) => send('transfer_host', { userId })}
          />

          {control ? (
            <RequestsPanel
              requests={state.requests}
              onResolve={(requestId, approve) => send('resolve_request', { requestId, approve })}
            />
          ) : (
            <RequestPanel onRequest={(action, data) => send('request_action', { action, data })} />
          )}

          <Chat messages={state.chat} meId={myId} onSend={(text) => send('chat', { text })} />
        </aside>
      </main>

      <Toasts notices={state.notices} onDismiss={dismissNotice} />
    </div>
  );
}
