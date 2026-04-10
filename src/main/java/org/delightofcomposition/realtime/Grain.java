package org.delightofcomposition.realtime;

/**
 * A single active grain reading from the shared bell sample.
 * Grains are pooled and reused — no allocation during audio rendering.
 */
public class Grain {
    // Fade-out over last ~50ms to prevent clicks when grain lifetime expires
    private static final int FADE_SAMPLES = 2400;
    // Reverse grain envelope
    private static final int REVERSE_FADE_IN_SAMPLES = 12000;  // ~250ms
    private static final int REVERSE_FADE_OUT_SAMPLES = 1200;  // ~25ms

    double position;      // fractional read position in bell sample
    double playbackRate;  // samples to advance per output sample
    double amplitude;     // grain amplitude (from spectral peak)
    boolean active;
    int age;              // output samples rendered so far
    int maxLifetime;      // max output samples before forced fade-out + death

    // Bell sample references (shared, set once per voice)
    double[] bellDry;
    double[] bellWet;
    int bellLength;       // effective length (dry or wet, whichever is in use)
    boolean useReverb;
    double reverbMix;
    boolean reverse;

    public Grain() {
        this.active = false;
    }

    /**
     * Activate this grain with the given parameters.
     */
    public void activate(double playbackRate, double amplitude, int maxLifetime,
                          double[] bellDry, double[] bellWet,
                          boolean useReverb, double reverbMix, boolean reverse) {
        this.position = 0;
        this.playbackRate = playbackRate;
        this.amplitude = amplitude;
        this.maxLifetime = maxLifetime;
        this.age = 0;
        this.bellDry = bellDry;
        this.bellWet = bellWet;
        this.useReverb = useReverb;
        this.reverbMix = reverbMix;
        this.reverse = reverse;
        this.bellLength = useReverb ? Math.min(bellDry.length, bellWet.length) : bellDry.length;
        // Reverse grains: start offset so they always reach the attack at the end
        if (reverse) {
            double traversable = playbackRate * maxLifetime;
            this.position = Math.max(0, bellLength - traversable);
        }
        this.active = true;
    }

    /**
     * Render this grain into the output buffer, advancing position.
     * Grain dies when it reaches maxLifetime or the end of the bell sample.
     * A smooth fade-out is applied over the last FADE_SAMPLES to prevent clicks.
     */
    public boolean renderInto(double[] buffer, int length) {
        if (!active) return false;

        for (int i = 0; i < length; i++) {
            int idx = (int) position;
            if (idx + 1 >= bellLength || age >= maxLifetime) {
                active = false;
                return false;
            }

            double frac = position - idx;

            // Fade envelope
            double env = 1.0;
            if (reverse) {
                // Reverse: long fade-IN at start (swell-up) + fade-OUT at end (no clicks)
                if (age < REVERSE_FADE_IN_SAMPLES) {
                    env = age / (double) REVERSE_FADE_IN_SAMPLES;
                }
                int remaining = maxLifetime - age;
                if (remaining < REVERSE_FADE_OUT_SAMPLES) {
                    env *= remaining / (double) REVERSE_FADE_OUT_SAMPLES;
                }
            } else {
                // Forward: fade-OUT at end only
                int remaining = maxLifetime - age;
                if (remaining < FADE_SAMPLES) {
                    env = remaining / (double) FADE_SAMPLES;
                }
            }

            double sample;
            if (useReverb) {
                double mix = (1.0 - amplitude) * reverbMix;
                double invMix = 1.0 - mix;
                double dry = bellDry[idx] * (1 - frac) + bellDry[idx + 1] * frac;
                double wet = bellWet[idx] * (1 - frac) + bellWet[idx + 1] * frac;
                sample = (invMix * dry + mix * wet) * amplitude * env;
            } else {
                sample = (bellDry[idx] * (1 - frac) + bellDry[idx + 1] * frac) * amplitude * env;
            }

            buffer[i] += sample;
            position += playbackRate;
            age++;
        }

        return true;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        active = false;
    }
}
