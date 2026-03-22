package org.delightofcomposition.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.delightofcomposition.sound.FFT;
import org.delightofcomposition.sound.FFT2;
import org.delightofcomposition.sound.WaveWriter;
import org.delightofcomposition.util.ProgressBar;

public class SpectralAnalysis {
    private final List<WindowPeaks> windows;
    private final double windowDurationSec;
    private final double sourceFundamentalHz;

    private SpectralAnalysis(List<WindowPeaks> windows, double windowDurationSec, double sourceFundamentalHz) {
        this.windows = windows;
        this.windowDurationSec = windowDurationSec;
        this.sourceFundamentalHz = sourceFundamentalHz;
    }

    public static SpectralAnalysis analyze(double[] sourceSample, int windowSize, double controlRate) {
        // Auto-detect fundamental frequency
        double fundamental = FFT2.getPitch(sourceSample, WaveWriter.SAMPLE_RATE);
        System.out.println("Detected source fundamental: " + fundamental + " Hz");

        List<WindowPeaks> windows = new ArrayList<>();

        for (double time = controlRate;
             time < (sourceSample.length / (double) WaveWriter.SAMPLE_RATE) - controlRate;
             time += controlRate) {

            int fftStart = (int) (time * WaveWriter.SAMPLE_RATE) - windowSize / 2;
            int fftEnd = (int) (time * WaveWriter.SAMPLE_RATE) + windowSize / 2;
            fftStart = Math.max(0, fftStart);
            fftEnd = Math.min(sourceSample.length, fftEnd);
            double[] fftSample = Arrays.copyOfRange(sourceSample, fftStart, fftEnd);

            // Apply cosine window function (same as Demo.java)
            for (int i = 0; i < fftSample.length; i++) {
                double windowFunction = (2 - (Math.cos(Math.PI * 2 * i / (double) (windowSize - 1)) + 1)) / 2.0;
                fftSample[i] *= windowFunction;
            }

            ArrayList<double[]> spec = FFT.getSpectrumWPhase(fftSample, true);

            // Find peaks (same logic as Demo.java)
            ArrayList<Double> peakFreqs = new ArrayList<>();
            ArrayList<Double> peakAmps = new ArrayList<>();
            int index = 1;

            while (index < spec.size() - 1) {
                if (spec.get(index)[1] > 0.05
                        && spec.get(index)[1] > spec.get(index - 1)[1]
                        && spec.get(index)[1] > spec.get(index + 1)[1]) {
                    peakFreqs.add(spec.get(index)[0] * WaveWriter.SAMPLE_RATE);
                    peakAmps.add(spec.get(index)[1]);
                }
                index++;
            }

            double[] freqs = peakFreqs.stream().mapToDouble(Double::doubleValue).toArray();
            double[] amps = peakAmps.stream().mapToDouble(Double::doubleValue).toArray();
            windows.add(new WindowPeaks(freqs, amps));

            int p = (int) (100 * time / (sourceSample.length / (double) WaveWriter.SAMPLE_RATE));
            ProgressBar.printProgressBar(p, 100, "Analyzing Spectrum");
        }

        System.out.println("\nSpectral analysis complete: " + windows.size() + " windows");
        return new SpectralAnalysis(windows, controlRate, fundamental);
    }

    public WindowPeaks getWindow(int index) {
        if (index < 0 || index >= windows.size()) return null;
        return windows.get(index);
    }

    public int getWindowCount() {
        return windows.size();
    }

    public double getWindowDurationSec() {
        return windowDurationSec;
    }

    public double getSourceFundamentalHz() {
        return sourceFundamentalHz;
    }
}
