package org.delightofcomposition.analysis;

public class WindowPeaks {
    private final double[] frequencies;  // Hz
    private final double[] amplitudes;   // normalized 0-1

    public WindowPeaks(double[] frequencies, double[] amplitudes) {
        this.frequencies = frequencies;
        this.amplitudes = amplitudes;
    }

    public int getPeakCount() {
        return frequencies.length;
    }

    public double getFrequency(int index) {
        return frequencies[index];
    }

    public double getAmplitude(int index) {
        return amplitudes[index];
    }
}
