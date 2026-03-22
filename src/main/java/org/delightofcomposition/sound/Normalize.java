package org.delightofcomposition.sound;

public class Normalize {
    public static void normalize(double[] sig) {
        double max = 0;
        for (int i = 0; i < sig.length; i++) {
            max = Math.max(Math.abs(sig[i]), max);
        }
        if (max > 0) {
            for (int i = 0; i < sig.length; i++) {
                sig[i] /= max;
            }
        }
    }
}
