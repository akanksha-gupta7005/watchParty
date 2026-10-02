import { useCallback, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import Chat from '../components/Chat';
import ParticipantList from '../components/ParticipantList';
import ReactionBar from '../components/ReactionBar';
import ReactionLayer from '../components/ReactionLayer';
import RequestPanel from '../components/RequestPanel';
import RequestsPanel from '../components/RequestsPanel';
import Toasts from '../components/Toasts';
import VideoUrlInput from '../components/VideoUrlInput';
import YouTubePlayer from '../components/YouTubePlayer';
import { useRoom } from '../hooks/useRoom';
import { clearAuth, getAuth } from '../lib/auth';
import type { AuthInfo } from '../lib/auth';
import { canControl, ROLE_LABEL } from '../lib/types';
import type { Role } from '../lib/types';

export default function Room() {
  const { code = '' } = useParams();
  const roomCode = code.toUpperCase();
  const auth = getAuth();
  if (!auth) return <Navigate to="/login" replace state={{ from: `/room/${roomCode}` }} />;
  return <RoomView roomCode={roomCode} auth={auth} />;
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

function RoomView({ roomCode, auth }: { roomCode: string; auth: AuthInfo }) {
  const navigate = useNavigate();
  const { state, send, dismissNotice, myRole } = useRoom(roomCode, auth.token);
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
  if (state.status === 'unauthorized') {
    return (
      <Message title="Please log in again" text="Your login is not valid any more (it may have expired).">
        <button
          className="btn primary"
          onClick={() => {
            clearAuth();
            navigate('/login', { replace: true, state: { from: `/room/${roomCode}` } });
          }}
        >
          Log in
        </button>
      </Message>
    );
  }
  if (state.status === 'room_full') {
    return (
      <Message title="This room is full" text="The room has reached its maximum number of people. Try again later.">
        <Link className="btn primary" to="/">
          Back to home
        </Link>
      </Message>
    );
  }
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
        text="You joined this room from another tab or window. One account can use only one tab at a time."
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
          <button className="btn small" onClick={copyInvite}>
            {copied ? 'Copied!' : 'Copy invite link'}
          </button>
        </div>
        <div className="topbar-right">
          <span className="muted">{auth.user.username}</span>
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
          <YouTubePlayer
            sync={state.sync}
            canControl={control}
            onCommand={sendPlayback}
            overlay={<ReactionLayer reactions={state.reactions} />}
          />

          <ReactionBar onReact={(emoji) => send('react', { emoji })} />

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
