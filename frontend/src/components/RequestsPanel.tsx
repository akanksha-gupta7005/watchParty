import type { ActionRequest } from '../lib/types';
import { formatTime } from '../lib/time';

interface Props {
  requests: ActionRequest[];
  onResolve: (requestId: string, approve: boolean) => void;
}

function describe(r: ActionRequest): string {
  switch (r.action) {
    case 'play':
      return 'wants to resume playback';
    case 'pause':
      return 'wants to pause';
    case 'seek':
      return `wants to jump to ${formatTime(r.data.time ?? 0)}`;
    case 'change_video':
      return 'wants to play a different video';
  }
}

/** For Host/Moderator: approve or reject what participants asked for. */
export default function RequestsPanel({ requests, onResolve }: Props) {
  return (
    <div className="card">
      <h3>
        Requests <span className="count">{requests.length}</span>
      </h3>
      {requests.length === 0 ? (
        <p className="muted small-text">No pending requests.</p>
      ) : (
        <ul className="request-list">
          {requests.map((r) => (
            <li key={r.requestId}>
              <div>
                <strong>{r.username}</strong> {describe(r)}
                {r.action === 'change_video' && r.data.videoId && (
                  <a
                    className="link"
                    href={`https://www.youtube.com/watch?v=${r.data.videoId}`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    {' '}
                    preview
                  </a>
                )}
              </div>
              <div className="btn-row">
                <button className="btn small success" onClick={() => onResolve(r.requestId, true)}>
                  Approve
                </button>
                <button className="btn small danger" onClick={() => onResolve(r.requestId, false)}>
                  Reject
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
