package org.delightofcomposition;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.Transmitter;

import org.delightofcomposition.midi.MidiInputHandler;
import org.delightofcomposition.realtime.AudioEngine;
import org.delightofcomposition.realtime.ControlState;
import org.delightofcomposition.realtime.Voice;
import org.delightofcomposition.sound.FFT2;
import org.delightofcomposition.sound.Normalize;
import org.delightofcomposition.sound.ReadSound;
import org.delightofcomposition.sound.WaveWriter;

public class RealtimeMain {

    private static final int MAX_VOICES = 16;
    private static final int NUM_LAYERS = 3;

    public static AudioEngine start(String sourcePath, String grainPath, double grainOrigFreq) {
        System.out.println("=== Real-Time MIDI Granular Spectrum ===");
        System.out.println();

        // Load source sample
        System.out.println("Loading source sample: " + sourcePath);
        double[] sourceSample = ReadSound.readSoundDoubles(sourcePath);
        Normalize.normalize(sourceSample);

        // Detect source fundamental frequency
        double sourceFundamental = FFT2.getPitch(sourceSample, WaveWriter.SAMPLE_RATE);
        System.out.println("Detected source fundamental: " + String.format("%.1f", sourceFundamental) + " Hz");

        // Set up synthesis parameters
        SynthParameters params = new SynthParameters();
        params.grainReferenceFreq = grainOrigFreq;

        // Pre-render 3 granular layers with different random seeds
        double[][] granularLayers = new double[NUM_LAYERS][];
        for (int i = 0; i < NUM_LAYERS; i++) {
            long seedOffset = i * 1000L;
            System.out.println("Rendering granular layer " + (i + 1) + "/" + NUM_LAYERS
                    + " (seed offset " + seedOffset + ")...");
            double[] layerSource = ReadSound.readSoundDoubles(sourcePath);
            Normalize.normalize(layerSource);
            granularLayers[i] = Demo.renderGranularLayer(layerSource, params, seedOffset, null);
            if (granularLayers[i] == null) {
                System.err.println("Render cancelled or failed.");
                return null;
            }
        }
        System.out.println("All layers rendered.");

        // Create engine components
        ControlState controls = new ControlState();
        Voice[] voices = new Voice[MAX_VOICES];
        for (int i = 0; i < MAX_VOICES; i++) {
            voices[i] = new Voice();
        }

        // Create audio engine and set buffers
        AudioEngine engine = new AudioEngine(voices, controls);
        engine.setBuffers(granularLayers, sourceSample);

        // Set up MIDI input
        MidiDevice midiDevice = MidiInputHandler.findAndOpenDevice();
        if (midiDevice != null) {
            try {
                Transmitter transmitter = midiDevice.getTransmitter();
                MidiInputHandler handler = new MidiInputHandler(voices, controls, sourceFundamental);
                transmitter.setReceiver(handler);
                System.out.println("MIDI input connected.");
            } catch (Exception e) {
                System.err.println("Failed to connect MIDI: " + e.getMessage());
            }
        } else {
            System.out.println("No MIDI device found. You can still use the UI controls.");
        }

        // Start audio engine
        Thread audioThread = new Thread(engine, "AudioEngine");
        audioThread.setDaemon(true);
        audioThread.start();

        System.out.println();
        System.out.println("Controls:");
        System.out.println("  MIDI notes  -> play granular spectrum at pitch");
        System.out.println("  Mod wheel   -> mix (granular <-> source)");
        System.out.println("  CC#74       -> grain density (layers)");
        System.out.println();

        return engine;
    }

    public static void main(String[] args) throws Exception {
        AudioEngine engine = start(
                "resources/bowedCello1.wav",
                "resources/bell.wav",
                1287);

        if (engine == null) {
            System.err.println("Failed to start engine.");
            return;
        }

        System.out.println("Press Enter to quit.");
        System.in.read();
        engine.stop();
    }
}
