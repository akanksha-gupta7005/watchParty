import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import type { ChatMessage } from '../lib/types';

interface Props {
  messages: ChatMessage[];
  meId: string | undefined;
  onSend: (text: string) => void;
}

export default function Chat({ messages, meId, onSend }: Props) {
  const [text, setText] = useState('');
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages.length]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const t = text.trim();
    if (!t) return;
    onSend(t);
    setText('');
  };

  return (
    <div className="card chat">
      <h3>Chat</h3>
      <div className="chat-list" ref={listRef}>
        {messages.length === 0 && <p className="muted small-text">No messages yet. Say hi!</p>}
        {messages.map((m) => (
          <div key={m.id} className={m.userId === meId ? 'msg mine' : 'msg'}>
            <span className="msg-author">{m.username}</span>
            <span className="msg-text">{m.text}</span>
          </div>
        ))}
      </div>
      <form className="row-form" onSubmit={submit}>
        <input
          value={text}
          maxLength={500}
          onChange={(e) => setText(e.target.value)}
          placeholder="Type a message"
          aria-label="Chat message"
        />
        <button className="btn primary" type="submit" disabled={!text.trim()}>
          Send
        </button>
      </form>
    </div>
  );
}
