package org.delightofcomposition.realtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Consumer;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.Transmitter;

import org.delightofcomposition.Demo;
import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.analysis.SpectralAnalysis;
import org.delightofcomposition.midi.MidiInputHandler;
import org.delightofcomposition.sound.Normalize;
import org.delightofcomposition.sound.ReadSound;

public class LiveMidiController {

    private static final int MAX_VOICES = 16;
    private static final int NUM_LAYERS = 5;
    // Per-layer grain density: sparse → dense, sums to 1.0
    private static final double[] LAYER_DENSITIES = {0.05, 0.15, 0.20, 0.30, 0.30};

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
            // Load and normalize source sample
            double[] sourceSample = ReadSound.readSoundDoubles(params.sourceFile.getPath());
            Normalize.normalize(sourceSample);

            // Detect fundamental frequency via spectral analysis
            int windowSize = params.getWindowSize();
            double controlRate = params.controlRate;
            analysis = SpectralAnalysis.analyze(sourceSample, windowSize, controlRate);

            // Pre-render 5 granular layers in parallel at varying densities
            double[][] granularLayers = new double[NUM_LAYERS][];
            AtomicIntegerArray layerProgress = new AtomicIntegerArray(NUM_LAYERS);
            ExecutorService layerPool = Executors.newFixedThreadPool(NUM_LAYERS);
            List<Future<double[]>> futures = new ArrayList<>();

            for (int i = 0; i < NUM_LAYERS; i++) {
                final int layerIdx = i;
                long seedOffset = i * 1000L;
                double density = LAYER_DENSITIES[i];
                futures.add(layerPool.submit(() -> {
                    Consumer<Integer> layerCb = pct -> {
                        layerProgress.set(layerIdx, pct);
                        if (progress != null) {
                            int sum = 0;
                            for (int j = 0; j < NUM_LAYERS; j++) sum += layerProgress.get(j);
                            progress.accept(sum / NUM_LAYERS);
                        }
                    };
                    return Demo.renderGranularLayer(sourceSample, params, seedOffset, density, layerCb);
                }));
            }

            try {
                for (int i = 0; i < NUM_LAYERS; i++) {
                    granularLayers[i] = futures.get(i).get();
                    if (granularLayers[i] == null) {
                        layerPool.shutdownNow();
                        return;
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                layerPool.shutdownNow();
                throw new RuntimeException("Layer rendering failed", e);
            }
            layerPool.shutdown();
            if (progress != null) progress.accept(100);

            // Create engine components
            controlState = new ControlState();
            voices = new Voice[MAX_VOICES];
            for (int i = 0; i < MAX_VOICES; i++) {
                voices[i] = new Voice();
                voices[i].setBuffers(granularLayers, sourceSample,
                        params.dramaticFactor, params.dramaticEnvShape);
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
            engine = new AudioEngine(voices, controlState);
            audioThread = new Thread(engine, "LiveAudioEngine");
            audioThread.setDaemon(true);
            audioThread.start();

            running = true;
        } finally {
            starting = false;
        }
    }

    public void cancel() {
        Demo.cancelled = true;
    }

    public void stop() {
        if (starting) {
            Demo.cancelled = true;
            return;
        }
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
