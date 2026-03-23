package org.delightofcomposition.realtime;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import org.delightofcomposition.sound.WaveWriter;

/**
 * Audio output thread that plays back pre-rendered granular layers + source
 * sample, mixed and pitch-shifted per active MIDI voice.
 */
public class AudioEngine implements Runnable {
    private static final int BUFFER_SIZE = 512; // ~10.7ms at 48kHz

    private final Voice[] voices;
    private final ControlState controls;
    private volatile boolean running;

    // Pre-allocated buffers to avoid GC in audio thread
    private final double[] mixBuffer;
    private final byte[] byteBuffer;

    public AudioEngine(Voice[] voices, ControlState controls) {
        this.voices = voices;
        this.controls = controls;
        this.mixBuffer = new double[BUFFER_SIZE];
        this.byteBuffer = new byte[BUFFER_SIZE * 2]; // 16-bit mono = 2 bytes per sample
    }

    /**
     * Update pre-rendered buffers on all voices (can be called after re-render).
     */
    public void setBuffers(double[][] granularLayers, double[] sourceBuffer) {
        for (Voice voice : voices) {
            voice.setBuffers(granularLayers, sourceBuffer);
        }
    }

    @Override
    public void run() {
        running = true;

        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                WaveWriter.SAMPLE_RATE, 16, 1, 2,
                WaveWriter.SAMPLE_RATE, false); // little-endian mono

        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format, BUFFER_SIZE * 4);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, BUFFER_SIZE * 4);
            line.start();

            System.out.println("Audio engine started (48kHz, 16-bit, mono)");

            while (running) {
                // Clear mix buffer
                for (int i = 0; i < BUFFER_SIZE; i++) {
                    mixBuffer[i] = 0;
                }

                double mix = controls.getMix();
                double density = controls.getDensity();

                // Render all active voices into mix buffer
                for (Voice voice : voices) {
                    if (voice.isActive()) {
                        voice.render(mixBuffer, BUFFER_SIZE, mix, density);
                    }
                }

                // Soft clip and convert to 16-bit PCM bytes (little-endian)
                for (int i = 0; i < BUFFER_SIZE; i++) {
                    double sample = mixBuffer[i];
                    if (sample > 1.0) sample = 1.0;
                    else if (sample < -1.0) sample = -1.0;

                    int pcm = (int) (sample * Short.MAX_VALUE);
                    byteBuffer[i * 2] = (byte) (pcm & 0xFF);
                    byteBuffer[i * 2 + 1] = (byte) ((pcm >> 8) & 0xFF);
                }

                line.write(byteBuffer, 0, byteBuffer.length);
            }

            line.drain();
            line.stop();
            line.close();
            System.out.println("Audio engine stopped.");
        } catch (Exception e) {
            System.err.println("Audio engine error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public int getActiveVoiceCount() {
        int count = 0;
        for (Voice v : voices) {
            if (v.isActive()) count++;
        }
        return count;
    }
}
