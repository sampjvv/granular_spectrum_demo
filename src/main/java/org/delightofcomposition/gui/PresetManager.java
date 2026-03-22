package org.delightofcomposition.gui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.envelopes.Envelope;

/**
 * Saves and loads SynthParameters to/from .properties files.
 */
public class PresetManager {

    public static void save(SynthParameters params, String path) {
        Properties props = new Properties();
        props.setProperty("sourceFile", params.sourceFile.getPath());
        props.setProperty("grainFile", params.grainFile.getPath());
        props.setProperty("impulseResponseFile", params.impulseResponseFile.getPath());
        props.setProperty("grainReferenceFreq", String.valueOf(params.grainReferenceFreq));
        props.setProperty("windowSizeExponent", String.valueOf(params.windowSizeExponent));
        props.setProperty("controlRate", String.valueOf(params.controlRate));
        props.setProperty("grainsPerPeak", String.valueOf(params.grainsPerPeak));
        props.setProperty("amplitudeThreshold", String.valueOf(params.amplitudeThreshold));
        props.setProperty("useReverb", String.valueOf(params.useReverb));
        props.setProperty("sourceReverbMix", String.valueOf(params.sourceReverbMix));
        props.setProperty("synthReverbMix", String.valueOf(params.synthReverbMix));
        props.setProperty("panSmoothing", String.valueOf(params.panSmoothing));
        props.setProperty("crossfadeDuration", String.valueOf(params.crossfadeDuration));
        props.setProperty("dramaticFactor", String.valueOf(params.dramaticFactor));
        props.setProperty("useChordMode", String.valueOf(params.useChordMode));
        props.setProperty("chordRatios", arrayToString(params.chordRatios));
        props.setProperty("chordAttackTimes", arrayToString(params.chordAttackTimes));

        // Envelopes
        saveEnvelope(props, "probEnv", params.probEnv);
        saveEnvelope(props, "mixEnv", params.mixEnv);
        saveEnvelope(props, "dramaticEnvShape", params.dramaticEnvShape);
        saveEnvelope(props, "dynamicsEnv", params.dynamicsEnv);
        props.setProperty("useDynamicsEnv", String.valueOf(params.useDynamicsEnv));

        try (FileOutputStream fos = new FileOutputStream(path)) {
            props.store(fos, "Granular Spectrum Synthesizer Preset");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void load(SynthParameters params, String path) {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(path)) {
            props.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        params.sourceFile = new File(props.getProperty("sourceFile", params.sourceFile.getPath()));
        params.grainFile = new File(props.getProperty("grainFile", params.grainFile.getPath()));
        params.impulseResponseFile = new File(props.getProperty("impulseResponseFile",
                params.impulseResponseFile.getPath()));
        params.grainReferenceFreq = Double.parseDouble(
                props.getProperty("grainReferenceFreq", String.valueOf(params.grainReferenceFreq)));
        params.windowSizeExponent = Integer.parseInt(
                props.getProperty("windowSizeExponent", String.valueOf(params.windowSizeExponent)));
        params.controlRate = Double.parseDouble(
                props.getProperty("controlRate", String.valueOf(params.controlRate)));
        params.grainsPerPeak = Integer.parseInt(
                props.getProperty("grainsPerPeak", String.valueOf(params.grainsPerPeak)));
        params.amplitudeThreshold = Double.parseDouble(
                props.getProperty("amplitudeThreshold", String.valueOf(params.amplitudeThreshold)));
        params.useReverb = Boolean.parseBoolean(
                props.getProperty("useReverb", String.valueOf(params.useReverb)));
        params.sourceReverbMix = Double.parseDouble(
                props.getProperty("sourceReverbMix", String.valueOf(params.sourceReverbMix)));
        params.synthReverbMix = Double.parseDouble(
                props.getProperty("synthReverbMix", String.valueOf(params.synthReverbMix)));
        params.panSmoothing = Double.parseDouble(
                props.getProperty("panSmoothing", String.valueOf(params.panSmoothing)));
        params.crossfadeDuration = Double.parseDouble(
                props.getProperty("crossfadeDuration", String.valueOf(params.crossfadeDuration)));
        params.dramaticFactor = Double.parseDouble(
                props.getProperty("dramaticFactor", String.valueOf(params.dramaticFactor)));
        params.useChordMode = Boolean.parseBoolean(
                props.getProperty("useChordMode", String.valueOf(params.useChordMode)));
        params.chordRatios = parseDoubleArray(
                props.getProperty("chordRatios", arrayToString(params.chordRatios)));
        params.chordAttackTimes = parseDoubleArray(
                props.getProperty("chordAttackTimes", arrayToString(params.chordAttackTimes)));

        // Envelopes
        params.probEnv = loadEnvelope(props, "probEnv", params.probEnv);
        params.mixEnv = loadEnvelope(props, "mixEnv", params.mixEnv);
        params.dramaticEnvShape = loadEnvelope(props, "dramaticEnvShape", params.dramaticEnvShape);
        params.dynamicsEnv = loadEnvelope(props, "dynamicsEnv", params.dynamicsEnv);
        params.useDynamicsEnv = Boolean.parseBoolean(
                props.getProperty("useDynamicsEnv", "true"));
    }

    private static void saveEnvelope(Properties props, String prefix, Envelope env) {
        if (env.times == null) return;
        props.setProperty(prefix + ".times", arrayToString(env.times));
        props.setProperty(prefix + ".values", arrayToString(env.values));
    }

    private static Envelope loadEnvelope(Properties props, String prefix, Envelope fallback) {
        String timesStr = props.getProperty(prefix + ".times");
        String valuesStr = props.getProperty(prefix + ".values");
        if (timesStr == null || valuesStr == null) return fallback;
        try {
            double[] times = parseDoubleArray(timesStr);
            double[] values = parseDoubleArray(valuesStr);
            return new Envelope(times, values);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String arrayToString(double[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static double[] parseDoubleArray(String text) {
        String[] parts = text.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }
        return result;
    }
}
