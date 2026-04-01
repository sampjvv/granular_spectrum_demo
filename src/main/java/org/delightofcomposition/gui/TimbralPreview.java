package org.delightofcomposition.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.Random;
import java.util.function.Supplier;

import javax.swing.JComponent;

import org.delightofcomposition.envelopes.Envelope;

/**
 * Dot-density timbral preview showing four envelopes:
 * - Density envelope → dot visibility (more density = more dots)
 * - Dynamics envelope → dot size (louder = bigger)
 * - Mix envelope → color split (blue=synth at bottom, green=source above)
 * - Pitch envelope → vertical height of dot field (higher pitch = taller)
 */
public class TimbralPreview extends JComponent {

    private static final int HEIGHT = 60;
    private static final double MAX_GAIN = Math.pow(10, 6.0 / 20.0); // +6 dB

    private final Envelope probEnv;
    private final Envelope mixEnv;
    private final Envelope dynamicsEnv;
    private final Supplier<Boolean> dynamicsEnabled;
    private final Envelope pitchEnv;
    private final Supplier<Boolean> pitchEnabled;

    // Single unified dot grid (~30000 dots)
    private static final int COLS = 500;
    private static final int ROWS = 60;
    private final double[] dotTx;
    private final double[] dotTy;
    private final double[] dotThresh;
    private final int dotCount;

    public TimbralPreview(Envelope probEnv, Envelope mixEnv,
                          Envelope dynamicsEnv, Supplier<Boolean> dynamicsEnabled,
                          Envelope pitchEnv, Supplier<Boolean> pitchEnabled) {
        this.probEnv = probEnv;
        this.mixEnv = mixEnv;
        this.dynamicsEnv = dynamicsEnv;
        this.dynamicsEnabled = dynamicsEnabled;
        this.pitchEnv = pitchEnv;
        this.pitchEnabled = pitchEnabled;
        setPreferredSize(new Dimension(600, HEIGHT));
        setMinimumSize(new Dimension(100, HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));

        // Generate jittered dot grid (blue noise approximation)
        dotCount = COLS * ROWS;
        dotTx = new double[dotCount];
        dotTy = new double[dotCount];
        dotThresh = new double[dotCount];
        Random rng = new Random(42);
        double cellW = 1.0 / COLS;
        double cellH = 1.0 / ROWS;
        int idx = 0;
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                dotTx[idx] = (col + rng.nextDouble()) * cellW;
                dotTy[idx] = (row + rng.nextDouble()) * cellH;
                dotThresh[idx] = rng.nextDouble();
                idx++;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Background
        g2.setColor(Theme.BG_INPUT);
        g2.fillRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);

        // Derive synth/source colors from current theme
        Color synthColor = new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(),
                Theme.ACCENT.getBlue(), 200);
        Color sourceColor = new Color(Theme.SUCCESS.getRed(), Theme.SUCCESS.getGreen(),
                Theme.SUCCESS.getBlue(), 200);

        boolean dynOn = dynamicsEnabled.get();
        boolean pitchOn = pitchEnabled.get();
        int usableH = h - 2;

        // Dot loop: density→visibility, dynamics→size, pitch→height, mix→color
        for (int i = 0; i < dotCount; i++) {
            double t = dotTx[i];

            // density controls dot visibility (higher density = more dots)
            double density = getEnvValue(probEnv, t);
            if (dotThresh[i] >= density) continue;

            // dynamics controls vertical height in dB scale (+6 top, -60 bottom)
            double dynH;
            if (dynOn) {
                double dynamics = Math.max(1e-6, interpolate(dynamicsEnv, t));
                double dB = 20 * Math.log10(dynamics);
                // Map dB to 0-1: +6 dB = 1.0, 0 dB ≈ 0.91, -60 dB = 0.0
                dynH = clamp((dB + 60.0) / 66.0, 0.0, 1.0);
            } else {
                dynH = 1.0;
            }
            double totalH = dynH * usableH;
            double pyFromBottom = dotTy[i] * usableH;
            if (pyFromBottom > totalH) continue;

            // mix controls color split
            double mix = getEnvValue(mixEnv, t);
            double synthH = totalH * (1.0 - mix);
            g2.setColor(pyFromBottom <= synthH ? synthColor : sourceColor);

            // pitch controls dot size continuously (off = medium)
            double dotSize;
            if (pitchOn) {
                double pitchRatio = Math.max(0.25, interpolate(pitchEnv, t));
                double pitchNorm = clamp(1.0 - (Math.log(pitchRatio) / Math.log(2) / 4.0 + 0.5), 0.0, 1.0);
                dotSize = Math.max(0.5, Math.min(5.0, pitchNorm * 4.0 + 0.5));
            } else {
                dotSize = 2.5;
            }

            int px = (int) (t * (w - 1));
            int py = h - 1 - (int) pyFromBottom;
            g2.fill(new Ellipse2D.Double(px, py, dotSize, dotSize));
        }

        // Border
        g2.setColor(Theme.BORDER_SUBTLE);
        g2.drawRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);

        // Legend
        g2.setFont(Theme.FONT_SMALL);
        FontMetrics fm = g2.getFontMetrics();
        int legendY = fm.getAscent() + 4;

        // Synth
        g2.setColor(synthColor);
        g2.fillOval(6, legendY - 6, 6, 6);
        g2.setColor(Theme.FG_MUTED);
        g2.drawString("Synth", 16, legendY);

        // Source
        int sourceX = 16 + fm.stringWidth("Synth") + 12;
        g2.setColor(sourceColor);
        g2.fillOval(sourceX, legendY - 6, 6, 6);
        g2.setColor(Theme.FG_MUTED);
        g2.drawString("Source", sourceX + 10, legendY);

        g2.dispose();
    }

    private double getEnvValue(Envelope env, double t) {
        if (env == null || env.times == null || env.times.length == 0) return 0;
        return clamp(interpolate(env, t));
    }

    private double interpolate(Envelope env, double t) {
        double[] times = env.times;
        double[] values = env.values;
        if (times.length == 0) return 0;
        if (t <= times[0]) return values[0];
        if (t >= times[times.length - 1]) return values[times.length - 1];

        for (int i = 0; i < times.length - 1; i++) {
            if (t >= times[i] && t <= times[i + 1]) {
                double span = times[i + 1] - times[i];
                if (span <= 0) return values[i];
                double frac = (t - times[i]) / span;
                return values[i] + frac * (values[i + 1] - values[i]);
            }
        }
        return values[values.length - 1];
    }

    private double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
