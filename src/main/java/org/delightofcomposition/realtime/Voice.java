package org.delightofcomposition.realtime;

import org.delightofcomposition.sound.WaveWriter;

/**
 * One MIDI voice — plays back pre-rendered granular layers + source sample
 * with pitch shifting and ADSR envelope.
 */
public class Voice {
    private static final int NUM_LAYERS = 3;

    private int midiNote;
    private double velocity;
    private double playbackRate; // midiFreq / sourceFundamental
    private boolean active;

    // Playback position (fractional sample index into the pre-rendered buffers)
    private double position;

    // References to shared pre-rendered buffers (set by AudioEngine)
    private double[][] granularLayers; // [3][samples]
    private double[] sourceBuffer;
    private int bufferLength; // shortest buffer length

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

    public void setBuffers(double[][] granularLayers, double[] sourceBuffer) {
        this.granularLayers = granularLayers;
        this.sourceBuffer = sourceBuffer;
        // Use shortest buffer to avoid out-of-bounds
        int min = sourceBuffer.length;
        for (double[] layer : granularLayers) {
            min = Math.min(min, layer.length);
        }
        this.bufferLength = min;
    }

    public void noteOn(int note, int velocity, double sourceFundamental) {
        this.midiNote = note;
        this.velocity = velocity / 127.0;
        double midiFreq = 440.0 * Math.pow(2, (note - 69) / 12.0);
        this.playbackRate = midiFreq / sourceFundamental;
        this.position = 0;
        this.envStage = EnvStage.ATTACK;
        this.envSamplesInStage = 0;
        this.envLevel = 0;
        this.active = true;
    }

    public void noteOff() {
        if (active && envStage != EnvStage.RELEASE && envStage != EnvStage.OFF) {
            this.envStage = EnvStage.RELEASE;
            this.envSamplesInStage = 0;
        }
    }

    /**
     * Render this voice into the output buffer, blending granular layers
     * and source based on mix and density controls.
     */
    public void render(double[] outBuffer, int length, double mix, double density) {
        if (!active || granularLayers == null || sourceBuffer == null) return;

        for (int i = 0; i < length; i++) {
            // Check if we've reached the end of the buffers
            int srcIndex = (int) position;
            if (srcIndex + 1 >= bufferLength) {
                // Buffer exhausted — if in sustain, just hold; if releasing, deactivate
                if (envStage == EnvStage.RELEASE || envStage == EnvStage.OFF) {
                    active = false;
                }
                return;
            }

            double fract = position - srcIndex;

            // Read from each granular layer with linear interpolation
            double granularSum = 0;
            for (int layer = 0; layer < NUM_LAYERS; layer++) {
                double[] buf = granularLayers[layer];
                double sample = buf[srcIndex] * (1 - fract) + buf[srcIndex + 1] * fract;

                // Scale by density: layer 0 fades in at 0-0.33, layer 1 at 0.33-0.67, etc.
                double layerGain = Math.max(0, Math.min(1, (density - layer / 3.0) * 3.0));
                granularSum += sample * layerGain;
            }

            // Read source with linear interpolation
            double sourceVal = sourceBuffer[srcIndex] * (1 - fract)
                             + sourceBuffer[srcIndex + 1] * fract;

            // Blend granular and source
            double blended = (1.0 - mix) * granularSum + mix * sourceVal;

            // Apply ADSR envelope and velocity
            blended *= envLevel * velocity;

            outBuffer[i] += blended;

            // Advance playback position
            position += playbackRate;

            // Advance envelope
            advanceEnvelope();
            if (envStage == EnvStage.OFF) {
                active = false;
                return;
            }
        }
    }

    private void advanceEnvelope() {
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
                break;
        }
    }

    public boolean isActive() {
        return active;
    }

    public int getMidiNote() {
        return midiNote;
    }
}
