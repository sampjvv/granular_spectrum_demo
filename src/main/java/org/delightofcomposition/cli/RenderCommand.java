package org.delightofcomposition.cli;

import java.io.File;
import java.util.concurrent.Callable;

import org.delightofcomposition.OfflineRenderer;
import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.gui.PresetManager;
import org.delightofcomposition.sound.WaveWriter;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "render",
    mixinStandardHelpOptions = true,
    description = "Render a granular spectrum .wav offline.%n"
                + "Start from a preset (--preset) and/or set individual flags. "
                + "Any flag you set overrides the preset value; flags you omit "
                + "keep the preset (or built-in default).")
public class RenderCommand implements Callable<Integer> {

    // --- Files -------------------------------------------------------------

    @Option(names = "--preset",
            description = "Preset .properties file saved by the GUI. Provides defaults for every parameter including envelope curves.")
    File preset;

    @Option(names = "--source",
            description = "Source audio sample (.wav). Required if no preset.")
    File source;

    @Option(names = "--grain",
            description = "Grain sample (.wav). Required if no preset.")
    File grain;

    @Option(names = "--ir",
            description = "Impulse response .wav used by the reverb. Required if no preset and reverb is on.")
    File ir;

    @Option(names = {"-o", "--output"}, defaultValue = "out.wav",
            description = "Output .wav path (default: ${DEFAULT-VALUE}).")
    File output;

    // --- Source region -----------------------------------------------------

    @Option(names = "--source-start",
            description = "Start of source region, fraction 0..1.")
    Double sourceStart;

    @Option(names = "--source-end",
            description = "End of source region, fraction 0..1.")
    Double sourceEnd;

    // --- Grain synth -------------------------------------------------------

    @Option(names = "--grain-freq",
            description = "Reference frequency of the grain sample in Hz.")
    Double grainFreq;

    // --- FFT ---------------------------------------------------------------

    @Option(names = "--window-exp",
            description = "Window size exponent (window = 2^N). Default 14 = 16384 samples.")
    Integer windowExp;

    @Option(names = "--control-rate",
            description = "Seconds between FFT windows.")
    Double controlRate;

    @Option(names = "--grains-per-peak",
            description = "Grains spawned per spectral peak per window.")
    Integer grainsPerPeak;

    @Option(names = "--amp-threshold",
            description = "Minimum peak amplitude (0..1) to spawn grains.")
    Double ampThreshold;

    // --- Reverb ------------------------------------------------------------

    @Option(names = "--reverb", negatable = true,
            description = "Enable/disable convolution reverb. Use --no-reverb for dry output.")
    Boolean reverb;

    @Option(names = "--source-reverb-mix",
            description = "Reverb wet/dry on source layer, 0..1.")
    Double sourceReverbMix;

    @Option(names = "--synth-reverb-mix",
            description = "Reverb wet/dry on granular layer, 0..1.")
    Double synthReverbMix;

    // --- Output shaping ----------------------------------------------------

    @Option(names = "--pan-smoothing",
            description = "Stereo pan smoothing, 0..1.")
    Double panSmoothing;

    @Option(names = "--palindrome", negatable = true,
            description = "Play forward then crossfade into reversed copy.")
    Boolean palindrome;

    @Option(names = "--crossfade-duration",
            description = "Palindrome crossfade length in seconds.")
    Double crossfadeDuration;

    @Option(names = "--crossfade-curve",
            description = "Palindrome crossfade curve, -1..1 (0 = linear).")
    Double crossfadeCurve;

    @Option(names = "--crossfade-overlap",
            description = "Palindrome overlap plateau, 0..1.")
    Double crossfadeOverlap;

    @Option(names = "--dynamics", negatable = true,
            description = "Apply dynamics envelope.")
    Boolean dynamics;

    @Option(names = "--dramatic-factor",
            description = "Dynamics dramatic factor.")
    Double dramaticFactor;

    // --- CLI behaviour -----------------------------------------------------

    @Option(names = {"-q", "--quiet"},
            description = "Suppress progress bar output.")
    boolean quiet;

    @Override
    public Integer call() {
        // 1. Start with defaults.
        SynthParameters params = new SynthParameters();

        // 2. Preset overlay (if provided).
        if (preset != null) {
            if (!preset.isFile()) {
                System.err.println("Preset file not found: " + preset);
                return 1;
            }
            PresetManager.load(params, preset.getPath());
        }

        // 3. Flag overrides. Only apply fields the user actually set.
        if (source != null)            params.sourceFile = source;
        if (grain != null)             params.grainFile = grain;
        if (ir != null)                params.impulseResponseFile = ir;
        if (sourceStart != null)       params.sourceStartFraction = sourceStart;
        if (sourceEnd != null)         params.sourceEndFraction = sourceEnd;
        if (grainFreq != null)         params.grainReferenceFreq = grainFreq;
        if (windowExp != null)         params.windowSizeExponent = windowExp;
        if (controlRate != null)       params.controlRate = controlRate;
        if (grainsPerPeak != null)     params.grainsPerPeak = grainsPerPeak;
        if (ampThreshold != null)      params.amplitudeThreshold = ampThreshold;
        if (reverb != null)            params.useReverb = reverb;
        if (sourceReverbMix != null)   params.sourceReverbMix = sourceReverbMix;
        if (synthReverbMix != null)    params.synthReverbMix = synthReverbMix;
        if (panSmoothing != null)      params.panSmoothing = panSmoothing;
        if (palindrome != null)        params.usePalindrome = palindrome;
        if (crossfadeDuration != null) params.crossfadeDuration = crossfadeDuration;
        if (crossfadeCurve != null)    params.crossfadeCurve = crossfadeCurve;
        if (crossfadeOverlap != null)  params.crossfadeOverlap = crossfadeOverlap;
        if (dynamics != null)          params.useDynamicsEnv = dynamics;
        if (dramaticFactor != null)    params.dramaticFactor = dramaticFactor;

        // 4. Validate inputs.
        if (!params.sourceFile.isFile()) {
            System.err.println("Source file not found: " + params.sourceFile);
            System.err.println("Provide --source or a --preset that points to one.");
            return 1;
        }
        if (!params.grainFile.isFile()) {
            System.err.println("Grain file not found: " + params.grainFile);
            return 1;
        }
        if (params.useReverb && !params.impulseResponseFile.isFile()) {
            System.err.println("Impulse response file not found: " + params.impulseResponseFile);
            System.err.println("Provide --ir, or disable reverb with --no-reverb.");
            return 1;
        }

        // 5. Snapshot for thread-safety (also defensive against any later mutation).
        SynthParameters snap = params.snapshot();

        // 6. Render.
        OfflineRenderer.Progress progress = quiet ? null : new TerminalProgress("Rendering");
        long t0 = System.currentTimeMillis();
        float[][] stereo;
        try {
            stereo = OfflineRenderer.render(snap, progress);
        } catch (Exception e) {
            System.err.println();
            System.err.println("Render failed: " + e.getMessage());
            e.printStackTrace(System.err);
            return 2;
        }
        long elapsedMs = System.currentTimeMillis() - t0;

        if (stereo == null) {
            System.err.println("Render returned no data (cancelled or empty).");
            return 2;
        }

        // 7. Write output.
        try {
            WaveWriter ww = new WaveWriter("_cli_temp", stereo[0].length);
            ww.df = stereo;
            ww.renderToFile(output.getPath());
        } catch (Exception e) {
            System.err.println();
            System.err.println("Failed to write output: " + e.getMessage());
            return 2;
        }

        int samples = stereo[0].length;
        double seconds = samples / (double) WaveWriter.SAMPLE_RATE;
        System.out.printf("Wrote %s (%d samples, %.2fs, rendered in %.1fs)%n",
                output.getPath(), samples, seconds, elapsedMs / 1000.0);
        return 0;
    }
}
