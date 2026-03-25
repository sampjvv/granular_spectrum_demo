package org.delightofcomposition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.delightofcomposition.envelopes.Envelope;
import org.delightofcomposition.sound.FFT;
import org.delightofcomposition.sound.Normalize;
import org.delightofcomposition.sound.ReadSound;
import org.delightofcomposition.sound.Reverb;
import org.delightofcomposition.sound.WaveWriter;
import org.delightofcomposition.synth.SimpleSynth;
import org.delightofcomposition.synth.Synth;
import org.delightofcomposition.util.ProgressBar;

public class Demo {

    /*
     * In this demo, we will use granular synthesis to create a rich
     * spectralist texture made out of many bell sounds that morphs
     * into the sound of a single cello note. In so doing, we make
     * a musical commontary on the perennial metaphyisical question of
     * the one and the many.
     * 
     * First we divide the cello sound into many windows lasting 2^14
     * samples. These windows overlap. They are spaced at intervals of
     * 1 tenth of a second, but they last about 0.3 seconds each.
     * 
     * Next, for each window, we multiply by a hamming-like function
     * to prevent 'spectral leakage' (through experimentation, it turns
     * out cosine works better than classic hamming function).
     * 
     * Next, for each window, we find peaks in the spectrograph, simply
     * by identifying values for which the amplitude of the next higher
     * or lower value are both lower.
     * 
     * For each peak value we play up to 10 grains at that frequency and
     * amplitude at random points within the time window.
     * 
     * We use a density envelope to move between individual grains heard
     * as distinct notes (low denisty) and a single spectrum (high density).
     * For added effect, we also fade between this whole granular spectrum
     * and the original cello sample.
     */

    public static double[] demo(double[] fullSound, Envelope probEnv, Envelope mixEnv) {
        SynthParameters defaults = new SynthParameters();
        defaults.probEnv = probEnv;
        defaults.mixEnv = mixEnv;
        return demo(fullSound, defaults, null);
    }

    /**
     * Cancellation flag — set to true from another thread to abort rendering.
     */
    public static volatile boolean cancelled = false;

    /**
     * Fully parameterized overload. All constants come from params.
     * progress callback receives percentage (0-100). Can be null.
     */
    public static double[] demo(double[] fullSound, SynthParameters params, Consumer<Integer> progress) {
        cancelled = false;

        // Allocate output buffer sized to source + generous margin for reverb tail
        int outLength = fullSound.length + WaveWriter.SAMPLE_RATE * 30; // 30s margin
        double[] outSig = new double[outLength];

        // A synth to make the grains
        Synth synth = new SimpleSynth(
                params.grainFile.getPath(),
                params.grainReferenceFreq,
                params.useReverb,
                params.synthReverbMix,
                params.impulseResponseFile.getPath());

        int windowSize = params.getWindowSize();
        double controlRate = params.controlRate;
        double amplitudeThreshold = params.amplitudeThreshold;
        int grainsPerPeak = params.grainsPerPeak;
        Envelope probEnv = params.probEnv;
        Envelope mixEnv = params.mixEnv;
        String irPath = params.impulseResponseFile.getPath();

        double totalDuration = fullSound.length / (double) WaveWriter.SAMPLE_RATE;
        final double[] sourceSound = fullSound;

        // Build list of window times
        List<Double> windowTimes = new ArrayList<>();
        for (double time = controlRate; time < totalDuration - controlRate; time += controlRate) {
            windowTimes.add(time);
        }
        int totalWindows = windowTimes.size();

        // Parallel rendering
        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        AtomicInteger completedWindows = new AtomicInteger(0);

        try {
            int batchSize = (totalWindows + nThreads - 1) / nThreads;
            List<Future<double[]>> futures = new ArrayList<>();

            for (int b = 0; b < nThreads; b++) {
                int from = b * batchSize;
                int to = Math.min(from + batchSize, totalWindows);
                if (from >= totalWindows) break;

                final int batchFrom = from;
                final int batchTo = to;

                futures.add(pool.submit(() -> {
                    double[] localBuf = new double[outLength];
                    for (int idx = batchFrom; idx < batchTo; idx++) {
                        if (cancelled) return localBuf;

                        double time = windowTimes.get(idx);
                        Random rng = new Random(idx);

                        processWindow(time, sourceSound, synth, windowSize,
                                controlRate, amplitudeThreshold, grainsPerPeak,
                                probEnv, localBuf, rng);

                        int done = completedWindows.incrementAndGet();
                        if (progress != null) {
                            progress.accept((int) (100.0 * done / totalWindows));
                        }
                    }
                    return localBuf;
                }));
            }

            // Merge thread-local buffers
            for (Future<double[]> f : futures) {
                double[] buf = f.get();
                if (cancelled) return null;
                for (int i = 0; i < outSig.length; i++) {
                    outSig[i] += buf[i];
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            return null;
        } finally {
            pool.shutdownNow();
        }

        if (cancelled) return null;

        int origLen = fullSound.length;

        // apply reverb to original sample
        double[] wet = params.useReverb ? Reverb.generateWet(fullSound, irPath) : fullSound;
        Normalize.normalize(fullSound);
        if (params.useReverb) {
            Normalize.normalize(wet);
            fullSound = Arrays.copyOf(fullSound, Math.max(wet.length, fullSound.length));
            double reverbMix = params.sourceReverbMix;
            for (int i = 0; i < fullSound.length; i++) {
                fullSound[i] = reverbMix * wet[i] + (1 - reverbMix) * fullSound[i];
            }
        }

        Normalize.normalize(outSig);
        Normalize.normalize(fullSound);

        for (int i = 0; i < fullSound.length; i++) {
            double percComplete = i / (double) origLen;
            double mix = mixEnv.getValue(percComplete);
            outSig[i] = (float) (((1 - mix) * outSig[i]) + (mix * fullSound[i]));
        }

        outSig = Arrays.copyOf(outSig, fullSound.length);

        if (progress != null) progress.accept(100);
        return outSig;
    }

    /*
     * Overload with default envelopes
     */

    public static double[] demo(double[] fullSound) {
        // one envelope to control the density of the granular synthesis
        // another to control the mix between the granular texture and the original
        // cello
        // sample.
        // N.b. these envelopes are defined with an 2 arrs of doubles, the time for each
        // node
        // (from 0 - 1, with 1 being the complete dur of the cello sample), and the
        // hight of
        // each node (also 0 - 1)
        Envelope probEnv = new Envelope(new double[] { 0, 0.6, 0.8, 0.9, 0.91 }, new double[] { 0, 0.1, 1, 1, 0 });
        Envelope mixEnv = new Envelope(new double[] { 0, 0.7, 0.9 }, new double[] { 0, 0, 1 });
        return demo(fullSound, probEnv, mixEnv);
    }

    private static void processWindow(double time, double[] fullSound, Synth synth,
            int windowSize, double controlRate, double amplitudeThreshold,
            int grainsPerPeak, Envelope probEnv, double[] outBuf, Random rng) {

        int fftStart = (int) (time * WaveWriter.SAMPLE_RATE) - windowSize / 2;
        int fftEnd = (int) (time * WaveWriter.SAMPLE_RATE) + windowSize / 2;
        fftStart = Math.max(0, fftStart);
        fftEnd = Math.min(fullSound.length, fftEnd);
        double[] fftSample = Arrays.copyOfRange(fullSound, fftStart, fftEnd);

        for (int i = 0; i < fftSample.length; i++) {
            double windowFunction = (2 - (Math.cos(Math.PI * 2 * i / (double) (windowSize - 1)) + 1)) / 2.0;
            fftSample[i] *= windowFunction;
        }

        double[][] spec = FFT.getTransformRaw(fftSample);
        int halfLen = spec[0].length / 2 + 1;

        int firstSoundingFrame = (int) ((time - controlRate) * (double) WaveWriter.SAMPLE_RATE);
        int lastFrame = (int) ((time + controlRate) * (double) WaveWriter.SAMPLE_RATE);
        int totSounding = lastFrame - firstSoundingFrame;
        int index = 1;

        while (index < halfLen - 1) {
            double peakFreq = 0;
            double peakAmp = 0;
            while (index < halfLen - 1 && peakFreq == 0) {
                if (spec[0][index] > amplitudeThreshold
                        && spec[0][index] > spec[0][index - 1]
                        && spec[0][index] > spec[0][index + 1]) {
                    peakAmp = spec[0][index];
                    peakFreq = index / (double) spec[0].length * WaveWriter.SAMPLE_RATE;
                }
                index++;
            }

            if (peakAmp == 0)
                continue;

            for (int i = 0; i < grainsPerPeak; i++) {
                double n = totSounding * rng.nextDouble();
                int t = (int) (firstSoundingFrame + n);

                double prob = probEnv.getValue(t / (double) fullSound.length);

                if (rng.nextDouble() < prob) {
                    double[] tone = synth.note(peakFreq, peakAmp);
                    for (int j = 0; j < tone.length && (j + t) < outBuf.length; j++) {
                        outBuf[j + t] += tone[j];
                    }
                }
            }
        }
    }

    public static double[] demoCrispAttack(double[] fullSound) {
        Envelope probEnv = new Envelope(new double[] { 0, 0.1, 0.6, 0.8, 0.9, 0.91 },
                new double[] { 1, 0.05, 0.1, 1, 1, 0 });
        Envelope mixEnv = new Envelope(new double[] { 0, 0.7, 0.9 }, new double[] { 0, 0, 1 });
        return demo(fullSound, probEnv, mixEnv);
    }

    /**
     * Renders a single granular texture layer (pure granular, no source mix).
     * The layerDensity controls grain placement probability (e.g. 0.05 for a sparse
     * layer, 0.30 for a dense one). Multiple layers with different seeds and
     * densities stack to full density. The seedOffset shifts the random seed
     * per window so each layer gets different grain placements.
     */
    public static double[] renderGranularLayer(double[] fullSound, SynthParameters params,
                                                long seedOffset, double layerDensity,
                                                Consumer<Integer> progress) {
        cancelled = false;

        int outLength = fullSound.length + WaveWriter.SAMPLE_RATE * 30;
        double[] outSig = new double[outLength];

        Synth synth = new SimpleSynth(
                params.grainFile.getPath(),
                params.grainReferenceFreq,
                params.useReverb,
                params.synthReverbMix,
                params.impulseResponseFile.getPath());

        int windowSize = params.getWindowSize();
        double controlRate = params.controlRate;
        double amplitudeThreshold = params.amplitudeThreshold;
        int grainsPerPeak = params.grainsPerPeak;
        Envelope probEnv = new Envelope(new double[]{0, 1}, new double[]{layerDensity, layerDensity});

        double totalDuration = fullSound.length / (double) WaveWriter.SAMPLE_RATE;
        final double[] sourceSound = fullSound;

        List<Double> windowTimes = new ArrayList<>();
        for (double time = controlRate; time < totalDuration - controlRate; time += controlRate) {
            windowTimes.add(time);
        }
        int totalWindows = windowTimes.size();

        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        AtomicInteger completedWindows = new AtomicInteger(0);

        try {
            int batchSize = (totalWindows + nThreads - 1) / nThreads;
            List<Future<double[]>> futures = new ArrayList<>();

            for (int b = 0; b < nThreads; b++) {
                int from = b * batchSize;
                int to = Math.min(from + batchSize, totalWindows);
                if (from >= totalWindows) break;

                final int batchFrom = from;
                final int batchTo = to;

                futures.add(pool.submit(() -> {
                    double[] localBuf = new double[outLength];
                    for (int idx = batchFrom; idx < batchTo; idx++) {
                        if (cancelled) return localBuf;

                        double time = windowTimes.get(idx);
                        Random rng = new Random(idx + seedOffset);

                        processWindow(time, sourceSound, synth, windowSize,
                                controlRate, amplitudeThreshold, grainsPerPeak,
                                probEnv, localBuf, rng);

                        int done = completedWindows.incrementAndGet();
                        if (progress != null) {
                            progress.accept((int) (100.0 * done / totalWindows));
                        }
                    }
                    return localBuf;
                }));
            }

            for (Future<double[]> f : futures) {
                double[] buf = f.get();
                if (cancelled) return null;
                for (int i = 0; i < outSig.length; i++) {
                    outSig[i] += buf[i];
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            return null;
        } finally {
            pool.shutdownNow();
        }

        if (cancelled) return null;

        // Trim to source length and normalize — no source mix applied
        outSig = Arrays.copyOf(outSig, fullSound.length);
        Normalize.normalize(outSig);

        if (progress != null) progress.accept(100);
        return outSig;
    }
}
