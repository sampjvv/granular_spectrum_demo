package org.delightofcomposition.realtime;

import org.delightofcomposition.envelopes.Envelope;
import org.delightofcomposition.sound.WaveWriter;

/**
 * One MIDI voice — plays back pre-rendered granular layers + source sample
 * with pitch shifting, ADSR envelope, dramatic envelope, and stereo panning.
 */
public class Voice {
    private static final int NUM_LAYERS = 5;

    private int midiNote;
    private double velocity;
    private double playbackRate; // midiFreq / sourceFundamental
    private boolean active;

    // Playback position (fractional sample index into the pre-rendered buffers)
    private double position;

    // References to shared pre-rendered buffers
    private double[][] granularLayers; // [5][samples]
    private double[] sourceBuffer;
    private int bufferLength; // shortest buffer length

    // Dramatic envelope parameters
    private double dramaticFactor;
    private Envelope dramaticEnvShape;
    private double dramaticDenom; // precomputed: (e^factor - 1)

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

    public void setBuffers(double[][] granularLayers, double[] sourceBuffer,
                           double dramaticFactor, Envelope dramaticEnvShape) {
        this.granularLayers = granularLayers;
        this.sourceBuffer = sourceBuffer;
        this.dramaticFactor = dramaticFactor;
        this.dramaticEnvShape = dramaticEnvShape;
        this.dramaticDenom = (dramaticFactor > 0.001)
                ? (Math.pow(Math.E, dramaticFactor) - 1) : 1.0;
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
     * Render this voice into stereo output buffers, blending granular layers
     * and source based on mix, density, and pan controls.
     */
    public void render(double[] outL, double[] outR, int length,
                       double mix, double density, double pan) {
        if (!active || granularLayers == null || sourceBuffer == null) return;

        for (int i = 0; i < length; i++) {
            // Check if we've reached the end of the buffers
            int srcIndex = (int) position;
            if (srcIndex + 1 >= bufferLength) {
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

                // Scale by density: each layer gets 1/5 of the slider range to fade in
                double layerGain = Math.max(0, Math.min(1, (density - layer / (double) NUM_LAYERS) * NUM_LAYERS));
                granularSum += sample * layerGain;
            }

            // Read source with linear interpolation
            double sourceVal = sourceBuffer[srcIndex] * (1 - fract)
                             + sourceBuffer[srcIndex + 1] * fract;

            // Blend granular and source
            double blended = (1.0 - mix) * granularSum + mix * sourceVal;

            // Apply dramatic envelope based on playback position
            if (dramaticFactor > 0.001 && dramaticEnvShape != null) {
                double envPos = position / (double) bufferLength;
                double linear = dramaticEnvShape.getValue(Math.min(1.0, envPos));
                double expFunc = (Math.pow(Math.E, dramaticFactor * linear) - 1) / dramaticDenom;
                blended *= expFunc;
            }

            // Apply ADSR envelope and velocity
            blended *= envLevel * velocity;

            // Stereo pan: 0.0 = left, 0.5 = center, 1.0 = right
            outL[i] += blended * (1.0 - pan);
            outR[i] += blended * pan;

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
