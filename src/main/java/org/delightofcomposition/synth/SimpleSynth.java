package org.delightofcomposition.synth;

import java.nio.file.Paths;
import java.util.Arrays;

import org.delightofcomposition.sound.FFT2;
import org.delightofcomposition.sound.Normalize;
import org.delightofcomposition.sound.ReadSound;
import org.delightofcomposition.sound.Reverb;

public class SimpleSynth extends Synth {
    public double origFreq;
    public boolean useReverb;
    double[] wetSig;
    double[] sample;

    public SimpleSynth() {
        initialize("resources/4.wav", 394, true, 0.3, "resources/cathedral.wav");
    }

    public SimpleSynth(String samplePath, double freq) {
        initialize(samplePath, freq, true, 0.3, "resources/cathedral.wav");
    }

    public SimpleSynth(String samplePath, double freq, boolean useReverb, double reverbMix, String irPath) {
        initialize(samplePath, freq, useReverb, reverbMix, irPath);
    }

    private double reverbMix = 0.3;

    public void initialize(String samplePath, double freq, boolean useReverb, double reverbMix, String irPath) {
        origFreq = freq;
        this.useReverb = useReverb;
        this.reverbMix = reverbMix;
        sample = ReadSound.readSoundDoubles(samplePath);
        Normalize.normalize(sample);
        if (useReverb) {
            wetSig = Reverb.generateWet(sample, irPath);
            sample = Arrays.copyOf(sample, wetSig.length);
        }
    }

    public double[] reverb(double amp) {
        return reverb(amp, reverbMix, sample, wetSig);
    }

    public double[] synthAlg(double freq, double amp) {
        // Inline reverb mix into interpolation to avoid allocating a full
        // wet.length intermediate array on every grain call.
        double[] src = sample;
        int srcLen = src.length;
        double mix = 0;
        if (useReverb) {
            mix = (1 - amp) * reverbMix;
            srcLen = wetSig.length;
        }
        double invMix = 1 - mix;

        double[] processed = new double[(int) (srcLen * origFreq / freq)];

        for (int i = 0; i < processed.length; i++) {
            double exInd = i * freq / origFreq;
            int index = (int) exInd;
            double fract = exInd - index;

            double frame1, frame2;
            if (useReverb) {
                double dry1 = src[index];
                double wet1 = wetSig[index];
                frame1 = invMix * dry1 + mix * wet1;
                if (index + 1 < srcLen) {
                    double dry2 = src[index + 1];
                    double wet2 = wetSig[index + 1];
                    frame2 = invMix * dry2 + mix * wet2;
                } else {
                    frame2 = frame1;
                }
            } else {
                frame1 = src[index];
                frame2 = (index + 1 < srcLen) ? src[index + 1] : frame1;
            }

            processed[i] = (frame1 * (1 - fract) + frame2 * fract) * amp;
        }
        return processed;
    }
}
