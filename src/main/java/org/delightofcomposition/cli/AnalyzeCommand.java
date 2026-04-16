package org.delightofcomposition.cli;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.concurrent.Callable;

import org.delightofcomposition.analysis.SpectralAnalysis;
import org.delightofcomposition.analysis.WindowPeaks;
import org.delightofcomposition.sound.ReadSound;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "analyze",
    mixinStandardHelpOptions = true,
    description = "Dump per-window spectral peaks for a source sample.%n"
                + "Useful for inspecting a sample before choosing render parameters.")
public class AnalyzeCommand implements Callable<Integer> {

    public enum Format { text, csv }

    @Option(names = "--source", required = true,
            description = "Source audio sample (.wav).")
    File source;

    @Option(names = "--window-exp", defaultValue = "14",
            description = "Window size exponent (window = 2^N). Default: ${DEFAULT-VALUE}.")
    int windowExp;

    @Option(names = "--control-rate", defaultValue = "0.1",
            description = "Seconds between FFT windows. Default: ${DEFAULT-VALUE}.")
    double controlRate;

    @Option(names = "--format", defaultValue = "text",
            description = "Output format: ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.")
    Format format;

    @Option(names = {"-o", "--output"},
            description = "Write to file instead of stdout.")
    File output;

    @Override
    public Integer call() {
        if (!source.isFile()) {
            System.err.println("Source file not found: " + source);
            return 1;
        }

        double[] sample = ReadSound.readSoundDoubles(source.getPath());
        if (sample == null || sample.length == 0) {
            System.err.println("Failed to read source sample: " + source);
            return 1;
        }

        // SpectralAnalysis.analyze() prints its own progress bar and a
        // "Detected source fundamental" line to System.out. If the user is
        // piping our stdout (no -o, CSV format), that noise would corrupt
        // the output. Redirect stdout to stderr during analysis in that case.
        boolean writingToStdout = (output == null);
        PrintStream origOut = System.out;
        if (writingToStdout) {
            System.setOut(System.err);
        }

        SpectralAnalysis sa;
        try {
            int windowSize = 1 << windowExp;
            sa = SpectralAnalysis.analyze(sample, windowSize, controlRate);
        } finally {
            if (writingToStdout) {
                System.setOut(origOut);
            }
        }

        // Pick a sink.
        PrintWriter out;
        try {
            out = (output == null)
                    ? new PrintWriter(System.out)
                    : new PrintWriter(output, "UTF-8");
        } catch (IOException e) {
            System.err.println("Cannot open output: " + e.getMessage());
            return 1;
        }

        try {
            if (format == Format.csv) {
                writeCsv(out, sa);
            } else {
                writeText(out, sa);
            }
        } finally {
            out.flush();
            if (output != null) out.close();
        }

        if (output != null) {
            System.out.println("Wrote " + output.getPath()
                    + " (" + sa.getWindowCount() + " windows)");
        }
        return 0;
    }

    private static void writeText(PrintWriter out, SpectralAnalysis sa) {
        out.printf("# fundamental=%.2f Hz  windows=%d  windowDur=%.3fs%n",
                sa.getSourceFundamentalHz(),
                sa.getWindowCount(),
                sa.getWindowDurationSec());
        for (int w = 0; w < sa.getWindowCount(); w++) {
            WindowPeaks wp = sa.getWindow(w);
            double time = (w + 1) * sa.getWindowDurationSec();
            out.printf("window=%d time=%.3fs peaks=%d%n", w, time, wp.getPeakCount());
            for (int p = 0; p < wp.getPeakCount(); p++) {
                out.printf("  %10.2f  %.4f%n", wp.getFrequency(p), wp.getAmplitude(p));
            }
        }
    }

    private static void writeCsv(PrintWriter out, SpectralAnalysis sa) {
        out.println("window,time_sec,freq_hz,amplitude");
        for (int w = 0; w < sa.getWindowCount(); w++) {
            WindowPeaks wp = sa.getWindow(w);
            double time = (w + 1) * sa.getWindowDurationSec();
            for (int p = 0; p < wp.getPeakCount(); p++) {
                out.printf("%d,%.3f,%.3f,%.6f%n",
                        w, time, wp.getFrequency(p), wp.getAmplitude(p));
            }
        }
    }
}
