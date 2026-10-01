package com.watchparty;

import static org.assertj.core.api.Assertions.assertThat;

import com.watchparty.model.PlaybackState;
import org.junit.jupiter.api.Test;

class PlaybackStateTest {

    @Test
    void pausedPositionDoesNotMove() throws InterruptedException {
        PlaybackState s = new PlaybackState("abcdefghijk", false, 10);
        Thread.sleep(120);
        assertThat(s.currentPosition()).isEqualTo(10.0);
    }

    @Test
    void playingPositionAdvances() throws InterruptedException {
        PlaybackState s = new PlaybackState("abcdefghijk", false, 10);
        s.play(null);
        Thread.sleep(200);
        assertThat(s.currentPosition()).isGreaterThan(10.1);
    }

    @Test
    void seekAndPauseUseTheGivenTime() {
        PlaybackState s = new PlaybackState("abcdefghijk", true, 0);
        s.seek(50);
        s.pause(55.5);
        assertThat(s.isPlaying()).isFalse();
        assertThat(s.currentPosition()).isEqualTo(55.5);
    }

    @Test
    void changeVideoResetsPositionAndStartsPlaying() {
        PlaybackState s = new PlaybackState("abcdefghijk", false, 99);
        s.changeVideo("zyxwvutsrqp");
        assertThat(s.getVideoId()).isEqualTo("zyxwvutsrqp");
        assertThat(s.isPlaying()).isTrue();
        assertThat(s.currentPosition()).isLessThan(1.0);
    }
}
