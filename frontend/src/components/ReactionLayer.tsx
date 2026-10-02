import { useEffect, useState } from 'react';
import { formatTime } from '../lib/time';
import { REACTIONS } from '../lib/types';
import type { Reaction } from '../lib/types';

const SHOW_MS = 3200;

/** Emojis that float up over the video. Each one shows who reacted and the video time. */
export default function ReactionLayer({ reactions }: { reactions: Reaction[] }) {
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 400);
    return () => window.clearInterval(id);
  }, []);

  const recent = reactions.filter((r) => now - r.receivedAt < SHOW_MS);

  return (
    <div className="reaction-layer" aria-hidden="true">
      {recent.map((r) => {
        const emoji = REACTIONS.find((x) => x.key === r.emoji)?.emoji ?? '';
        return (
          <div key={r.id} className="floating" style={{ left: `${8 + ((r.id * 37) % 78)}%` }}>
            <span className="floating-emoji">{emoji}</span>
            <span className="floating-who">
              {r.username} at {formatTime(r.videoTime)}
            </span>
          </div>
        );
      })}
    </div>
  );
}
