package org.delightofcomposition.sound;

import org.delightofcomposition.envelopes.Envelope;

public class DramaticEnvelope {
    public static void dramaticEnvelope(double[] sig, double len) {
        Envelope env = new Envelope(new double[] { 0, 0.25, 1 }, new double[] { 1, 0.7, 1 });
        dramaticEnvelope(sig, len, env, 10);
    }

    public static void dramaticEnvelope(double[] sig, double len, Envelope shape, double factor) {
        for (int i = 0; i < sig.length; i++) {
            double linear = shape.getValue(i / len);
            double expFunc = (Math.pow(Math.E, factor * linear) - 1) / (Math.pow(Math.E, factor) - 1);
            sig[i] *= expFunc;
        }
    }

    public static void drammaticDecay(double[] sig, double len) {
        Envelope env = new Envelope(new double[] { 0, 1 }, new double[] { 1, 0.1 });
        double drammaFactor = 1;
        for (int i = 0; i < sig.length; i++) {
            double linear = env.getValue(i / len);
            double expFunc = (Math.pow(Math.E, drammaFactor * linear) - 1) / (Math.pow(Math.E, drammaFactor) - 1);
            sig[i] *= expFunc;
        }
    }
}
