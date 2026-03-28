package org.delightofcomposition.realtime;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import org.delightofcomposition.sound.WaveWriter;

/**
 * Audio output thread that mixes all active MIDI voices into a stereo
 * PCM stream with real-time mix, density, and pan control.
 */
public class AudioEngine implements Runnable {
    private static final int BUFFER_SIZE = 1024; // samples per channel per block

    private final Voice[] voices;
    private final ControlState controls;
    private volatile boolean running;

    // Pre-allocated buffers to avoid GC in audio thread
    private final double[] mixBufferL;
    private final double[] mixBufferR;
    private final byte[] byteBuffer;

    public AudioEngine(Voice[] voices, ControlState controls) {
        this.voices = voices;
        this.controls = controls;
        this.mixBufferL = new double[BUFFER_SIZE];
        this.mixBufferR = new double[BUFFER_SIZE];
        this.byteBuffer = new byte[BUFFER_SIZE * 4]; // 16-bit stereo = 4 bytes per frame
    }

    @Override
    public void run() {
        running = true;

        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                WaveWriter.SAMPLE_RATE, 16, 2, 4,
                WaveWriter.SAMPLE_RATE, false); // little-endian stereo

        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format, BUFFER_SIZE * 8);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, BUFFER_SIZE * 8);
            line.start();

            System.out.println("Audio engine started (48kHz, 16-bit, stereo)");

            while (running) {
                // Clear mix buffers
                for (int i = 0; i < BUFFER_SIZE; i++) {
                    mixBufferL[i] = 0;
                    mixBufferR[i] = 0;
                }

                double mix = controls.getMix();
                double density = controls.getDensity();
                double pan = controls.getPan();

                // Render all active voices into stereo mix buffers
                for (Voice voice : voices) {
                    if (voice.isActive()) {
                        voice.render(mixBufferL, mixBufferR, BUFFER_SIZE, mix, density, pan);
                    }
                }

                // Soft clip and convert to interleaved 16-bit stereo PCM
                for (int i = 0; i < BUFFER_SIZE; i++) {
                    double sampleL = mixBufferL[i];
                    double sampleR = mixBufferR[i];
                    if (sampleL > 1.0) sampleL = 1.0;
                    else if (sampleL < -1.0) sampleL = -1.0;
                    if (sampleR > 1.0) sampleR = 1.0;
                    else if (sampleR < -1.0) sampleR = -1.0;

                    int pcmL = (int) (sampleL * Short.MAX_VALUE);
                    int pcmR = (int) (sampleR * Short.MAX_VALUE);
                    byteBuffer[i * 4]     = (byte) (pcmL & 0xFF);
                    byteBuffer[i * 4 + 1] = (byte) ((pcmL >> 8) & 0xFF);
                    byteBuffer[i * 4 + 2] = (byte) (pcmR & 0xFF);
                    byteBuffer[i * 4 + 3] = (byte) ((pcmR >> 8) & 0xFF);
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
