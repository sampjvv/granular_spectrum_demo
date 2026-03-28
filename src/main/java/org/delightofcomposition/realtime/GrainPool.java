package org.delightofcomposition.realtime;

/**
 * Fixed-size pool of Grain objects for zero-allocation real-time rendering.
 * When the pool is full, new spawn requests are silently dropped —
 * grains naturally expire as they finish playing the bell sample.
 */
public class GrainPool {
    private final Grain[] grains;
    private final int maxGrains;

    public GrainPool(int maxGrains) {
        this.maxGrains = maxGrains;
        this.grains = new Grain[maxGrains];
        for (int i = 0; i < maxGrains; i++) {
            grains[i] = new Grain();
        }
    }

    /**
     * Spawn a new grain if a free slot is available.
     * Returns true if spawned, false if pool is full.
     */
    public boolean spawn(double playbackRate, double amplitude, int maxLifetime,
                          double[] bellDry, double[] bellWet,
                          boolean useReverb, double reverbMix) {
        for (int i = 0; i < maxGrains; i++) {
            if (!grains[i].isActive()) {
                grains[i].activate(playbackRate, amplitude, maxLifetime,
                        bellDry, bellWet, useReverb, reverbMix);
                return true;
            }
        }
        return false; // pool exhausted
    }

    /**
     * Render all active grains into the output buffer (additive).
     * Grains that finish are automatically deactivated.
     */
    public void renderAll(double[] buffer, int length) {
        for (int i = 0; i < maxGrains; i++) {
            if (grains[i].isActive()) {
                grains[i].renderInto(buffer, length);
            }
        }
    }

    public int activeCount() {
        int count = 0;
        for (int i = 0; i < maxGrains; i++) {
            if (grains[i].isActive()) count++;
        }
        return count;
    }

    /** Deactivate all grains (e.g., on note-off after release). */
    public void clear() {
        for (int i = 0; i < maxGrains; i++) {
            grains[i].deactivate();
        }
    }
}
