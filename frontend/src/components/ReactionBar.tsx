import { useRef } from 'react';
import { REACTIONS } from '../lib/types';
import type { ReactionKey } from '../lib/types';

interface Props {
  onReact: (key: ReactionKey) => void;
}

/** Emoji buttons under the player. Everyone can use them, whatever their role. */
export default function ReactionBar({ onReact }: Props) {
  const lastClick = useRef(0);

  const click = (key: ReactionKey) => {
    const now = Date.now();
    if (now - lastClick.current < 300) return; // the server also limits how fast you can react
    lastClick.current = now;
    onReact(key);
  };

  return (
    <div className="reaction-bar" aria-label="Reactions">
      <span className="muted small-text">React:</span>
      {REACTIONS.map((r) => (
        <button key={r.key} className="reaction-btn" title={r.label} onClick={() => click(r.key)}>
          {r.emoji}
        </button>
      ))}
    </div>
  );
}
