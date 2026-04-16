package org.delightofcomposition.cli;

import org.delightofcomposition.OfflineRenderer;
import org.delightofcomposition.util.ProgressBar;

/**
 * Progress callback that prints a progress bar to stderr, reusing the
 * existing util.ProgressBar. Stderr so it doesn't corrupt stdout data
 * (e.g. CSV output from the analyze subcommand).
 */
final class TerminalProgress implements OfflineRenderer.Progress {
    private final String label;
    private int lastPct = -1;

    TerminalProgress(String label) {
        this.label = label;
    }

    @Override
    public void update(int pct) {
        // util.ProgressBar prints to System.out. Redirect transiently to stderr
        // so stdout stays clean for users piping CLI output.
        if (pct == lastPct) return;
        lastPct = pct;
        java.io.PrintStream origOut = System.out;
        System.setOut(System.err);
        try {
            ProgressBar.printProgressBar(pct, 100, label);
        } finally {
            System.setOut(origOut);
        }
    }
}
