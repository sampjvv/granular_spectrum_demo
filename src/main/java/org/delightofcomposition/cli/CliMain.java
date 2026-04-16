package org.delightofcomposition.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "granular",
    mixinStandardHelpOptions = true,
    version = "granular 1.0.0",
    subcommands = { RenderCommand.class, AnalyzeCommand.class },
    description = "Granular spectrum synthesizer — offline CLI.%n"
                + "Render .wav files or inspect spectral content without the GUI.")
public class CliMain implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        // Defensive: fail loudly if any code path tries to open a display
        // instead of hanging a headless JVM.
        System.setProperty("java.awt.headless", "true");
        int code = new CommandLine(new CliMain()).execute(args);
        System.exit(code);
    }
}
