package org.delightofcomposition.realtime;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import org.delightofcomposition.analysis.SpectralAnalysis;
import org.delightofcomposition.sound.WaveWriter;

public class AudioEngine implements Runnable {
    private static final int BUFFER_SIZE = 512; // ~10.7ms at 48kHz

    private final GrainPool grainPool;
    private final Voice[] voices;
    private final ControlState controls;
    private final SpectralAnalysis analysis;
    private final double[] grainSample;
    private final double grainOrigFreq;
    private final double[] sourceSample;
    private volatile boolean running;

    // Pre-allocated buffers to avoid GC in audio thread
    private final double[] mixBuffer;
    private final byte[] byteBuffer;

    // Source playback position tracking (for mix feature)
    private int sourcePlaybackPos;

    public AudioEngine(GrainPool grainPool, Voice[] voices, ControlState controls,
                       SpectralAnalysis analysis, double[] grainSample,
                       double grainOrigFreq, double[] sourceSample) {
        this.grainPool = grainPool;
        this.voices = voices;
        this.controls = controls;
        this.analysis = analysis;
        this.grainSample = grainSample;
        this.grainOrigFreq = grainOrigFreq;
        this.sourceSample = sourceSample;
        this.mixBuffer = new double[BUFFER_SIZE];
        this.byteBuffer = new byte[BUFFER_SIZE * 2]; // 16-bit mono = 2 bytes per sample
        this.sourcePlaybackPos = 0;
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

                double density = controls.getDensity();
                double mix = controls.getMix();

                // Schedule grains for active voices
                for (Voice voice : voices) {
                    if (voice.isActive()) {
                        voice.scheduleGrains(analysis, grainPool, grainSample,
                                grainOrigFreq, density, BUFFER_SIZE);
                    }
                }

                // Render all active grains into mix buffer
                grainPool.renderAll(mixBuffer, 0, BUFFER_SIZE);

                // Blend with source sample based on mix control
                if (mix > 0 && sourceSample != null) {
                    for (int i = 0; i < BUFFER_SIZE; i++) {
                        int srcPos = sourcePlaybackPos + i;
                        double srcSample = 0;
                        if (srcPos >= 0 && srcPos < sourceSample.length) {
                            srcSample = sourceSample[srcPos];
                        }
                        mixBuffer[i] = (1.0 - mix) * mixBuffer[i] + mix * srcSample;
                    }
                    sourcePlaybackPos += BUFFER_SIZE;
                    if (sourcePlaybackPos >= sourceSample.length) {
                        sourcePlaybackPos = 0; // loop source for mix
                    }
                }

                // Soft clip and convert to 16-bit PCM bytes (little-endian)
                for (int i = 0; i < BUFFER_SIZE; i++) {
                    // Soft clip
                    double sample = mixBuffer[i];
                    if (sample > 1.0) sample = 1.0;
                    else if (sample < -1.0) sample = -1.0;

                    int pcm = (int) (sample * Short.MAX_VALUE);
                    byteBuffer[i * 2] = (byte) (pcm & 0xFF);
                    byteBuffer[i * 2 + 1] = (byte) ((pcm >> 8) & 0xFF);
                }

                // This blocks until the audio system consumes the buffer
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

    public int getActiveGrainCount() {
        return grainPool.activeCount();
    }

    public int getActiveVoiceCount() {
        int count = 0;
        for (Voice v : voices) {
            if (v.isActive()) count++;
        }
        return count;
    }
}
