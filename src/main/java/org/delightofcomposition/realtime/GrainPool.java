package org.delightofcomposition.realtime;

/**
 * Fixed-size pool of Grain objects for zero-allocation real-time rendering.
 * When the pool is full, new spawn requests are silently dropped —
 * grains naturally expire as they finish playing the bell sample.
 */
public class GrainPool {
    private final Grain[] grains;
    private final int maxGrains;
    private int nextFree = 0; // rotating search start for O(1) average spawn

    public GrainPool(int maxGrains) {
        this.maxGrains = maxGrains;
        this.grains = new Grain[maxGrains];
        for (int i = 0; i < maxGrains; i++) {
            grains[i] = new Grain();
        }
    }

    /**
     * Spawn a new grain if a free slot is available.
     * Uses rotating index for O(1) average-case lookup.
     * Returns true if spawned, false if pool is full.
     */
    public boolean spawn(double playbackRate, double amplitude, int maxLifetime,
                          double[] bellDry, double[] bellWet,
                          boolean useReverb, double reverbMix, boolean reverse) {
        for (int j = 0; j < maxGrains; j++) {
            int i = (nextFree + j) % maxGrains;
            if (!grains[i].isActive()) {
                grains[i].activate(playbackRate, amplitude, maxLifetime,
                        bellDry, bellWet, useReverb, reverbMix, reverse);
                nextFree = (i + 1) % maxGrains;
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
