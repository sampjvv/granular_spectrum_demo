package org.delightofcomposition.realtime;

import org.delightofcomposition.analysis.SpectralAnalysis;
import org.delightofcomposition.analysis.WindowPeaks;
import org.delightofcomposition.sound.WaveWriter;

public class Voice {
    private int midiNote;
    private double velocity;
    private double transposeRatio;
    private boolean active;
    private boolean released;

    // Playback position in spectral analysis windows
    private double windowPosition;
    private int samplesElapsed;

    // ADSR envelope
    private static final int ATTACK_SAMPLES = (int) (0.050 * WaveWriter.SAMPLE_RATE);
    private static final int DECAY_SAMPLES = (int) (0.200 * WaveWriter.SAMPLE_RATE);
    private static final double SUSTAIN_LEVEL = 0.7;
    private static final int RELEASE_SAMPLES = (int) (0.500 * WaveWriter.SAMPLE_RATE);

    private enum EnvStage { ATTACK, DECAY, SUSTAIN, RELEASE, OFF }
    private EnvStage envStage;
    private int envSamplesInStage;
    private double envLevel;

    public Voice() {
        this.active = false;
    }

    public void noteOn(int note, int velocity, double sourceFundamental) {
        this.midiNote = note;
        this.velocity = velocity / 127.0;
        double midiFreq = 440.0 * Math.pow(2, (note - 69) / 12.0);
        this.transposeRatio = midiFreq / sourceFundamental;
        this.windowPosition = 0;
        this.samplesElapsed = 0;
        this.envStage = EnvStage.ATTACK;
        this.envSamplesInStage = 0;
        this.envLevel = 0;
        this.released = false;
        this.active = true;
    }

    public void noteOff() {
        if (active && envStage != EnvStage.RELEASE && envStage != EnvStage.OFF) {
            this.released = true;
            this.envStage = EnvStage.RELEASE;
            this.envSamplesInStage = 0;
        }
    }

    public void scheduleGrains(SpectralAnalysis analysis, GrainPool pool,
                                double[] grainSample, double grainOrigFreq,
                                double density, int bufferSize) {
        if (!active || envStage == EnvStage.OFF) return;

        // Advance window position based on elapsed samples
        double controlRateSamples = analysis.getWindowDurationSec() * WaveWriter.SAMPLE_RATE;
        int windowIndex = (int) (samplesElapsed / controlRateSamples);

        // Stop scheduling if we've run past the analysis
        if (windowIndex >= analysis.getWindowCount()) {
            // No more windows, but don't deactivate yet - let release tail play
            samplesElapsed += bufferSize;
            advanceEnvelope(bufferSize);
            return;
        }

        WindowPeaks peaks = analysis.getWindow(windowIndex);
        if (peaks == null) {
            samplesElapsed += bufferSize;
            advanceEnvelope(bufferSize);
            return;
        }

        double currentEnv = envLevel * velocity;

        // For each peak, probabilistically spawn grains (same as Demo.java logic)
        for (int p = 0; p < peaks.getPeakCount(); p++) {
            double peakFreq = peaks.getFrequency(p) * transposeRatio;
            double peakAmp = peaks.getAmplitude(p) * currentEnv;

            // Up to 10 grain attempts per peak per window
            for (int i = 0; i < 10; i++) {
                if (Math.random() < density) {
                    Grain grain = pool.allocate();
                    if (grain == null) return; // pool exhausted

                    double playbackRate = peakFreq / grainOrigFreq;
                    int lifetime = (int) (grainSample.length / playbackRate);
                    if (lifetime > 0) {
                        grain.activate(grainSample, playbackRate, peakAmp, lifetime);
                    }
                }
            }
        }

        samplesElapsed += bufferSize;
        advanceEnvelope(bufferSize);
    }

    private void advanceEnvelope(int samples) {
        for (int i = 0; i < samples; i++) {
            switch (envStage) {
                case ATTACK:
                    envLevel = envSamplesInStage / (double) ATTACK_SAMPLES;
                    envSamplesInStage++;
                    if (envSamplesInStage >= ATTACK_SAMPLES) {
                        envStage = EnvStage.DECAY;
                        envSamplesInStage = 0;
                        envLevel = 1.0;
                    }
                    break;
                case DECAY:
                    envLevel = 1.0 - (1.0 - SUSTAIN_LEVEL) * (envSamplesInStage / (double) DECAY_SAMPLES);
                    envSamplesInStage++;
                    if (envSamplesInStage >= DECAY_SAMPLES) {
                        envStage = EnvStage.SUSTAIN;
                        envSamplesInStage = 0;
                        envLevel = SUSTAIN_LEVEL;
                    }
                    break;
                case SUSTAIN:
                    envLevel = SUSTAIN_LEVEL;
                    break;
                case RELEASE:
                    envLevel = SUSTAIN_LEVEL * (1.0 - envSamplesInStage / (double) RELEASE_SAMPLES);
                    envSamplesInStage++;
                    if (envSamplesInStage >= RELEASE_SAMPLES) {
                        envStage = EnvStage.OFF;
                        envLevel = 0;
                        active = false;
                    }
                    break;
                case OFF:
                    active = false;
                    return;
            }
        }
    }

    public boolean isActive() {
        return active;
    }

    public int getMidiNote() {
        return midiNote;
    }
}
