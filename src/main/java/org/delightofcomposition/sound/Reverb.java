package org.delightofcomposition.sound;

import java.util.Arrays;

public class Reverb {
    public static double[] generateWet(double[] sample) {
        return generateWet(sample, "../samples/cathedral.wav");
    }

    public static double[] generateWet(double[] sample, String irPath) {
        double[] ir = ReadSound.readSoundDoubles(irPath);
        sample = Arrays.copyOf(sample, sample.length + ir.length);
        ir = Arrays.copyOf(ir, sample.length);
        double[] wetSig = FFT2.convAsImaginaryProduct(sample, ir);
        wetSig = Arrays.copyOf(wetSig, sample.length);
        double wMax = 0;
        for (int i = 0; i < wetSig.length; i++) {
            wMax = Math.max(wMax, Math.abs(wetSig[i]));
        }
        for (int i = 0; i < wetSig.length; i++) {
            wetSig[i] /= wMax;
        }
        return wetSig;
    }
}
