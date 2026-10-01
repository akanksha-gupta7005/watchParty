import { useCallback, useEffect, useRef, useState } from 'react';
import { loadYouTubeApi, PS } from '../lib/youtube';
import type { YTPlayer } from '../lib/youtube';
import type { SyncState } from '../lib/types';

interface Props {
  sync: SyncState;
  canControl: boolean;
  /** Host/Moderator only: tell the server about a local play/pause/seek. */
  onCommand: (type: 'play' | 'pause' | 'seek', payload: { time: number }) => void;
}

const DRIFT_PLAYING = 1.5; // seconds of drift tolerated while playing
const DRIFT_PAUSED = 0.5;
const SUPPRESS_MS = 1200; // ignore player events right after we changed the player ourselves

/**
 * Wraps the YouTube IFrame player.
 *
 * Remote -> local: whenever the server sends sync_state we make the player match it.
 * Local -> remote: only for Host/Moderator. Their play/pause/seek are sent to the server,
 *                  which validates and broadcasts to everyone (including back to them).
 *
 * Echo-loop protection: changing the player from code also fires player events, so for a short
 * window after applying remote state those events are ignored.
 */
export default function YouTubePlayer({ sync, canControl, onCommand }: Props) {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const playerRef = useRef<YTPlayer | null>(null);

  const [ready, setReady] = useState(false);
  const [started, setStarted] = useState(false);
  const [volume, setVolume] = useState(80);

  // Refs hold the latest values so long-lived callbacks never go stale.
  const syncRef = useRef(sync);
  syncRef.current = sync;
  const canControlRef = useRef(canControl);
  canControlRef.current = canControl;
  const onCommandRef = useRef(onCommand);
  onCommandRef.current = onCommand;

  const startedRef = useRef(false);
  const currentVideoRef = useRef(sync.videoId);
  const suppressUntil = useRef(0);
  const lastPoll = useRef<{ time: number; at: number; playing: boolean } | null>(null);

  /** Make the local player match the server state. */
  const applySync = useCallback((s: SyncState) => {
    const p = playerRef.current;
    if (!p) return;

    suppressUntil.current = Date.now() + SUPPRESS_MS;
    lastPoll.current = null;

    // Position the server meant, moved forward by the time the message spent in this browser.
    const target =
      s.playState === 'PLAYING' ? s.currentTime + (Date.now() - s.receivedAt) / 1000 : s.currentTime;

    if (s.videoId !== currentVideoRef.current) {
      currentVideoRef.current = s.videoId;
      const opts = { videoId: s.videoId, startSeconds: target };
      if (s.playState === 'PLAYING') p.loadVideoById(opts);
      else p.cueVideoById(opts);
      return;
    }

    const state = p.getPlayerState();
    const diff = Math.abs(p.getCurrentTime() - target);

    if (s.playState === 'PLAYING') {
      if (diff > DRIFT_PLAYING) p.seekTo(target, true);
      if (state !== PS.PLAYING && state !== PS.BUFFERING) p.playVideo();
    } else {
      if (diff > DRIFT_PAUSED) p.seekTo(target, true);
      if (state !== PS.PAUSED || diff > DRIFT_PAUSED) p.pauseVideo();
    }
  }, []);

  /** Host/Moderator pressed something inside the YouTube player. */
  const handleStateChange = useCallback((state: number) => {
    const p = playerRef.current;
    if (!p || !canControlRef.current || !startedRef.current) return;
    if (Date.now() < suppressUntil.current) return;

    const serverPlaying = syncRef.current.playState === 'PLAYING';

    if (state === PS.PLAYING && !serverPlaying) {
      onCommandRef.current('play', { time: p.getCurrentTime() });
    } else if (state === PS.PAUSED) {
      // A seek can briefly report PAUSED first; confirm after a moment that it really stayed paused.
      window.setTimeout(() => {
        const q = playerRef.current;
        if (!q || Date.now() < suppressUntil.current) return;
        if (q.getPlayerState() === PS.PAUSED && syncRef.current.playState === 'PLAYING') {
          onCommandRef.current('pause', { time: q.getCurrentTime() });
        }
      }, 300);
    } else if (state === PS.ENDED && serverPlaying) {
      onCommandRef.current('pause', { time: p.getDuration() });
    }
  }, []);

  // Create the player once.
  useEffect(() => {
    let cancelled = false;
    let player: YTPlayer | null = null;
    const container = containerRef.current;

    loadYouTubeApi().then((YT) => {
      if (cancelled || !container) return;
      const target = document.createElement('div');
      container.appendChild(target);
      player = new YT.Player(target, {
        width: '100%',
        height: '100%',
        videoId: syncRef.current.videoId,
        playerVars: {
          controls: 1,
          rel: 0,
          playsinline: 1,
          modestbranding: 1,
          enablejsapi: 1,
          origin: window.location.origin,
        },
        events: {
          onReady: () => {
            if (cancelled) return;
            playerRef.current = player;
            setReady(true);
          },
          onStateChange: (e) => handleStateChange(e.data),
        },
      });
    });

    return () => {
      cancelled = true;
      playerRef.current = null;
      try {
        player?.destroy();
      } catch {
        /* ignore */
      }
      if (container) container.innerHTML = '';
    };
  }, [handleStateChange]);

  // Apply every new server state.
  useEffect(() => {
    const p = playerRef.current;
    if (!ready || !p) return;
    if (!startedRef.current) {
      // Browsers block autoplay until the user clicks once. Until then just show the right video.
      if (sync.videoId !== currentVideoRef.current) {
        currentVideoRef.current = sync.videoId;
        p.cueVideoById({ videoId: sync.videoId, startSeconds: sync.currentTime });
      }
      return;
    }
    applySync(sync);
  }, [sync.rev, ready, applySync]); // eslint-disable-line react-hooks/exhaustive-deps

  // Host/Moderator: detect scrubbing. The IFrame API has no "seek" event, so compare the
  // player's time with where it should be and treat a jump as a seek.
  useEffect(() => {
    lastPoll.current = null;
    if (!ready || !canControl || !started) return;

    const id = window.setInterval(() => {
      const p = playerRef.current;
      if (!p) return;
      const now = Date.now();
      const time = p.getCurrentTime();
      const st = p.getPlayerState();
      const last = lastPoll.current;

      if (last && now > suppressUntil.current) {
        const playingBoth = last.playing && st === PS.PLAYING;
        const expected = playingBoth ? last.time + (now - last.at) / 1000 : last.time;
        const threshold = playingBoth ? 1.5 : 1.0;
        if (Math.abs(time - expected) > threshold) {
          onCommandRef.current('seek', { time });
        }
      }
      lastPoll.current = { time, at: now, playing: st === PS.PLAYING };
    }, 500);

    return () => window.clearInterval(id);
  }, [ready, canControl, started]);

  const handleStart = () => {
    const p = playerRef.current;
    startedRef.current = true;
    setStarted(true);
    if (p) {
      p.unMute();
      p.setVolume(volume);
      applySync(syncRef.current); // inside the click, so autoplay is allowed
    }
  };

  const handleVolume = (v: number) => {
    setVolume(v);
    const p = playerRef.current;
    if (p) {
      p.setVolume(v);
      if (v > 0) p.unMute();
    }
  };

  const handleFullscreen = () => {
    wrapperRef.current?.requestFullscreen?.().catch(() => undefined);
  };

  return (
    <div className="player-card">
      <div className="player-wrap" ref={wrapperRef}>
        <div className="player-frame" ref={containerRef} />

        {/* Viewers cannot click through to the YouTube controls (the server would ignore them anyway). */}
        {!canControl && started && (
          <div className="player-lock" title="Only the host and moderators control playback" />
        )}

        {!started && (
          <div className="player-start">
            <button className="btn primary big" disabled={!ready} onClick={handleStart}>
              {ready ? 'Join playback' : 'Loading player...'}
            </button>
            <p>Your browser needs one click before it lets the video play.</p>
          </div>
        )}
      </div>

      <div className="player-bar">
        <span className={canControl ? 'pill pill-on' : 'pill'}>
          {canControl ? 'You control playback' : 'View only: playback follows the host'}
        </span>
        <label className="volume">
          Volume
          <input
            type="range"
            min={0}
            max={100}
            value={volume}
            onChange={(e) => handleVolume(Number(e.target.value))}
          />
        </label>
        <button className="btn small" onClick={handleFullscreen}>
          Fullscreen
        </button>
      </div>
    </div>
  );
}
