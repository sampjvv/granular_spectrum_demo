package org.delightofcomposition;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.Transmitter;

import org.delightofcomposition.analysis.SpectralAnalysis;
import org.delightofcomposition.midi.MidiInputHandler;
import org.delightofcomposition.realtime.AudioEngine;
import org.delightofcomposition.realtime.ControlState;
import org.delightofcomposition.realtime.GrainSchedule;
import org.delightofcomposition.realtime.Voice;
import org.delightofcomposition.sound.Normalize;
import org.delightofcomposition.sound.ReadSound;

public class RealtimeMain {

    private static final int MAX_VOICES = 16;

    public static AudioEngine start(String sourcePath, String grainPath, double grainOrigFreq) {
        System.out.println("=== Real-Time MIDI Granular Spectrum ===");
        System.out.println();

        // Load source sample
        System.out.println("Loading source sample: " + sourcePath);
        double[] sourceSample = ReadSound.readSoundDoubles(sourcePath);
        Normalize.normalize(sourceSample);

        // Set up synthesis parameters
        SynthParameters params = new SynthParameters();
        params.grainReferenceFreq = grainOrigFreq;

        // Run spectral analysis
        SpectralAnalysis analysis = SpectralAnalysis.analyze(
                sourceSample, params.getWindowSize(), params.controlRate);

        // Build grain schedule
        GrainSchedule schedule = GrainSchedule.build(
                analysis, sourceSample, grainPath, grainOrigFreq,
                params.impulseResponseFile.getPath(),
                params.useReverb, params.synthReverbMix,
                params.grainsPerPeak);

        // Create engine components
        ControlState controls = new ControlState();
        Voice[] voices = new Voice[MAX_VOICES];
        for (int i = 0; i < MAX_VOICES; i++) {
            voices[i] = new Voice();
            voices[i].setGrainSchedule(schedule,
                    params.dramaticFactor, params.dramaticEnvShape);
        }

        // Create audio engine
        AudioEngine engine = new AudioEngine(voices, controls);

        // Set up MIDI input
        MidiDevice midiDevice = MidiInputHandler.findAndOpenDevice();
        if (midiDevice != null) {
            try {
                Transmitter transmitter = midiDevice.getTransmitter();
                MidiInputHandler handler = new MidiInputHandler(voices, controls);
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
        audioThread.setPriority(Thread.MAX_PRIORITY);
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
                "../samples/Cello/bowedCello1.wav",
                "../samples/bell.wav",
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
