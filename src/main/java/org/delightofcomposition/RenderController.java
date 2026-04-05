package org.delightofcomposition;

import java.util.Arrays;
import java.util.List;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import org.delightofcomposition.envelopes.Envelope;
import org.delightofcomposition.sound.ChangeSpeed;
import org.delightofcomposition.sound.ReadSound;
import org.delightofcomposition.sound.WaveWriter;

/**
 * Manages background rendering of the granular spectrum synthesis.
 * Uses SwingWorker so the GUI stays responsive during the heavy FFT work.
 */
public class RenderController {

    public interface RenderCallback {
        void onComplete(float[][] stereoBuffer);
        void onError(String message);
        void onCancelled();
    }

    private SwingWorker<float[][], Integer> worker;
    private JProgressBar progressBar;
    private RenderCallback callback;

    public RenderController(JProgressBar progressBar, RenderCallback callback) {
        this.progressBar = progressBar;
        this.callback = callback;
    }

    public void render(SynthParameters params) {
        cancel(); // cancel any existing render

        SynthParameters snap = params.snapshot();
        Demo.cancelled = false;

        worker = new SwingWorker<float[][], Integer>() {
            @Override
            protected float[][] doInBackground() {
                double[] sample = ReadSound.readSoundDoubles(snap.sourceFile.getPath());

                // Extract selected region
                sample = extractRegion(sample, snap);
                int origlen = sample.length;

                if (!snap.useChordMode) {
                    // Simple single-voice render
                    double[] sound = Demo.demo(sample, snap, pct -> publish(pct));
                    if (sound == null) return null; // cancelled

                    // Per-voice dynamics (applied before panning/palindrome)
                    double[] rawCopy = null;
                    if (snap.useDynamicsEnv && snap.dynamicsPerVoice) {
                        rawCopy = snap.usePalindrome ? Arrays.copyOf(sound, sound.length) : null;
                        applyDynamicsToMono(sound, origlen, snap);
                    } else if (snap.usePalindrome && snap.useDynamicsEnv) {
                        // Match harmonic palindrome: dynamics on forward, raw on reverse
                        rawCopy = Arrays.copyOf(sound, sound.length);
                        applyDynamicsToMono(sound, origlen, snap);
                    }

                    // Palindrome
                    if (snap.usePalindrome) {
                        double[] pre = (rawCopy != null) ? rawCopy : Arrays.copyOf(sound, sound.length);
                        sound = applyPalindrome(sound, pre, sound.length, snap.crossfadeDuration);
                    }

                    // Calculate needed buffer length
                    int bufLen = sound.length + WaveWriter.SAMPLE_RATE * 5;
                    WaveWriter ww = new WaveWriter("_render_temp", bufLen);

                    for (int i = 0; i < sound.length; i++) {
                        double pan = Math.min(
                                snap.panSmoothing * 0.5
                                        + (1 - snap.panSmoothing) * 0.5 * (i / (double) sound.length),
                                1);
                        ww.df[0][i] += (float) (pan * sound[i]);
                        ww.df[1][i] += (float) ((1 - pan) * sound[i]);
                    }

                    // Post-mix dynamics (only if not already applied per-voice)
                    if (snap.useDynamicsEnv && !snap.dynamicsPerVoice && !snap.usePalindrome)
                        applyDynamicsEnvelope(ww.df, snap.dynamicsEnv, origlen, snap);
                    return ww.getBuffer();
                } else {
                    // Chord mode — mirrors Main.java experiment2 logic
                    return renderChord(snap, sample, origlen);
                }
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (!chunks.isEmpty()) {
                    int latest = chunks.get(chunks.size() - 1);
                    progressBar.setValue(latest);
                    progressBar.setString("Rendering " + latest + "%");
                }
            }

            @Override
            protected void done() {
                try {
                    float[][] result = get();
                    if (result == null) {
                        callback.onCancelled();
                    } else {
                        callback.onComplete(result);
                    }
                } catch (Exception e) {
                    if (isCancelled()) {
                        callback.onCancelled();
                    } else {
                        callback.onError(e.getMessage());
                    }
                }
            }
        };

        progressBar.setValue(0);
        worker.execute();
    }

    private float[][] renderChord(SynthParameters snap, double[] origSample, int origlen) {
        double[] ratios = snap.chordRatios;
        double[] attacktimes = snap.chordAttackTimes;
        int fundLen = origlen;

        // Estimate total buffer length needed
        int maxLen = 0;
        for (int n = 0; n < ratios.length; n++) {
            int voiceLen = (int) (origlen / ratios[n]);
            int attackOffset = (int) (attacktimes[n] * WaveWriter.SAMPLE_RATE);
            if (n > 0) {
                // forward + reverse with crossfade
                voiceLen = voiceLen * 2;
            }
            maxLen = Math.max(maxLen, voiceLen + attackOffset);
        }
        maxLen += WaveWriter.SAMPLE_RATE * 30; // reverb tail margin

        WaveWriter ww = new WaveWriter("_render_chord", maxLen);

        for (int n = 0; n < ratios.length; n++) {
            if (Demo.cancelled) return null;

            double ratio = ratios[n];
            double attackTime = attacktimes[n];
            double[] sample = ReadSound.readSoundDoubles(snap.sourceFile.getPath());
            sample = extractRegion(sample, snap);
            sample = ChangeSpeed.changeSpeed(sample, ratio, 1);
            int sampleLen = sample.length;

            // Use crisp attack envelope for non-fundamental voices
            double[] sound;
            if (n == 0) {
                sound = Demo.demo(sample, snap, null);
            } else {
                SynthParameters voiceParams = snap.snapshot();
                voiceParams.probEnv = snap.crispProbEnv;
                voiceParams.mixEnv = snap.crispMixEnv;
                sound = Demo.demo(sample, voiceParams, null);
            }
            if (sound == null) return null;

            // Per-voice dynamics
            if (snap.useDynamicsEnv && snap.dynamicsPerVoice) {
                double[] rawCopy = snap.usePalindrome ? Arrays.copyOf(sound, sound.length) : null;
                applyDynamicsToMono(sound, sampleLen, snap);

                if (n > 0 || snap.usePalindrome) {
                    // Palindrome for harmonics (always) or fundamental (if toggled)
                    boolean doPalindrome = (n > 0) || snap.usePalindrome;
                    if (doPalindrome) {
                        double[] pre = (rawCopy != null) ? rawCopy : Arrays.copyOf(sound, sound.length);
                        sound = applyPalindrome(sound, pre, sound.length, snap.crossfadeDuration);
                    }
                }

                // Soft attack fill for harmonics
                if (n > 0) {
                    int startForEndAlignment = fundLen - sampleLen;
                    if (startForEndAlignment >= 0) {
                        sample = ReadSound.readSoundDoubles(snap.sourceFile.getPath());
                        sample = extractRegion(sample, snap);
                        sample = ChangeSpeed.changeSpeed(sample, ratio, 1);
                        double[] softAttack = Demo.demo(sample, snap, null);
                        if (softAttack == null) return null;
                        applyDynamicsToMono(softAttack, sampleLen, snap);
                        for (int i = 0; i < softAttack.length && (startForEndAlignment + i) < ww.df[0].length; i++) {
                            ww.df[0][startForEndAlignment + i] += (float) softAttack[i];
                            ww.df[1][startForEndAlignment + i] += (float) softAttack[i];
                        }
                    }
                }

                sampleLen = sound.length;
            } else {
                // No per-voice dynamics — apply dynamics to forward pass for palindrome
                if (n > 0) {
                    double[] rawSound = Arrays.copyOf(sound, sampleLen);
                    double[] forwardSound = Arrays.copyOf(sound, sound.length);
                    // Apply dynamics to forward pass (matches old dramatic envelope behavior)
                    if (snap.useDynamicsEnv) {
                        applyDynamicsToMono(forwardSound, sampleLen, snap);
                    }
                    sound = applyPalindrome(forwardSound, rawSound, sound.length, snap.crossfadeDuration);

                    int startForEndAlignment = fundLen - sampleLen;
                    if (startForEndAlignment >= 0) {
                        sample = ReadSound.readSoundDoubles(snap.sourceFile.getPath());
                        sample = extractRegion(sample, snap);
                        sample = ChangeSpeed.changeSpeed(sample, ratio, 1);
                        double[] softAttack = Demo.demo(sample, snap, null);
                        if (softAttack == null) return null;
                        if (snap.useDynamicsEnv) {
                            applyDynamicsToMono(softAttack, sampleLen, snap);
                        }
                        for (int i = 0; i < softAttack.length && (startForEndAlignment + i) < ww.df[0].length; i++) {
                            ww.df[0][startForEndAlignment + i] += (float) softAttack[i];
                            ww.df[1][startForEndAlignment + i] += (float) softAttack[i];
                        }
                    }

                    sampleLen = sound.length;
                } else if (snap.usePalindrome) {
                    double[] rawCopy = Arrays.copyOf(sound, sound.length);
                    if (snap.useDynamicsEnv) {
                        applyDynamicsToMono(sound, sampleLen, snap);
                    }
                    sound = applyPalindrome(sound, rawCopy, sound.length, snap.crossfadeDuration);
                    sampleLen = sound.length;
                }
            }

            for (int i = 0; i < sound.length; i++) {
                int writeIdx = i + (int) (attackTime * WaveWriter.SAMPLE_RATE);
                if (writeIdx >= ww.df[0].length) break;
                double pan = Math.min(
                        snap.panSmoothing * 0.5
                                + (1 - snap.panSmoothing) * 0.5 * (i / (double) sampleLen),
                        1);
                if (n % 2 == 1) pan = 1 - pan;
                ww.df[0][writeIdx] += (float) (pan * sound[i]);
                ww.df[1][writeIdx] += (float) ((1 - pan) * sound[i]);
            }
        }

        if (snap.useDynamicsEnv && !snap.dynamicsPerVoice)
            applyDynamicsEnvelope(ww.df, snap.dynamicsEnv, fundLen, snap);
        return ww.getBuffer();
    }

    /**
     * Apply dynamics envelope to a mono voice buffer (before stereo panning).
     */
    private static void applyDynamicsToMono(double[] sound, int refLen, SynthParameters snap) {
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

    /**
     * Apply palindrome: forward sound with crossfade into reversed raw sound.
     */
    private static double[] applyPalindrome(double[] forwardSound, double[] rawSound,
            int sampleLen, double crossfadeDuration) {
        int crossFadeSamples = (int) (crossfadeDuration * WaveWriter.SAMPLE_RATE);
        double[] truncatedRaw = Arrays.copyOf(rawSound, Math.min(rawSound.length, sampleLen));

        double[] result = new double[forwardSound.length + truncatedRaw.length - crossFadeSamples];
        for (int i = 0; i < result.length; i++) {
            if (i < forwardSound.length)
                result[i] = forwardSound[i];
            int framesPastTransition = i - (sampleLen - crossFadeSamples);
            if (framesPastTransition > 0 && framesPastTransition < truncatedRaw.length) {
                double mix = Math.min(1.0, framesPastTransition / (double) crossFadeSamples);
                result[i] = mix * truncatedRaw[truncatedRaw.length - framesPastTransition]
                        + (1 - mix) * result[i];
            }
        }
        return result;
    }

    private static void applyDynamicsEnvelope(float[][] df, Envelope env, int refLen, SynthParameters snap) {
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

    private static double[] extractRegion(double[] sample, SynthParameters snap) {
        int start = (int) (snap.sourceStartFraction * sample.length);
        int end = (int) (snap.sourceEndFraction * sample.length);
        start = Math.max(0, start);
        end = Math.min(sample.length, end);
        if (end - start < 1000) end = Math.min(sample.length, start + 1000);
        return Arrays.copyOfRange(sample, start, end);
    }

    public void cancel() {
        Demo.cancelled = true;
        if (worker != null && !worker.isDone()) {
            worker.cancel(false);
        }
    }

    public boolean isRendering() {
        return worker != null && !worker.isDone();
    }
}
