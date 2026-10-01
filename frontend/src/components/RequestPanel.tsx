import { useState } from 'react';
import type { FormEvent } from 'react';
import { parseTime } from '../lib/time';
import VideoUrlInput from './VideoUrlInput';

interface Props {
  /** Sends a request_action to the server. */
  onRequest: (action: 'play' | 'pause' | 'seek' | 'change_video', data: object) => void;
}

/** For Participants/Viewers: they cannot act directly, so they ask a Host/Moderator. */
export default function RequestPanel({ onRequest }: Props) {
  const [seek, setSeek] = useState('');
  const [error, setError] = useState('');

  const submitSeek = (e: FormEvent) => {
    e.preventDefault();
    const t = parseTime(seek);
    if (t === null) {
      setError('Use seconds (90) or mm:ss (1:30).');
      return;
    }
    setError('');
    setSeek('');
    onRequest('seek', { time: t });
  };

  return (
    <div className="card">
      <h3>Request a change</h3>
      <p className="muted small-text">
        You can't control the video yourself. Send a request and the host or a moderator can approve it.
      </p>

      <div className="btn-row">
        <button className="btn" onClick={() => onRequest('play', {})}>
          Request play
        </button>
        <button className="btn" onClick={() => onRequest('pause', {})}>
          Request pause
        </button>
      </div>

      <form className="row-form" onSubmit={submitSeek}>
        <input
          value={seek}
          onChange={(e) => setSeek(e.target.value)}
          placeholder="Seek to (e.g. 1:30)"
          aria-label="Seek to time"
        />
        <button className="btn" type="submit" disabled={!seek.trim()}>
          Request seek
        </button>
        {error && <div className="form-error">{error}</div>}
      </form>

      <VideoUrlInput buttonText="Request video" onSubmit={(videoId) => onRequest('change_video', { videoId })} />
    </div>
  );
}
