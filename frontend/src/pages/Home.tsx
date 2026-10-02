import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthError, createRoom, roomExists } from '../lib/api';
import { clearAuth, getAuth } from '../lib/auth';
import { saveHostKey } from '../lib/storage';

export default function Home() {
  const navigate = useNavigate();
  const auth = getAuth();
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (!auth) return null; // RequireAuth sends the user to the login page

  const logout = () => {
    clearAuth();
    navigate('/login', { replace: true });
  };

  const handleCreate = async () => {
    setError('');
    setBusy(true);
    try {
      const room = await createRoom(auth.token);
      saveHostKey(room.code, room.hostKey);
      navigate(`/room/${room.code}`);
    } catch (e) {
      if (e instanceof AuthError) {
        clearAuth();
        navigate('/login', { replace: true, state: { from: '/' } });
        return;
      }
      setError(e instanceof Error ? e.message : 'Something went wrong.');
    } finally {
      setBusy(false);
    }
  };

  const handleJoin = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    const c = code.trim().toUpperCase();
    if (!/^[A-Z0-9]{4,12}$/.test(c)) {
      setError('Enter a valid room code.');
      return;
    }
    setBusy(true);
    try {
      if (!(await roomExists(c))) {
        setError('No room found with that code.');
        return;
      }
      navigate(`/room/${c}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="home">
      <div className="hero">
        <h1>Watch Party</h1>
        <p>Watch YouTube together, perfectly in sync.</p>
      </div>

      <div className="card home-card">
        <div className="who-row">
          <span>
            Signed in as <strong>{auth.user.username}</strong>
          </span>
          <button className="btn small" onClick={logout}>
            Log out
          </button>
        </div>

        <button className="btn primary big" onClick={handleCreate} disabled={busy}>
          Create a new room
        </button>

        <div className="divider">
          <span>or join one</span>
        </div>

        <form className="row-form" onSubmit={handleJoin}>
          <input
            value={code}
            maxLength={12}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder="Room code"
            aria-label="Room code"
          />
          <button className="btn" type="submit" disabled={busy || !code.trim()}>
            Join
          </button>
        </form>

        {error && <div className="form-error">{error}</div>}
      </div>
    </div>
  );
}
