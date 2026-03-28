package org.delightofcomposition.realtime;

import java.util.function.Consumer;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.Transmitter;

import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.analysis.SpectralAnalysis;
import org.delightofcomposition.midi.MidiInputHandler;
import org.delightofcomposition.sound.Normalize;
import org.delightofcomposition.sound.ReadSound;

public class LiveMidiController {

    private static final int MAX_VOICES = 16;

    private AudioEngine engine;
    private MidiDevice midiDevice;
    private ControlState controlState;
    private Voice[] voices;
    private SpectralAnalysis analysis;
    private Thread audioThread;

    private volatile boolean running;
    private volatile boolean starting;

    public void start(SynthParameters params, Consumer<Integer> progress) {
        if (running || starting) return;
        starting = true;

        try {
            // 1. Load and normalize source sample, applying region selection
            if (progress != null) progress.accept(5);
            double[] rawSource = ReadSound.readSoundDoubles(params.sourceFile.getPath());
            int regionStart = (int) (params.sourceStartFraction * rawSource.length);
            int regionEnd = (int) (params.sourceEndFraction * rawSource.length);
            regionStart = Math.max(0, regionStart);
            regionEnd = Math.min(rawSource.length, regionEnd);
            if (regionEnd - regionStart < 1000) regionEnd = Math.min(rawSource.length, regionStart + 1000);
            final double[] sourceSample = java.util.Arrays.copyOfRange(rawSource, regionStart, regionEnd);
            Normalize.normalize(sourceSample);

            // 2. Run spectral analysis (FFT → peaks per window)
            if (progress != null) progress.accept(15);
            int windowSize = params.getWindowSize();
            double controlRate = params.controlRate;
            analysis = SpectralAnalysis.analyze(sourceSample, windowSize, controlRate);

            // 3. Build grain schedule from spectral analysis + load bell sample
            if (progress != null) progress.accept(50);
            GrainSchedule schedule = GrainSchedule.build(
                    analysis, sourceSample,
                    params.grainFile.getPath(),
                    params.grainReferenceFreq,
                    params.impulseResponseFile.getPath(),
                    params.useReverb,
                    params.synthReverbMix,
                    params.grainsPerPeak);

            if (progress != null) progress.accept(90);

            // 4. Create engine components
            controlState = new ControlState();
            voices = new Voice[MAX_VOICES];
            for (int i = 0; i < MAX_VOICES; i++) {
                voices[i] = new Voice();
                voices[i].setGrainSchedule(schedule,
                        params.dramaticFactor, params.dramaticEnvShape);
            }

            // 5. Set up MIDI input
            midiDevice = MidiInputHandler.findAndOpenDevice();
            if (midiDevice != null) {
                try {
                    Transmitter transmitter = midiDevice.getTransmitter();
                    MidiInputHandler handler = new MidiInputHandler(voices, controlState);
                    transmitter.setReceiver(handler);
                } catch (Exception e) {
                    System.err.println("Failed to connect MIDI: " + e.getMessage());
                }
            }

            // 6. Start audio engine on high-priority thread
            engine = new AudioEngine(voices, controlState);
            audioThread = new Thread(engine, "LiveAudioEngine");
            audioThread.setDaemon(true);
            audioThread.setPriority(Thread.MAX_PRIORITY);
            audioThread.start();

            if (progress != null) progress.accept(100);
            running = true;
        } finally {
            starting = false;
        }
    }

    public void cancel() {
        // No long-running render to cancel — startup is fast
    }

    public void stop() {
        if (starting) return;
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

    public int getMaxVoices() {
        return MAX_VOICES;
    }
}
