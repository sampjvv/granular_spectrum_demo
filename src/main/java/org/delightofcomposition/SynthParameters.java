package org.delightofcomposition;

import java.io.File;
import java.util.Arrays;

import org.delightofcomposition.envelopes.Envelope;

/**
 * Central parameter model for the granular spectrum synthesizer.
 * Replaces all hardcoded constants with configurable values.
 */
public class SynthParameters {

    // Sample files
    public File sourceFile = new File("resources/bowedCello1.wav");
    public File grainFile = new File("resources/bell.wav");
    public File impulseResponseFile = new File("resources/cathedral.wav");

    // Source region selection (fractions of source sample, 0.0–1.0)
    public double sourceStartFraction = 0.0;
    public double sourceEndFraction = 1.0;

    // Grain synth
    public double grainReferenceFreq = 1287;

    // FFT / windowing
    public int windowSizeExponent = 14; // 2^14 = 16384
    public double controlRate = 0.1; // seconds between windows

    // Granular synthesis
    public int grainsPerPeak = 15;
    public double amplitudeThreshold = 0.05;

    // Reverb
    public boolean useReverb = true;
    public double sourceReverbMix = 0.2;
    public double synthReverbMix = 0.3;

    // Output
    public double panSmoothing = 0.5;
    public double crossfadeDuration = 0.5;
    public double dramaticFactor = 0.01;
    public boolean usePalindrome = false;
    public double crossfadeCurve = 0.0;        // -1.0 (concave/fast) to +1.0 (convex/slow), 0 = linear
    public double crossfadeOverlap = 0.0;         // 0.0 = standard crossfade, 1.0 = full overlap plateau

    // Dynamics envelope options
    public boolean dynamicsExponential = false;
    public boolean dynamicsPerVoice = false;

    // Chord mode
    public boolean useChordMode = false;
    public double[] chordRatios = {1, 3, 5};
    public double[] chordAttackTimes = {0, 3, 4};

    // Envelopes
    public Envelope probEnv = new Envelope(
            new double[]{0, 0.6, 0.8, 0.9, 1.0},
            new double[]{0, 0.1, 1, 1, 0});
    public Envelope mixEnv = new Envelope(
            new double[]{0, 0.7, 1.0},
            new double[]{0, 0, 1});
    public Envelope dramaticEnvShape = new Envelope(
            new double[]{0, 0.25, 1},
            new double[]{1, 0.7, 1});

    // For crisp attack mode in chord voices
    public Envelope crispProbEnv = new Envelope(
            new double[]{0, 0.1, 0.6, 0.8, 0.9, 1.0},
            new double[]{1, 0.05, 0.1, 1, 1, 0});
    public Envelope crispMixEnv = new Envelope(
            new double[]{0, 0.7, 1.0},
            new double[]{0, 0, 1});
    public Envelope dynamicsEnv = new Envelope(
            new double[]{0, 1.0},
            new double[]{1, 1});
    public boolean useDynamicsEnv = true;

    // Pitch envelope (values are frequency ratios: 1.0 = no change, 2.0 = octave up, 0.5 = octave down)
    public Envelope pitchEnv = new Envelope(
            new double[]{0, 1.0},
            new double[]{1.0, 1.0});
    public boolean usePitchEnv = false;

    public int getWindowSize() {
        return (int) Math.pow(2, windowSizeExponent);
    }

    /**
     * Create a deep copy for thread-safe rendering.
     * Clones envelope data so the UI can keep editing while rendering.
     */
    public SynthParameters snapshot() {
        SynthParameters copy = new SynthParameters();
        copy.sourceFile = this.sourceFile;
        copy.sourceStartFraction = this.sourceStartFraction;
        copy.sourceEndFraction = this.sourceEndFraction;
        copy.grainFile = this.grainFile;
        copy.impulseResponseFile = this.impulseResponseFile;
        copy.grainReferenceFreq = this.grainReferenceFreq;
        copy.windowSizeExponent = this.windowSizeExponent;
        copy.controlRate = this.controlRate;
        copy.grainsPerPeak = this.grainsPerPeak;
        copy.amplitudeThreshold = this.amplitudeThreshold;
        copy.useReverb = this.useReverb;
        copy.sourceReverbMix = this.sourceReverbMix;
        copy.synthReverbMix = this.synthReverbMix;
        copy.panSmoothing = this.panSmoothing;
        copy.crossfadeDuration = this.crossfadeDuration;
        copy.dramaticFactor = this.dramaticFactor;
        copy.usePalindrome = this.usePalindrome;
        copy.crossfadeCurve = this.crossfadeCurve;
        copy.crossfadeOverlap = this.crossfadeOverlap;
        copy.dynamicsExponential = this.dynamicsExponential;
        copy.dynamicsPerVoice = this.dynamicsPerVoice;
        copy.useChordMode = this.useChordMode;
        copy.chordRatios = Arrays.copyOf(this.chordRatios, this.chordRatios.length);
        copy.chordAttackTimes = Arrays.copyOf(this.chordAttackTimes, this.chordAttackTimes.length);
        copy.probEnv = cloneEnvelope(this.probEnv);
        copy.mixEnv = cloneEnvelope(this.mixEnv);
        copy.dramaticEnvShape = cloneEnvelope(this.dramaticEnvShape);
        copy.crispProbEnv = cloneEnvelope(this.crispProbEnv);
        copy.crispMixEnv = cloneEnvelope(this.crispMixEnv);
        copy.dynamicsEnv = cloneEnvelope(this.dynamicsEnv);
        copy.useDynamicsEnv = this.useDynamicsEnv;
        copy.pitchEnv = cloneEnvelope(this.pitchEnv);
        copy.usePitchEnv = this.usePitchEnv;
        return copy;
    }

    private static Envelope cloneEnvelope(Envelope env) {
        double[] curvesCopy = env.curves != null
                ? Arrays.copyOf(env.curves, env.curves.length) : null;
        return new Envelope(
                Arrays.copyOf(env.times, env.times.length),
                Arrays.copyOf(env.values, env.values.length),
                curvesCopy);
    }
}
