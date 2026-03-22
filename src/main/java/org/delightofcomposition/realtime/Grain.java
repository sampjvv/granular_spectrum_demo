package org.delightofcomposition.realtime;

import org.delightofcomposition.sound.WaveWriter;

public class Grain {
    private double[] sample;
    private double playbackRate;
    private double amplitude;
    private double position;
    private int age;
    private int totalLifetime;
    private boolean active;

    // Envelope: ~5ms attack, ~10ms release at 48kHz
    private static final int ATTACK_SAMPLES = (int) (0.005 * WaveWriter.SAMPLE_RATE);
    private static final int RELEASE_SAMPLES = (int) (0.010 * WaveWriter.SAMPLE_RATE);

    public Grain() {
        this.active = false;
    }

    public void activate(double[] sample, double playbackRate, double amplitude, int lifetimeSamples) {
        this.sample = sample;
        this.playbackRate = playbackRate;
        this.amplitude = amplitude;
        this.position = 0;
        this.age = 0;
        this.totalLifetime = lifetimeSamples;
        this.active = true;
    }

    public void render(double[] buffer, int offset, int length) {
        if (!active) return;

        for (int i = 0; i < length; i++) {
            if (!active) break;

            int index = (int) position;
            if (index + 1 >= sample.length) {
                deactivate();
                break;
            }

            double fract = position - index;
            double frame = sample[index] * (1 - fract) + sample[index + 1] * fract;

            // Trapezoid envelope
            double env = 1.0;
            if (age < ATTACK_SAMPLES) {
                env = age / (double) ATTACK_SAMPLES;
            } else if (totalLifetime - age < RELEASE_SAMPLES) {
                env = (totalLifetime - age) / (double) RELEASE_SAMPLES;
            }

            buffer[offset + i] += frame * amplitude * env;

            position += playbackRate;
            age++;
            if (age >= totalLifetime) {
                deactivate();
                break;
            }
        }
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        active = false;
    }
}
