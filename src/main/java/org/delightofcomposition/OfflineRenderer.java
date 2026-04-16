package org.delightofcomposition;

import java.util.Arrays;

import org.delightofcomposition.envelopes.Envelope;
import org.delightofcomposition.sound.ReadSound;
import org.delightofcomposition.sound.WaveWriter;

/**
 * Headless offline render pipeline. Extracted from RenderController so the
 * same code path can be driven from both the Swing GUI and the CLI.
 *
 * No Swing imports — safe to call from a headless JVM. Cancellation still
 * goes through the existing {@link Demo#cancelled} volatile flag.
 */
public final class OfflineRenderer {

    /** Progress callback (0..100). May be null at the call site. */
    public interface Progress {
        void update(int pct);
    }

    private OfflineRenderer() {}

    /**
     * Load the source sample from {@code params.sourceFile} and run the full
     * pipeline. Caller is responsible for passing a {@link SynthParameters#snapshot()}
     * if the params may be mutated concurrently.
     */
    public static float[][] render(SynthParameters params, Progress progress) {
        double[] sample = ReadSound.readSoundDoubles(params.sourceFile.getPath());
        return render(sample, params, progress);
    }

    /**
     * Run the pipeline on an already-loaded raw sample. The sample is trimmed
     * by {@code sourceStartFraction}/{@code sourceEndFraction} inside this method.
     */
    public static float[][] render(double[] sample, SynthParameters snap, Progress progress) {
        // Extract selected region
        sample = extractRegion(sample, snap);
        int origlen = sample.length;

        double[] sound = Demo.demo(sample, snap, progress == null ? null : progress::update);
        if (sound == null) return null; // cancelled

        // Dynamics + palindrome interaction
        double[] rawCopy = null;
        if (snap.usePalindrome && snap.useDynamicsEnv) {
            // Dynamics on forward pass, raw on reverse
            rawCopy = Arrays.copyOf(sound, sound.length);
            applyDynamicsToMono(sound, origlen, snap);
        }

        // Palindrome
        if (snap.usePalindrome) {
            double[] pre = (rawCopy != null) ? rawCopy : Arrays.copyOf(sound, sound.length);
            sound = applyPalindrome(sound, pre, sound.length,
                    snap.crossfadeDuration, snap.crossfadeCurve, snap.crossfadeOverlap);
        }

        // Calculate needed buffer length
        int bufLen = sound.length + WaveWriter.SAMPLE_RATE * 5;
        WaveWriter ww = new WaveWriter("_render_temp", bufLen);

        for (int i = 0; i < sound.length; i++) {
            double pan;
            if (snap.usePanEnv && snap.panEnv != null) {
                pan = snap.panEnv.getValue(i / (double) sound.length);
            } else {
                pan = Math.min(
                        snap.panSmoothing * 0.5
                                + (1 - snap.panSmoothing) * 0.5 * (i / (double) sound.length),
                        1);
            }
            ww.df[0][i] += (float) (pan * sound[i]);
            ww.df[1][i] += (float) ((1 - pan) * sound[i]);
        }

        // Post-mix dynamics (only if not already applied for palindrome)
        if (snap.useDynamicsEnv && !snap.usePalindrome)
            applyDynamicsEnvelope(ww.df, snap.dynamicsEnv, origlen, snap);

        return ww.getBuffer();
    }

    static double[] extractRegion(double[] sample, SynthParameters snap) {
        int start = (int) (snap.sourceStartFraction * sample.length);
        int end = (int) (snap.sourceEndFraction * sample.length);
        start = Math.max(0, start);
        end = Math.min(sample.length, end);
        if (end - start < 1000) end = Math.min(sample.length, start + 1000);
        return Arrays.copyOfRange(sample, start, end);
    }

    static void applyDynamicsToMono(double[] sound, int refLen, SynthParameters snap) {
        Envelope env = snap.dynamicsEnv;
        if (env.times == null || env.times.length == 0) return;
        boolean exponential = snap.dynamicsExponential;
        double factor = snap.dramaticFactor;
        double denom = (factor > 0.001) ? (Math.exp(factor) - 1) : 1.0;

        for (int i = 0; i < sound.length; i++) {
            double t = Math.min((double) i / refLen, 1.0);
            double linear = env.getValue(t);
            if (exponential && factor > 0.001) {
                double expGain = (Math.exp(factor * linear) - 1) / denom;
                sound[i] *= expGain;
            } else {
                sound[i] *= linear;
            }
        }
    }

    static double[] applyPalindrome(double[] forwardSound, double[] rawSound,
            int sampleLen, double crossfadeDuration,
            double crossfadeCurve, double overlap) {
        int crossFadeSamples = (int) (crossfadeDuration * WaveWriter.SAMPLE_RATE);
        double[] truncatedRaw = Arrays.copyOf(rawSound, Math.min(rawSound.length, sampleLen));

        // Clamp crossfade to not exceed either buffer
        crossFadeSamples = Math.min(crossFadeSamples, Math.min(forwardSound.length, truncatedRaw.length));

        double[] result = new double[forwardSound.length + truncatedRaw.length - crossFadeSamples];
        double curveExp = Math.exp(-crossfadeCurve);
        double fadeZone = (1.0 - overlap) / 2.0;

        for (int i = 0; i < result.length; i++) {
            if (i < forwardSound.length)
                result[i] = forwardSound[i];
            int framesPastTransition = i - (sampleLen - crossFadeSamples);
            if (framesPastTransition > 0 && framesPastTransition < truncatedRaw.length) {
                double revSample = truncatedRaw[truncatedRaw.length - framesPastTransition];
                double linearPos = crossFadeSamples > 0
                        ? Math.min(1.0, framesPastTransition / (double) crossFadeSamples) : 1.0;

                double fwdGain, revGain;
                if (overlap > 0.001) {
                    // Overlap plateau model
                    if (fadeZone < 0.001) {
                        fwdGain = 1.0; revGain = 1.0;
                    } else {
                        if (linearPos < fadeZone) {
                            revGain = Math.pow(linearPos / fadeZone, curveExp);
                        } else {
                            revGain = 1.0;
                        }
                        if (linearPos < fadeZone + overlap) {
                            fwdGain = 1.0;
                        } else {
                            double f = (linearPos - fadeZone - overlap) / fadeZone;
                            fwdGain = 1.0 - Math.pow(Math.min(1, f), curveExp);
                        }
                    }
                } else {
                    // Classic crossfade with curve shaping
                    double mix = crossFadeSamples > 0 ? Math.pow(linearPos, curveExp) : 1.0;
                    revGain = mix;
                    fwdGain = 1.0 - mix;
                }
                result[i] = fwdGain * result[i] + revGain * revSample;
            }
        }
        return result;
    }

    static void applyDynamicsEnvelope(float[][] df, Envelope env, int refLen, SynthParameters snap) {
        double[] times = env.times;
        double[] values = env.values;
        if (times == null || times.length == 0) return;

        boolean exponential = snap.dynamicsExponential;
        double factor = snap.dramaticFactor;
        double denom = (factor > 0.001) ? (Math.exp(factor) - 1) : 1.0;

        int len = df[0].length;
        int seg = 0;
        double segStartT = times[0];
        double segEndT = times.length > 1 ? times[1] : segStartT;
        double segStartV = values[0];
        double segEndV = times.length > 1 ? values[1] : segStartV;
        double segSpan = segEndT - segStartT;
        double segCurve = env.getCurve(0);

        for (int i = 0; i < len; i++) {
            double t = Math.min((double) i / refLen, 1.0);

            while (seg < times.length - 2 && t >= segEndT) {
                seg++;
                segStartT = times[seg];
                segEndT = times[seg + 1];
                segStartV = values[seg];
                segEndV = values[seg + 1];
                segSpan = segEndT - segStartT;
                segCurve = env.getCurve(seg);
            }

            float gain;
            if (t <= times[0]) {
                gain = (float) values[0];
            } else if (t >= times[times.length - 1]) {
                gain = (float) values[times.length - 1];
            } else {
                double frac = segSpan > 0 ? (t - segStartT) / segSpan : 0;
                double shapedFrac = Envelope.applyCurve(frac, segCurve);
                gain = (float) (segStartV + shapedFrac * (segEndV - segStartV));
            }

            if (exponential && factor > 0.001) {
                gain = (float) ((Math.exp(factor * gain) - 1) / denom);
            }

            df[0][i] *= gain;
            df[1][i] *= gain;
        }
    }
}
