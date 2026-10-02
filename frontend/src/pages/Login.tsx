import { useState } from 'react';
import type { FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { login, register } from '../lib/api';
import { getAuth, setAuth } from '../lib/auth';

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // Already logged in in this tab: go on.
  if (getAuth()) return <Navigate to={from} replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    if (!username.trim() || !password) {
      setError('Please enter a username and a password.');
      return;
    }
    setBusy(true);
    try {
      const info = mode === 'login' ? await login(username.trim(), password) : await register(username.trim(), password);
      setAuth(info);
      navigate(from, { replace: true });
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
        <p>Log in to create or join a room.</p>
      </div>

      <div className="card home-card">
        <div className="tabs">
          <button className={mode === 'login' ? 'tab active' : 'tab'} onClick={() => setMode('login')} type="button">
            Log in
          </button>
          <button className={mode === 'register' ? 'tab active' : 'tab'} onClick={() => setMode('register')} type="button">
            Create account
          </button>
        </div>

        <form className="stack" onSubmit={submit}>
          <label className="field">
            Username
            <input
              value={username}
              maxLength={20}
              autoFocus
              autoComplete="username"
              onChange={(e) => setUsername(e.target.value)}
              placeholder="letters, numbers, _"
            />
          </label>
          <label className="field">
            Password
            <input
              type="password"
              value={password}
              maxLength={72}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={mode === 'register' ? 'at least 6 characters' : ''}
            />
          </label>
          <button className="btn primary big" type="submit" disabled={busy}>
            {busy ? 'Please wait...' : mode === 'login' ? 'Log in' : 'Create account'}
          </button>
          {error && <div className="form-error">{error}</div>}
        </form>
      </div>
    </div>
  );
}
