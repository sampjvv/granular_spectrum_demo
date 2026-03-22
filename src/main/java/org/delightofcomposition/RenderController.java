package org.delightofcomposition;

import java.util.Arrays;
import java.util.List;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import org.delightofcomposition.envelopes.Envelope;
import org.delightofcomposition.sound.ChangeSpeed;
import org.delightofcomposition.sound.DramaticEnvelope;
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
                int origlen = sample.length;

                if (!snap.useChordMode) {
                    // Simple single-voice render
                    double[] sound = Demo.demo(sample, snap, pct -> publish(pct));
                    if (sound == null) return null; // cancelled

                    // Calculate needed buffer length
                    int bufLen = sound.length + WaveWriter.SAMPLE_RATE * 5;
                    WaveWriter ww = new WaveWriter("_render_temp", bufLen);

                    for (int i = 0; i < sound.length; i++) {
                        double pan = Math.min(
                                snap.panSmoothing * 0.5
                                        + (1 - snap.panSmoothing) * 0.5 * (i / (double) origlen),
                                1);
                        ww.df[0][i] += (float) (pan * sound[i]);
                        ww.df[1][i] += (float) ((1 - pan) * sound[i]);
                    }

                    if (snap.useDynamicsEnv)
                        applyDynamicsEnvelope(ww.df, snap.dynamicsEnv, sound.length);
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

            if (n == 0) {
                DramaticEnvelope.dramaticEnvelope(sound, sampleLen,
                        snap.dramaticEnvShape, snap.dramaticFactor);
            } else {
                double[] dramaticEnvelopeSound = Arrays.copyOf(sound, sound.length);
                DramaticEnvelope.dramaticEnvelope(dramaticEnvelopeSound, sampleLen,
                        snap.dramaticEnvShape, snap.dramaticFactor);

                sound = Arrays.copyOf(sound, sampleLen);
                double crossFadeDur = snap.crossfadeDuration;

                double[] fullSound = new double[dramaticEnvelopeSound.length + sound.length
                        - (int) (crossFadeDur * WaveWriter.SAMPLE_RATE)];
                for (int i = 0; i < fullSound.length; i++) {
                    if (i < dramaticEnvelopeSound.length)
                        fullSound[i] = dramaticEnvelopeSound[i];
                    int framesPastTransition = i - (sampleLen
                            - (int) (WaveWriter.SAMPLE_RATE * crossFadeDur));
                    if (framesPastTransition > 0 && framesPastTransition < sound.length) {
                        double crossFadeDurInFrames = WaveWriter.SAMPLE_RATE * crossFadeDur;
                        double mix = Math.min(1, framesPastTransition / crossFadeDurInFrames);
                        fullSound[i] = mix * sound[sound.length - framesPastTransition]
                                + (1 - mix) * fullSound[i];
                    }
                }

                int startForEndAlignment = fundLen - sampleLen;
                if (startForEndAlignment >= 0) {
                    sample = ReadSound.readSoundDoubles(snap.sourceFile.getPath());
                    sample = ChangeSpeed.changeSpeed(sample, ratio, 1);
                    double[] softAttack = Demo.demo(sample, snap, null);
                    if (softAttack == null) return null;
                    DramaticEnvelope.dramaticEnvelope(softAttack, sampleLen,
                            snap.dramaticEnvShape, snap.dramaticFactor);
                    for (int i = 0; i < softAttack.length && (startForEndAlignment + i) < ww.df[0].length; i++) {
                        ww.df[0][startForEndAlignment + i] += (float) softAttack[i];
                        ww.df[1][startForEndAlignment + i] += (float) softAttack[i];
                    }
                }

                sound = fullSound;
                sampleLen = sound.length;
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

        if (snap.useDynamicsEnv)
            applyDynamicsEnvelope(ww.df, snap.dynamicsEnv, fundLen);
        return ww.getBuffer();
    }

    private static void applyDynamicsEnvelope(float[][] df, Envelope env, int refLen) {
        double[] times = env.times;
        double[] values = env.values;
        if (times == null || times.length == 0) return;

        int len = df[0].length;
        int seg = 0; // current envelope segment
        double segStartT = times[0];
        double segEndT = times.length > 1 ? times[1] : segStartT;
        double segStartV = values[0];
        double segEndV = times.length > 1 ? values[1] : segStartV;
        double segSpan = segEndT - segStartT;
        double slope = segSpan > 0 ? (segEndV - segStartV) / segSpan : 0;

        for (int i = 0; i < len; i++) {
            double t = Math.min((double) i / refLen, 1.0);

            // Advance segment if needed
            while (seg < times.length - 2 && t >= segEndT) {
                seg++;
                segStartT = times[seg];
                segEndT = times[seg + 1];
                segStartV = values[seg];
                segEndV = values[seg + 1];
                segSpan = segEndT - segStartT;
                slope = segSpan > 0 ? (segEndV - segStartV) / segSpan : 0;
            }

            float gain;
            if (t <= times[0]) {
                gain = (float) values[0];
            } else if (t >= times[times.length - 1]) {
                gain = (float) values[times.length - 1];
            } else {
                gain = (float) (segStartV + slope * (t - segStartT));
            }

            df[0][i] *= gain;
            df[1][i] *= gain;
        }
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
