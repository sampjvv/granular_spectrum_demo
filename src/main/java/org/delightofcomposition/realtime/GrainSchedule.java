package org.delightofcomposition.realtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.delightofcomposition.analysis.SpectralAnalysis;
import org.delightofcomposition.analysis.WindowPeaks;
import org.delightofcomposition.sound.Normalize;
import org.delightofcomposition.sound.ReadSound;
import org.delightofcomposition.sound.Reverb;
import org.delightofcomposition.sound.WaveWriter;

/**
 * Pre-computed grain event schedule built from SpectralAnalysis data.
 * Each event describes when and at what frequency a bell grain should spawn.
 * The density control gates events via their threshold value.
 *
 * Also holds the shared bell sample (dry + wet) used by all grains.
 */
public class GrainSchedule {

    // A single grain spawn event
    public static class GrainEvent {
        public final double time;       // seconds into source timeline
        public final double frequency;  // spectral peak frequency (Hz)
        public final double amplitude;  // peak amplitude (0-1)
        public final double threshold;  // random value 0-1 for density gating

        GrainEvent(double time, double frequency, double amplitude, double threshold) {
            this.time = time;
            this.frequency = frequency;
            this.amplitude = amplitude;
            this.threshold = threshold;
        }
    }

    private final GrainEvent[] events;      // sorted by time
    private final double[] bellDry;          // bell sample (normalized)
    private final double[] bellWet;          // bell with reverb (may be longer)
    private final double[] bellDryReverse;   // bell reversed for reverse grain mode
    private final double[] bellWetReverse;   // wet bell reversed
    private final double bellOrigFreq;       // reference frequency of bell sample
    private final double sourceFundamental;  // detected fundamental of source
    private final double[] sourceSample;     // source for mix control
    private final double sourceDurationSec;  // total source duration
    private final double reverbMix;          // reverb blend ratio
    private final boolean useReverb;

    private GrainSchedule(GrainEvent[] events, double[] bellDry, double[] bellWet,
                           double bellOrigFreq, double sourceFundamental,
                           double[] sourceSample, double sourceDurationSec,
                           double reverbMix, boolean useReverb) {
        this.events = events;
        this.bellDry = bellDry;
        this.bellWet = bellWet;
        this.bellDryReverse = trimSilence(reverseArray(bellDry));
        this.bellWetReverse = bellWet != null ? trimSilence(reverseArray(bellWet)) : null;
        this.bellOrigFreq = bellOrigFreq;
        this.sourceFundamental = sourceFundamental;
        this.sourceSample = sourceSample;
        this.sourceDurationSec = sourceDurationSec;
        this.reverbMix = reverbMix;
        this.useReverb = useReverb;
    }

    private static double[] reverseArray(double[] src) {
        double[] rev = new double[src.length];
        for (int i = 0; i < src.length; i++) {
            rev[i] = src[src.length - 1 - i];
        }
        return rev;
    }

    private static double[] trimSilence(double[] samples) {
        double threshold = 0.005;
        int start = 0;
        for (int i = 0; i < samples.length; i++) {
            if (Math.abs(samples[i]) > threshold) {
                start = i;
                break;
            }
        }
        if (start == 0) return samples;
        return java.util.Arrays.copyOfRange(samples, start, samples.length);
    }

    /**
     * Build a grain schedule from spectral analysis data.
     * Mirrors the offline Demo.processWindow() algorithm: for each spectral peak
     * in each analysis window, generates grainsPerPeak grain events at random
     * time offsets within ±controlRate of the window center. Each event gets a
     * random density threshold for continuous density gating.
     *
     * @param analysis       pre-computed spectral analysis of source
     * @param sourceSample   the source audio (region-selected, normalized)
     * @param bellPath       path to bell grain sample
     * @param bellOrigFreq   reference frequency of bell sample
     * @param irPath         path to impulse response for reverb
     * @param useReverb      whether to generate wet bell
     * @param reverbMix      reverb blend ratio (0-1)
     * @param grainsPerPeak  number of grain events per spectral peak per window
     * @return GrainSchedule ready for real-time use
     */
    public static GrainSchedule build(SpectralAnalysis analysis, double[] sourceSample,
                                       String bellPath, double bellOrigFreq,
                                       String irPath, boolean useReverb, double reverbMix,
                                       int grainsPerPeak) {
        long startTime = System.currentTimeMillis();

        // Load and normalize bell sample
        double[] bellDry = ReadSound.readSoundDoubles(bellPath);
        Normalize.normalize(bellDry);
        System.out.println("[GrainSchedule] Bell sample: " + bellDry.length + " samples"
                + " (" + String.format("%.2f", bellDry.length / (double) WaveWriter.SAMPLE_RATE) + "s)");

        // Generate wet bell if reverb enabled
        double[] bellWet = null;
        if (useReverb) {
            bellWet = Reverb.generateWet(bellDry, irPath);
            // Extend dry to match wet length (same as SimpleSynth)
            bellDry = Arrays.copyOf(bellDry, bellWet.length);
            System.out.println("[GrainSchedule] Wet bell: " + bellWet.length + " samples");
        }

        // Build grain events from spectral analysis windows
        // Mirrors Demo.processWindow(): for each peak, spawn grainsPerPeak grains
        // at random times within ±controlRate of window center
        double sourceDuration = sourceSample.length / (double) WaveWriter.SAMPLE_RATE;
        double windowDuration = analysis.getWindowDurationSec();
        int windowCount = analysis.getWindowCount();
        Random rng = new Random(42); // deterministic seed for reproducible schedules

        List<GrainEvent> eventList = new ArrayList<>();

        for (int w = 0; w < windowCount; w++) {
            WindowPeaks peaks = analysis.getWindow(w);
            if (peaks == null) continue;

            // Window center time (matches Demo.processWindow timing)
            double windowTime = (w + 1) * windowDuration;
            // Grain scatter range: ±controlRate around window center
            double scatterStart = windowTime - windowDuration;
            double scatterRange = windowDuration * 2;

            for (int p = 0; p < peaks.getPeakCount(); p++) {
                double freq = peaks.getFrequency(p);
                double amp = peaks.getAmplitude(p);

                // Skip very low frequencies
                if (freq < 30) continue;

                // Spawn grainsPerPeak events per peak, each at a random time
                // within the scatter range (same as offline processWindow)
                for (int g = 0; g < grainsPerPeak; g++) {
                    double grainTime = scatterStart + rng.nextDouble() * scatterRange;
                    grainTime = Math.max(0, Math.min(sourceDuration, grainTime));

                    // Each grain gets its own density threshold for continuous control
                    double threshold = rng.nextDouble();

                    eventList.add(new GrainEvent(grainTime, freq, amp, threshold));
                }
            }
        }

        // Sort by time for efficient sequential scanning during playback
        eventList.sort((a, b) -> Double.compare(a.time, b.time));

        GrainEvent[] events = eventList.toArray(new GrainEvent[0]);

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("[GrainSchedule] " + events.length + " grain events ("
                + grainsPerPeak + " per peak) across "
                + windowCount + " windows, built in " + elapsed + "ms");

        return new GrainSchedule(events, bellDry, bellWet, bellOrigFreq,
                analysis.getSourceFundamentalHz(), sourceSample, sourceDuration,
                reverbMix, useReverb);
    }

    // ── Accessors ──

    public GrainEvent[] getEvents() { return events; }
    public double[] getBellDry() { return bellDry; }
    public double[] getBellWet() { return bellWet; }
    public double[] getBellDryReverse() { return bellDryReverse; }
    public double[] getBellWetReverse() { return bellWetReverse; }
    public double getBellOrigFreq() { return bellOrigFreq; }
    public double getSourceFundamental() { return sourceFundamental; }
    public double[] getSourceSample() { return sourceSample; }
    public double getSourceDurationSec() { return sourceDurationSec; }
    public double getReverbMix() { return reverbMix; }
    public boolean isUseReverb() { return useReverb; }

    /**
     * Find the index of the first event at or after the given time.
     * Uses binary search for efficient seeking.
     */
    public int findFirstEventAt(double timeSec) {
        int lo = 0, hi = events.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (events[mid].time < timeSec) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }
}
