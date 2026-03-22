package org.delightofcomposition.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * In-app audio playback via SourceDataLine.
 * Plays float[][] stereo buffers at 48kHz, 16-bit.
 */
public class AudioPlayer {

    private volatile boolean playing = false;
    private volatile boolean stopRequested = false;
    private Thread playThread;
    private volatile int currentFrame = 0;
    private volatile Runnable onComplete;

    public void play(float[][] buf, int sampleRate) {
        play(buf, sampleRate, null);
    }

    public void play(float[][] buf, int sampleRate, Runnable onComplete) {
        stop();
        this.onComplete = onComplete;
        stopRequested = false;
        playing = true;
        currentFrame = 0;

        playThread = new Thread(() -> {
            AudioFormat format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, 2, 4, sampleRate, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format, 4096 * 4);
                line.start();

                int chunkFrames = 4096;
                byte[] chunk = new byte[chunkFrames * 4]; // 2 channels * 2 bytes per sample
                int totalFrames = buf[0].length;

                while (currentFrame < totalFrames && !stopRequested) {
                    int framesToWrite = Math.min(chunkFrames, totalFrames - currentFrame);
                    int byteIndex = 0;

                    for (int i = 0; i < framesToWrite; i++) {
                        int frame = currentFrame + i;
                        // Left channel
                        short left = (short) (buf[0][frame] * Short.MAX_VALUE);
                        chunk[byteIndex++] = (byte) (left & 0xFF);
                        chunk[byteIndex++] = (byte) ((left >> 8) & 0xFF);
                        // Right channel
                        short right = (short) (buf[1][frame] * Short.MAX_VALUE);
                        chunk[byteIndex++] = (byte) (right & 0xFF);
                        chunk[byteIndex++] = (byte) ((right >> 8) & 0xFF);
                    }

                    line.write(chunk, 0, framesToWrite * 4);
                    currentFrame += framesToWrite;
                }

                if (!stopRequested) {
                    line.drain();
                }
                line.stop();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                playing = false;
                Runnable cb = this.onComplete;
                if (cb != null) {
                    javax.swing.SwingUtilities.invokeLater(cb);
                }
            }
        }, "AudioPlayer");
        playThread.setDaemon(true);
        playThread.start();
    }

    public void stop() {
        stopRequested = true;
        if (playThread != null) {
            try {
                playThread.join(500);
            } catch (InterruptedException ignored) {
            }
        }
        playing = false;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }
}
