package org.delightofcomposition.realtime;

import java.util.Random;

import org.delightofcomposition.envelopes.Envelope;
import org.delightofcomposition.sound.WaveWriter;

/**
 * One MIDI voice — spawns and renders bell grains in real-time from a
 * pre-computed grain schedule, with truly continuous density control,
 * ADSR envelope, dramatic envelope, and stereo panning.
 */
public class Voice {

    private static final int MAX_GRAINS = 2048;

    private int midiNote;
    private double velocity;
    private boolean active;

    // Grain synthesis
    private GrainSchedule schedule;
    private ControlState controls;
    private final Random reverseRng = new Random();
    private final GrainPool grainPool = new GrainPool(MAX_GRAINS);
    private double pitchRatio;       // midiFreq / sourceFundamental
    private int nextEventIndex;      // pointer into schedule events
    private double voiceTimeSec;     // elapsed time in source timeline

    // Source playback (for mix control)
    private double sourcePosition;   // fractional sample index into source
    private double sourceRate;       // playback rate for source at MIDI pitch

    // Grain lifetime cap: 3 seconds preserves bell attack + reverb tail
    // while allowing faster voice recycling for better polyphony
    private static final double MAX_GRAIN_DURATION_SEC = 3.0;
    private static final int MAX_GRAIN_SAMPLES = (int) (MAX_GRAIN_DURATION_SEC * WaveWriter.SAMPLE_RATE);

    // Density rescaling: UI 100% maps to 10% of raw event probability
    private static final double DENSITY_SCALE = 0.1;

    // Dramatic envelope parameters
    private double dramaticFactor;
    private Envelope dramaticEnvShape;
    private double dramaticDenom;

    // Pre-allocated per-block buffers (mono, summed before mix)
    private final double[] grainBuffer = new double[1024];
    private final double[] dramEnvBlock = new double[1024]; // pre-computed dramatic envelope

    // ADSR envelope
    private static final int ATTACK_SAMPLES = (int) (0.050 * WaveWriter.SAMPLE_RATE);
    private static final int DECAY_SAMPLES = (int) (0.200 * WaveWriter.SAMPLE_RATE);
    private static final double SUSTAIN_LEVEL = 0.7;
    private static final int RELEASE_SAMPLES = (int) (0.500 * WaveWriter.SAMPLE_RATE);

    private enum EnvStage { ATTACK, DECAY, SUSTAIN, RELEASE, OFF }
    private EnvStage envStage;
    private int envSamplesInStage;
    private double envLevel;

    // Track total samples for dramatic envelope position
    private double sampleCount;

    public Voice() {
        this.active = false;
    }

    /**
     * Set the grain schedule (called once during setup, shared across voices).
     */
    public void setGrainSchedule(GrainSchedule schedule,
                                  double dramaticFactor, Envelope dramaticEnvShape) {
        this.schedule = schedule;
        this.dramaticFactor = dramaticFactor;
        this.dramaticEnvShape = dramaticEnvShape;
        this.dramaticDenom = (dramaticFactor > 0.001)
                ? (Math.exp(dramaticFactor) - 1) : 1.0;
    }

    public void setControls(ControlState controls) {
        this.controls = controls;
    }

    // Legacy compatibility — not used with grain schedule path
    public void setOctaveBuffers(double[][][] octaveLayers, double[][] octaveSources,
                                  int[] octaveBufferLengths, double[] octaveRootFreqs,
                                  double dramaticFactor, Envelope dramaticEnvShape) {
        this.dramaticFactor = dramaticFactor;
        this.dramaticEnvShape = dramaticEnvShape;
        this.dramaticDenom = (dramaticFactor > 0.001)
                ? (Math.exp(dramaticFactor) - 1) : 1.0;
    }

    public void noteOn(int note, int velocity) {
        if (schedule == null) return;

        this.midiNote = note;
        this.velocity = velocity / 127.0;
        this.sampleCount = 0;
        this.voiceTimeSec = 0;
        this.nextEventIndex = 0;

        double midiFreq = 440.0 * Math.pow(2, (note - 69) / 12.0);
        this.pitchRatio = midiFreq / schedule.getSourceFundamental();

        // Source playback for mix control
        this.sourcePosition = 0;
        this.sourceRate = pitchRatio;

        // Clear any lingering grains from previous note
        grainPool.clear();

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
     * Render this voice into stereo output buffers.
     */
    public void render(double[] outL, double[] outR, int length,
                       double mix, double density, double pan) {
        if (!active || schedule == null) return;

        double sourceDuration = schedule.getSourceDurationSec();

        // ── 0. Wrap schedule when we've passed the source duration ──
        // Must happen BEFORE computing blockEndTimeSec to avoid firing
        // all events at once with a stale large time value
        if (nextEventIndex >= schedule.getEvents().length && voiceTimeSec >= sourceDuration) {
            voiceTimeSec %= sourceDuration;
            nextEventIndex = schedule.findFirstEventAt(voiceTimeSec);
        }

        double blockDurationSec = length / (double) WaveWriter.SAMPLE_RATE;
        double blockEndTimeSec = voiceTimeSec + blockDurationSec;

        // ── 1. Spawn grains for events within this block's time range ──
        double effectiveDensity = density * DENSITY_SCALE;
        double reverseAmt = (controls != null) ? controls.getReverseAmount() : 0.0;
        GrainSchedule.GrainEvent[] events = schedule.getEvents();
        while (nextEventIndex < events.length
                && events[nextEventIndex].time <= blockEndTimeSec) {
            GrainSchedule.GrainEvent event = events[nextEventIndex];
            nextEventIndex++;

            // Density gating: continuous probability threshold (rescaled)
            if (event.threshold >= effectiveDensity) continue;

            // Compute grain playback rate:
            // peakFreq × (midiFreq / sourceFundamental) / bellOrigFreq
            double grainRate = event.frequency * pitchRatio / schedule.getBellOrigFreq();

            // Probabilistic reverse: random threshold against reverseAmount
            boolean reverse = reverseAmt > 0 && reverseRng.nextDouble() < reverseAmt;
            double[] dry = reverse ? schedule.getBellDryReverse() : schedule.getBellDry();
            double[] wet = reverse ? schedule.getBellWetReverse() : schedule.getBellWet();

            grainPool.spawn(grainRate, event.amplitude, MAX_GRAIN_SAMPLES,
                    dry, wet,
                    schedule.isUseReverb(), schedule.getReverbMix(), reverse);
        }

        // ── 2. Render all active grains into mono buffer ──
        for (int i = 0; i < length; i++) grainBuffer[i] = 0;
        grainPool.renderAll(grainBuffer, length);

        // ── 3. Pre-compute dramatic envelope for entire block ──
        boolean useDramatic = dramaticFactor > 0.001 && dramaticEnvShape != null;
        double totalSamplesForSource = sourceDuration * WaveWriter.SAMPLE_RATE;
        if (useDramatic) {
            double posStart = Math.min(1.0, sampleCount / totalSamplesForSource);
            double posEnd = Math.min(1.0, (sampleCount + length) / totalSamplesForSource);
            double dramLinearStart = dramaticEnvShape.getValue(posStart);
            double dramLinearEnd = dramaticEnvShape.getValue(posEnd);
            // Compute at 64-sample granularity and linearly interpolate
            int step = 64;
            double prevVal = (Math.exp(dramaticFactor * dramLinearStart) - 1) / dramaticDenom;
            for (int blockStart = 0; blockStart < length; blockStart += step) {
                int blockEnd = Math.min(blockStart + step, length);
                double frac = (double) blockEnd / length;
                double linear = dramLinearStart + (dramLinearEnd - dramLinearStart) * frac;
                double nextVal = (Math.exp(dramaticFactor * linear) - 1) / dramaticDenom;
                double delta = (nextVal - prevVal) / (blockEnd - blockStart);
                double val = prevVal;
                for (int i = blockStart; i < blockEnd; i++) {
                    dramEnvBlock[i] = val;
                    val += delta;
                }
                prevVal = nextVal;
            }
        }

        // ── 4. Mix, envelope, pan — per sample ──
        double[] sourceBuf = schedule.getSourceSample();

        for (int i = 0; i < length; i++) {
            double granularVal = grainBuffer[i];

            // Read source for mix control
            double sourceVal = 0;
            if (mix > 0) {
                int srcIdx = (int) sourcePosition;
                if (srcIdx + 1 < sourceBuf.length) {
                    double frac = sourcePosition - srcIdx;
                    sourceVal = sourceBuf[srcIdx] * (1 - frac) + sourceBuf[srcIdx + 1] * frac;
                }
                sourcePosition += sourceRate;

                // Source looping with crossfade
                if (controls != null && controls.isSourceLoop()) {
                    int loopEnd = (int) (controls.getSourceLoopEnd() * sourceBuf.length);
                    int loopStart = (int) (controls.getSourceLoopStart() * sourceBuf.length);
                    loopEnd = Math.min(loopEnd, sourceBuf.length - 1);
                    int CF = 2400; // ~50ms crossfade

                    if (srcIdx >= loopEnd - CF && srcIdx < loopEnd) {
                        int dist = loopEnd - srcIdx;
                        double fadeOut = dist / (double) CF;
                        int cfIdx = loopStart + (CF - dist);
                        if (cfIdx + 1 < sourceBuf.length) {
                            double f = sourcePosition - srcIdx;
                            double lv = sourceBuf[cfIdx] * (1 - f) + sourceBuf[cfIdx + 1] * f;
                            sourceVal = sourceVal * fadeOut + lv * (1.0 - fadeOut);
                        }
                    }

                    if (sourcePosition >= loopEnd) {
                        sourcePosition = loopStart + CF;
                    }
                }
            }

            // Blend granular and source
            double blended = (1.0 - mix) * granularVal + mix * sourceVal;

            // Apply dramatic envelope (pre-computed)
            if (useDramatic) {
                blended *= dramEnvBlock[i];
            }

            // Apply ADSR envelope and velocity
            blended *= envLevel * velocity;

            // Stereo pan: 0.0 = left, 0.5 = center, 1.0 = right
            outL[i] += blended * (1.0 - pan);
            outR[i] += blended * pan;

            sampleCount++;

            // Advance envelope
            advanceEnvelope();
            if (envStage == EnvStage.OFF) {
                active = false;
                return;
            }
        }

        voiceTimeSec = blockEndTimeSec;
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

    /** True if voice is in RELEASE stage (candidate for stealing). */
    public boolean isReleasing() {
        return active && envStage == EnvStage.RELEASE;
    }

    /** Total samples rendered by this voice (age for voice-stealing priority). */
    public double getAge() {
        return sampleCount;
    }
}
