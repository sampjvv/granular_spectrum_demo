package org.delightofcomposition;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.Transmitter;

import org.delightofcomposition.analysis.SpectralAnalysis;
import org.delightofcomposition.midi.MidiInputHandler;
import org.delightofcomposition.realtime.AudioEngine;
import org.delightofcomposition.realtime.ControlState;
import org.delightofcomposition.realtime.GrainPool;
import org.delightofcomposition.realtime.Voice;
import org.delightofcomposition.sound.Normalize;
import org.delightofcomposition.sound.ReadSound;

public class RealtimeMain {

    private static final int MAX_VOICES = 16;
    private static final int MAX_GRAINS = 512;
    private static final int WINDOW_SIZE = (int) Math.pow(2, 14); // 16384
    private static final double CONTROL_RATE = 0.1;

    public static AudioEngine start(String sourcePath, String grainPath, double grainOrigFreq) {
        System.out.println("=== Real-Time MIDI Granular Spectrum ===");
        System.out.println();

        // Load source sample
        System.out.println("Loading source sample: " + sourcePath);
        double[] sourceSample = ReadSound.readSoundDoubles(sourcePath);
        Normalize.normalize(sourceSample);

        // Load grain sample
        System.out.println("Loading grain sample: " + grainPath);
        double[] grainSample = ReadSound.readSoundDoubles(grainPath);
        Normalize.normalize(grainSample);

        // Pre-compute spectral analysis
        System.out.println("Running spectral analysis...");
        SpectralAnalysis analysis = SpectralAnalysis.analyze(sourceSample, WINDOW_SIZE, CONTROL_RATE);

        // Create engine components
        ControlState controls = new ControlState();
        GrainPool pool = new GrainPool(MAX_GRAINS);
        Voice[] voices = new Voice[MAX_VOICES];
        for (int i = 0; i < MAX_VOICES; i++) {
            voices[i] = new Voice();
        }

        // Set up MIDI input
        MidiDevice midiDevice = MidiInputHandler.findAndOpenDevice();
        if (midiDevice != null) {
            try {
                Transmitter transmitter = midiDevice.getTransmitter();
                MidiInputHandler handler = new MidiInputHandler(voices, controls,
                        analysis.getSourceFundamentalHz());
                transmitter.setReceiver(handler);
                System.out.println("MIDI input connected.");
            } catch (Exception e) {
                System.err.println("Failed to connect MIDI: " + e.getMessage());
            }
        } else {
            System.out.println("No MIDI device found. You can still use the UI controls.");
        }

        // Start audio engine
        AudioEngine engine = new AudioEngine(pool, voices, controls, analysis,
                grainSample, grainOrigFreq, sourceSample);
        Thread audioThread = new Thread(engine, "AudioEngine");
        audioThread.setDaemon(true);
        audioThread.start();

        System.out.println();
        System.out.println("Controls:");
        System.out.println("  MIDI notes  -> play granular spectrum at pitch");
        System.out.println("  Mod wheel   -> mix (granular <-> source)");
        System.out.println("  CC#74       -> grain density");
        System.out.println();

        return engine;
    }

    public static void main(String[] args) throws Exception {
        AudioEngine engine = start(
                "resources/bowedCello1.wav",
                "resources/bell.wav",
                1287);

        System.out.println("Press Enter to quit.");
        System.in.read();
        engine.stop();
    }
}
