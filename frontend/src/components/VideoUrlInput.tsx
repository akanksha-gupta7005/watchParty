import { useState } from 'react';
import type { FormEvent } from 'react';
import { extractVideoId } from '../lib/youtube';

interface Props {
  buttonText: string;
  onSubmit: (videoId: string) => void;
}

/** Paste a YouTube link (or id) -> extracts the 11-character video id. */
export default function VideoUrlInput({ buttonText, onSubmit }: Props) {
  const [value, setValue] = useState('');
  const [error, setError] = useState('');

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const id = extractVideoId(value);
    if (!id) {
      setError("That doesn't look like a YouTube link.");
      return;
    }
    setError('');
    setValue('');
    onSubmit(id);
  };

  return (
    <form className="row-form" onSubmit={submit}>
      <input
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Paste a YouTube link, e.g. https://youtu.be/..."
        aria-label="YouTube link"
      />
      <button className="btn primary" type="submit" disabled={!value.trim()}>
        {buttonText}
      </button>
      {error && <div className="form-error">{error}</div>}
    </form>
  );
}
