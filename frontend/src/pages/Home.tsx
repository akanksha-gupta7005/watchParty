import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { createRoom, roomExists } from '../lib/api';
import { loadUsername, saveHostKey, saveUsername } from '../lib/storage';

export default function Home() {
  const navigate = useNavigate();
  const [name, setName] = useState(loadUsername());
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const cleanName = () => {
    const n = name.trim().replace(/\s+/g, ' ');
    if (!n) {
      setError('Please enter your name first.');
      return null;
    }
    if (n.length > 24) {
      setError('Name can be at most 24 characters.');
      return null;
    }
    return n;
  };

  const handleCreate = async () => {
    setError('');
    const n = cleanName();
    if (!n) return;
    setBusy(true);
    try {
      saveUsername(n);
      const room = await createRoom();
      saveHostKey(room.code, room.hostKey); // proves we are the creator when we join the socket
      navigate(`/room/${room.code}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong.');
    } finally {
      setBusy(false);
    }
  };

  const handleJoin = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    const n = cleanName();
    if (!n) return;
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
      saveUsername(n);
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
        <label className="field">
          Your name
          <input
            value={name}
            maxLength={24}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Riya"
          />
        </label>

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
