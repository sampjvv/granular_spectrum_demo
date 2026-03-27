package org.delightofcomposition.sound;

import java.util.Arrays;
import org.apache.commons.math3.transform.*;
import org.apache.commons.math3.complex.Complex;

/**
 * 
 * The math3 dependency can be found at
 * https://repo.maven.apache.org/maven2/org/apache/commons/commons-math3/3.6.1/
 * use the one that doesn't say java-docs or sources or anything
 * 
 * Basic explainations of convolution can be found here:
 * https://cmtext.indiana.edu/synthesis/chapter4_convolution.php
 * 
 * Write a description of class FFT2 here.
 *
 * @author (your name)
 * @version (a version number or a date)
 */
public class FFT2 {
    // returns [amp, phase]
    // freq is implied by index
    // freq = sample_rate * (index + 1) / length
    public static double[][] forwardTransform(double[] data) {
        int po2 = 0;
        while (Math.pow(2, po2) < data.length) {
            po2++;
        }
        data = Arrays.copyOf(data, (int) Math.pow(2, po2));
        double[][] tempConversion = new double[2][data.length];
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        try {
            Complex[] complx = transformer.transform(data, TransformType.FORWARD);

            for (int i = 0; i < complx.length; i++) {
                double rr = (complx[i].getReal());
                double ri = (complx[i].getImaginary());

                tempConversion[0][i] = Math.sqrt((rr * rr) + (ri * ri));// amp
                tempConversion[1][i] = Math.atan2(ri, rr);// phase
            }

        } catch (IllegalArgumentException e) {
            System.out.println(e);
        }
        return tempConversion;
    }

    public static Complex[] forwardTransformComplex(double[] data) {
        int po2 = 0;
        while (Math.pow(2, po2) < data.length) {
            po2++;
        }
        data = Arrays.copyOf(data, (int) Math.pow(2, po2));
        double[][] tempConversion = new double[2][data.length];
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] complx = transformer.transform(data, TransformType.FORWARD);

        return complx;
    }

    public static Complex[] inverseTransform(Complex[] data) {
        int po2 = 0;
        while (Math.pow(2, po2) < data.length) {
            po2++;
        }
        data = Arrays.copyOf(data, (int) Math.pow(2, po2));
        double[][] tempConversion = new double[2][data.length];
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] complx = transformer.transform(data, TransformType.INVERSE);
        /*
         * try {
         * 
         * for (int i = 0; i < complx.length; i++) {
         * double rr = (complx[i].getReal());
         * double ri = (complx[i].getImaginary());
         * 
         * tempConversion[0][i] = Math.sqrt((rr * rr) + (ri * ri));//amp
         * tempConversion[1][i] = Math.atan2(ri, rr);//phase
         * }
         * 
         * } catch (IllegalArgumentException e) {
         * System.out.println(e);
         * }
         */
        return complx;
    }

    public static double getPitch(double[] sig, int sampleRate) {
        double[][] realFreqDom = forwardTransform(sig);
        int N = realFreqDom[0].length;
        int halfN = N / 2; // only search below Nyquist (real signal is symmetric)

        // Skip bin 0 (DC) and very low bins — start from bin corresponding to ~20 Hz
        int minBin = Math.max(1, (int) (20.0 * N / sampleRate));
        double maxAmp = 0;
        int peakBin = minBin;
        for (int i = minBin; i < halfN; i++) {
            if (realFreqDom[0][i] > maxAmp) {
                maxAmp = realFreqDom[0][i];
                peakBin = i;
            }
        }

        // Parabolic interpolation around peak for sub-bin accuracy
        double refinedBin = peakBin;
        if (peakBin > 0 && peakBin < halfN - 1) {
            double alpha = realFreqDom[0][peakBin - 1];
            double beta  = realFreqDom[0][peakBin];
            double gamma = realFreqDom[0][peakBin + 1];
            double denom = alpha - 2 * beta + gamma;
            if (Math.abs(denom) > 1e-12) {
                refinedBin = peakBin + 0.5 * (alpha - gamma) / denom;
            }
        }

        return refinedBin * sampleRate / (double) N;
    }

    // matches the second to the first
    public static double[] matchPitch(double[] refSig, double[] sig) {
        double f1 = 0;
        double f2 = 0;
        double[][] realFreqDom1 = forwardTransform(refSig);
        double[][] realFreqDom2 = forwardTransform(sig);
        double maxAmp = 0;
        for (int i = 0; i < realFreqDom1[0].length; i++) {
            if (realFreqDom1[0][i] > maxAmp) {
                maxAmp = realFreqDom1[0][i];
                f1 = (i + 1) / (double) (realFreqDom1[0].length);
            }
        }

        maxAmp = 0;
        for (int i = 0; i < realFreqDom2[0].length; i++) {
            if (realFreqDom2[0][i] > maxAmp) {
                maxAmp = realFreqDom2[0][i];
                f2 = (i + 1) / (double) (realFreqDom2[0].length);
            }
        }

        double[] processed = new double[(int) (sig.length * f2 / f1)];

        for (int i = 0; i < processed.length; i++) {
            double exInd = i * f1 / f2;
            int index = (int) exInd;
            double fract = exInd - index;
            double frame1 = sig[index];
            double frame2 = frame1;
            if (index + 1 < sig.length)
                frame2 = sig[index + 1];
            processed[i] = frame1 * (1 - fract) + frame2 * fract;
        }

        return processed;
    }

    public static double[] convAsImaginaryProduct(double[] sig1, double[] sig2) {
        Complex[] freqDom1 = forwardTransformComplex(sig1);
        Complex[] freqDom2 = forwardTransformComplex(sig2);
        Complex[] freqProd = new Complex[freqDom1.length];
        for (int i = 0; i < freqDom1.length; i++) {
            double r1 = freqDom1[i].getReal();
            double r2 = freqDom2[i].getReal();
            double i1 = freqDom1[i].getImaginary();
            double i2 = freqDom2[i].getImaginary();
            double rProd = r1 * r2 - i1 * i2;
            double iProd = r1 * i2 + r2 * i1;
            freqProd[i] = new Complex(rProd, iProd);
        }
        Complex[] timeDomain = inverseTransform(freqProd);
        double[] convSig = new double[timeDomain.length];
        for (int i = 0; i < sig1.length; i++) {
            convSig[i] = timeDomain[i].getReal();
        }
        return convSig;
    }

    public static void convTest() {
        double secLength = 20;

        double[] sig1 = ReadSound.readSoundDoubles("resources/other_samples/cathedral.wav");// new double[10 *
        // WaveWriter.SAMPLE_RATE];//ReadSound.readSoundDoubles("cathedral.wav");
         sig1 = Arrays.copyOf(sig1, (int)Math.rint(WaveWriter_mono.SAMPLE_RATE * secLength));

        double[] sig2 = ReadSound.readSoundDoubles("resources/other_samples/bd2.wav");
        sig2 = Arrays.copyOf(sig2, sig1.length);

        double[] convSig = convAsImaginaryProduct(sig1, sig2);

        // normalize
        double max = 0;
        for (int i = 0; i < convSig.length; i++)
            max = Math.max(Math.abs(convSig[i]), max);
        for (int i = 0; i < convSig.length; i++)
            convSig[i] /= max;

        double[] sig3 = ReadSound.readSoundDoubles("resources/other_samples/bell.wav");
        sig3 = Arrays.copyOf(sig3, convSig.length);

        convSig = convAsImaginaryProduct(sig3, convSig);

        // normalize
        max = 0;
        for (int i = 0; i < convSig.length; i++)
            max = Math.max(Math.abs(convSig[i]), max);
        for (int i = 0; i < convSig.length; i++)
            convSig[i] /= max;

        WaveWriter_mono ww = new WaveWriter_mono("resources/other_samples/lowSound");

        for (int i = 0; i < convSig.length; i++) {
            ww.df[0][i] += convSig[i];
        }

        ww.render(1);
    }

    public static void main(String[] args) {
        convTest();
    }

}
