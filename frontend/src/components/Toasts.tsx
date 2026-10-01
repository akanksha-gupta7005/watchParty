import { useEffect } from 'react';
import type { Notice } from '../lib/types';

interface Props {
  notices: Notice[];
  onDismiss: (id: number) => void;
}

function Toast({ notice, onDismiss }: { notice: Notice; onDismiss: (id: number) => void }) {
  useEffect(() => {
    const t = window.setTimeout(() => onDismiss(notice.id), 4500);
    return () => window.clearTimeout(t);
  }, [notice.id, onDismiss]);

  return (
    <div className={`toast toast-${notice.kind}`} onClick={() => onDismiss(notice.id)}>
      {notice.text}
    </div>
  );
}

export default function Toasts({ notices, onDismiss }: Props) {
  return (
    <div className="toasts" aria-live="polite">
      {notices.map((n) => (
        <Toast key={n.id} notice={n} onDismiss={onDismiss} />
      ))}
    </div>
  );
}
