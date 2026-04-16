package org.delightofcomposition;

import org.delightofcomposition.sound.ReadSound;
import org.delightofcomposition.sound.WaveWriter;

public class Main {
    public static void main(String[] args) {
        double[] sample = ReadSound.readSoundDoubles("../samples/Cello/bowedCello1.wav");
        int origlen = sample.length;
        double panSmoothing = 0.5;
        WaveWriter ww = new WaveWriter("experiment1_basic_spectrum");

        double[] sound = Demo.demo(sample);

        for (int i = 0; i < sound.length; i++) {
            // simple linear change in panning, just for fun
            double pan = Math.min(panSmoothing * 0.5 + (1 - panSmoothing) * 0.5 * (i /
                    (double) (origlen)), 1);
            ww.df[0][i] += pan * sound[i];
            ww.df[1][i] += (1 - pan) * sound[i];
        }

        ww.render();
    }
}
