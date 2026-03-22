package org.delightofcomposition.realtime;

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

    public Grain allocate() {
        for (int i = 0; i < maxGrains; i++) {
            if (!grains[i].isActive()) {
                return grains[i];
            }
        }
        return null; // pool exhausted
    }

    public void renderAll(double[] buffer, int offset, int length) {
        for (int i = 0; i < maxGrains; i++) {
            if (grains[i].isActive()) {
                grains[i].render(buffer, offset, length);
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
}
