package org.delightofcomposition.realtime;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.Transmitter;

import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.analysis.SpectralAnalysis;
import org.delightofcomposition.midi.MidiInputHandler;
import org.delightofcomposition.sound.Normalize;
import org.delightofcomposition.sound.ReadSound;

public class LiveMidiController {

    private static final int MAX_VOICES = 16;
    private static final int MAX_GRAINS = 512;

    private AudioEngine engine;
    private MidiDevice midiDevice;
    private ControlState controlState;
    private Voice[] voices;
    private SpectralAnalysis analysis;
    private Thread audioThread;

    private volatile boolean running;
    private volatile boolean starting;

    public void start(SynthParameters params) {
        if (running || starting) return;
        starting = true;

        try {
            // Load and normalize source sample
            double[] sourceSample = ReadSound.readSoundDoubles(params.sourceFile.getPath());
            Normalize.normalize(sourceSample);

            // Load and normalize grain sample
            double[] grainSample = ReadSound.readSoundDoubles(params.grainFile.getPath());
            Normalize.normalize(grainSample);

            // Run spectral analysis (this is the slow part)
            int windowSize = params.getWindowSize();
            double controlRate = params.controlRate;
            analysis = SpectralAnalysis.analyze(sourceSample, windowSize, controlRate);

            // Create engine components
            controlState = new ControlState();
            GrainPool pool = new GrainPool(MAX_GRAINS);
            voices = new Voice[MAX_VOICES];
            for (int i = 0; i < MAX_VOICES; i++) {
                voices[i] = new Voice();
            }

            // Set up MIDI input
            midiDevice = MidiInputHandler.findAndOpenDevice();
            if (midiDevice != null) {
                try {
                    Transmitter transmitter = midiDevice.getTransmitter();
                    MidiInputHandler handler = new MidiInputHandler(voices, controlState,
                            analysis.getSourceFundamentalHz());
                    transmitter.setReceiver(handler);
                } catch (Exception e) {
                    System.err.println("Failed to connect MIDI: " + e.getMessage());
                }
            }

            // Start audio engine on daemon thread
            engine = new AudioEngine(pool, voices, controlState, analysis,
                    grainSample, params.grainReferenceFreq, sourceSample);
            audioThread = new Thread(engine, "LiveAudioEngine");
            audioThread.setDaemon(true);
            audioThread.start();

            running = true;
        } finally {
            starting = false;
        }
    }

    public void stop() {
        if (!running) return;
        running = false;

        if (engine != null) {
            engine.stop();
            engine = null;
        }
        if (audioThread != null) {
            try { audioThread.join(2000); } catch (InterruptedException ignored) {}
            audioThread = null;
        }
        if (midiDevice != null) {
            try { midiDevice.close(); } catch (Exception ignored) {}
            midiDevice = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public ControlState getControlState() {
        return controlState;
    }

    public Voice[] getVoices() {
        return voices != null ? voices : new Voice[0];
    }

    public double getSourceFundamentalHz() {
        return analysis != null ? analysis.getSourceFundamentalHz() : 440.0;
    }

    public String getMidiDeviceName() {
        if (midiDevice != null) {
            return midiDevice.getDeviceInfo().getName();
        }
        return "No MIDI device";
    }

    public int getActiveVoiceCount() {
        if (engine != null) return engine.getActiveVoiceCount();
        return 0;
    }

    public int getActiveGrainCount() {
        if (engine != null) return engine.getActiveGrainCount();
        return 0;
    }

    public int getMaxVoices() {
        return MAX_VOICES;
    }

    public int getMaxGrains() {
        return MAX_GRAINS;
    }
}
