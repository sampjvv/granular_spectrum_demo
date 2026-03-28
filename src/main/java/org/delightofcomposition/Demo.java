package org.delightofcomposition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
     * Computes the average pitch ratio from the pitch envelope by sampling it.
     * Returns 1.0 if pitch envelope is disabled.
     */
    static double computeAvgPitchRatio(Envelope pitchEnv, boolean usePitchEnv) {
        if (!usePitchEnv || pitchEnv == null) return 1.0;
        double sum = 0;
        int N = 200;
        for (int i = 0; i <= N; i++) {
            sum += pitchEnv.getValue(i / (double) N);
        }
        double avg = sum / (N + 1);
        return Math.max(0.01, avg); // safety: avoid division by zero
    }

    /**
     * Fully parameterized overload. All constants come from params.
     * progress callback receives percentage (0-100). Can be null.
     */
    public static double[] demo(double[] fullSound, SynthParameters params, Consumer<Integer> progress) {
        cancelled = false;

        int sourceLen = fullSound.length;
        double avgPitchRatio = computeAvgPitchRatio(params.pitchEnv, params.usePitchEnv);
        int outputLen = (int) (sourceLen / avgPitchRatio);
        outputLen = Math.max(outputLen, WaveWriter.SAMPLE_RATE); // minimum 1 second

        // Allocate output buffer sized to output + generous margin for reverb tail
        int outLength = outputLen + WaveWriter.SAMPLE_RATE * 30; // 30s margin
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
        Envelope pitchEnv = params.pitchEnv;
        boolean usePitchEnv = params.usePitchEnv;
        String irPath = params.impulseResponseFile.getPath();

        double totalDuration = sourceLen / (double) WaveWriter.SAMPLE_RATE;
        final double[] sourceSound = fullSound;
        final double[] sharedBuf = outSig;
        final int finalOutputLen = outputLen;

        // Build list of window times (based on source duration)
        List<Double> windowTimes = new ArrayList<>();
        for (double time = controlRate; time < totalDuration - controlRate; time += controlRate) {
            windowTimes.add(time);
        }
        int totalWindows = windowTimes.size();

        // Pre-compute window function (same for every window)
        double[] windowFunc = new double[windowSize];
        for (int i = 0; i < windowSize; i++) {
            windowFunc[i] = (2 - (Math.cos(Math.PI * 2 * i / (double) (windowSize - 1)) + 1)) / 2.0;
        }

        // Parallel rendering — shared output buffer, synchronized grain writes
        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        AtomicInteger completedWindows = new AtomicInteger(0);

        try {
            int batchSize = (totalWindows + nThreads - 1) / nThreads;
            List<Future<Void>> futures = new ArrayList<>();

            for (int b = 0; b < nThreads; b++) {
                int from = b * batchSize;
                int to = Math.min(from + batchSize, totalWindows);
                if (from >= totalWindows) break;

                final int batchFrom = from;
                final int batchTo = to;

                futures.add(pool.submit(() -> {
                    for (int idx = batchFrom; idx < batchTo; idx++) {
                        if (cancelled) return null;

                        double time = windowTimes.get(idx);
                        Random rng = new Random(idx);

                        processWindow(time, sourceSound, synth, windowSize, windowFunc,
                                controlRate, amplitudeThreshold, grainsPerPeak,
                                probEnv, pitchEnv, usePitchEnv, sharedBuf, rng,
                                finalOutputLen);

                        int done = completedWindows.incrementAndGet();
                        if (progress != null) {
                            progress.accept((int) (100.0 * done / totalWindows));
                        }
                    }
                    return null;
                }));
            }

            // Wait for all threads (no merge needed — writes go directly to sharedBuf)
            for (Future<Void> f : futures) {
                f.get();
                if (cancelled) return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            return null;
        } finally {
            pool.shutdownNow();
        }

        if (cancelled) return null;

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

        // Apply pitch envelope to source audio via time-varying resampling
        // Resample into outputLen samples (pitch ratio changes read speed)
        if (params.usePitchEnv && params.pitchEnv != null) {
            double[] pitchedSource = new double[outputLen];
            double readPointer = 0.0;
            for (int i = 0; i < outputLen; i++) {
                double ratio = params.pitchEnv.getValue(i / (double) outputLen);
                int index = (int) readPointer;
                if (index >= fullSound.length - 1) break;
                double fract = readPointer - index;
                pitchedSource[i] = fullSound[index] * (1 - fract)
                        + fullSound[Math.min(index + 1, fullSound.length - 1)] * fract;
                readPointer += ratio;
            }
            fullSound = pitchedSource;
        }

        // Mix granular and source across outputLen
        for (int i = 0; i < outputLen && i < fullSound.length; i++) {
            double percComplete = i / (double) outputLen;
            double mix = mixEnv.getValue(percComplete);
            outSig[i] = (float) (((1 - mix) * outSig[i]) + (mix * fullSound[i]));
        }

        outSig = Arrays.copyOf(outSig, outputLen);

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
            int windowSize, double[] windowFunc, double controlRate, double amplitudeThreshold,
            int grainsPerPeak, Envelope probEnv, Envelope pitchEnv, boolean usePitchEnv,
            double[] outBuf, Random rng, int outputLen) {

        double timeScale = outputLen / (double) fullSound.length;
        int fftStart = (int) (time * WaveWriter.SAMPLE_RATE) - windowSize / 2;
        int fftEnd = (int) (time * WaveWriter.SAMPLE_RATE) + windowSize / 2;
        fftStart = Math.max(0, fftStart);
        fftEnd = Math.min(fullSound.length, fftEnd);
        double[] fftSample = Arrays.copyOfRange(fullSound, fftStart, fftEnd);

        for (int i = 0; i < fftSample.length; i++) {
            fftSample[i] *= windowFunc[Math.min(i, windowFunc.length - 1)];
        }

        double[][] spec = FFT.getTransformRaw(fftSample);
        int halfLen = spec[0].length / 2 + 1;

        int firstSoundingFrame = (int) ((time - controlRate) * (double) WaveWriter.SAMPLE_RATE * timeScale);
        int lastFrame = (int) ((time + controlRate) * (double) WaveWriter.SAMPLE_RATE * timeScale);
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

                double prob = probEnv.getValue(t / (double) outputLen);

                if (rng.nextDouble() < prob) {
                    double actualFreq = peakFreq;
                    if (usePitchEnv && pitchEnv != null) {
                        double ratio = pitchEnv.getValue(t / (double) outputLen);
                        actualFreq = peakFreq * ratio;
                    }
                    double[] tone = synth.note(actualFreq, peakAmp);
                    synchronized (outBuf) {
                        for (int j = 0; j < tone.length && (j + t) < outBuf.length; j++) {
                            outBuf[j + t] += tone[j];
                        }
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
        return renderGranularLayer(fullSound, params, seedOffset, layerDensity, progress, null);
    }

    /**
     * Overload that accepts an external cancellation flag, avoiding the shared
     * static Demo.cancelled field. If cancelFlag is null, falls back to Demo.cancelled.
     */
    public static double[] renderGranularLayer(double[] fullSound, SynthParameters params,
                                                long seedOffset, double layerDensity,
                                                Consumer<Integer> progress,
                                                AtomicBoolean cancelFlag) {
        // Use external cancel flag if provided, otherwise fall back to static field
        final boolean useExternalCancel = (cancelFlag != null);
        if (!useExternalCancel) cancelled = false;

        int sourceLen = fullSound.length;
        double avgPitchRatio = computeAvgPitchRatio(params.pitchEnv, params.usePitchEnv);
        int outputLen = (int) (sourceLen / avgPitchRatio);
        outputLen = Math.max(outputLen, WaveWriter.SAMPLE_RATE);

        int outLength = outputLen + WaveWriter.SAMPLE_RATE * 30;
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
        Envelope pitchEnv = params.pitchEnv;
        boolean usePitchEnv = params.usePitchEnv;

        double totalDuration = sourceLen / (double) WaveWriter.SAMPLE_RATE;
        final double[] sourceSound = fullSound;
        final double[] sharedBuf = outSig;
        final int finalOutputLen = outputLen;

        List<Double> windowTimes = new ArrayList<>();
        for (double time = controlRate; time < totalDuration - controlRate; time += controlRate) {
            windowTimes.add(time);
        }
        int totalWindows = windowTimes.size();

        // Pre-compute window function
        double[] windowFunc = new double[windowSize];
        for (int i = 0; i < windowSize; i++) {
            windowFunc[i] = (2 - (Math.cos(Math.PI * 2 * i / (double) (windowSize - 1)) + 1)) / 2.0;
        }

        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        AtomicInteger completedWindows = new AtomicInteger(0);

        try {
            int batchSize = (totalWindows + nThreads - 1) / nThreads;
            List<Future<Void>> futures = new ArrayList<>();

            for (int b = 0; b < nThreads; b++) {
                int from = b * batchSize;
                int to = Math.min(from + batchSize, totalWindows);
                if (from >= totalWindows) break;

                final int batchFrom = from;
                final int batchTo = to;

                futures.add(pool.submit(() -> {
                    for (int idx = batchFrom; idx < batchTo; idx++) {
                        if (useExternalCancel ? cancelFlag.get() : cancelled) return null;

                        double time = windowTimes.get(idx);
                        Random rng = new Random(idx + seedOffset);

                        processWindow(time, sourceSound, synth, windowSize, windowFunc,
                                controlRate, amplitudeThreshold, grainsPerPeak,
                                probEnv, pitchEnv, usePitchEnv, sharedBuf, rng,
                                finalOutputLen);

                        int done = completedWindows.incrementAndGet();
                        if (progress != null) {
                            progress.accept((int) (100.0 * done / totalWindows));
                        }
                    }
                    return null;
                }));
            }

            for (Future<Void> f : futures) {
                f.get();
                if (useExternalCancel ? cancelFlag.get() : cancelled) return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            return null;
        } finally {
            pool.shutdownNow();
        }

        if (useExternalCancel ? cancelFlag.get() : cancelled) return null;

        // Trim to output length and normalize — no source mix applied
        outSig = Arrays.copyOf(outSig, outputLen);
        Normalize.normalize(outSig);

        if (progress != null) progress.accept(100);
        return outSig;
    }
}
