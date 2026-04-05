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

import com.sptc.uilab.tokens.PaperMinimalistTokens;

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

    // Bayer 4x4 ordered dithering matrix (normalized 0-1)
    private static final float[][] BAYER4 = {
        { 0/16f,  8/16f,  2/16f, 10/16f},
        {12/16f,  4/16f, 14/16f,  6/16f},
        { 3/16f, 11/16f,  1/16f,  9/16f},
        {15/16f,  7/16f, 13/16f,  5/16f}
    };

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (Theme.isPaper()) {
            paintPaperDithered(g2, w, h);
            g2.dispose();
            return;
        }

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

            // dynamics controls vertical height (dB scale)
            double dynH;
            if (dynOn) {
                double dynamics = Math.max(1e-6, interpolate(dynamicsEnv, t));
                double dB = 20 * Math.log10(dynamics);
                dynH = clamp((dB + 60.0) / 62.0, 0.0, 1.0);
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

        g2.setColor(synthColor);
        g2.fillOval(6, legendY - 6, 6, 6);
        g2.setColor(Theme.FG_MUTED);
        g2.drawString("Synth", 16, legendY);

        int sourceX = 16 + fm.stringWidth("Synth") + 12;
        g2.setColor(sourceColor);
        g2.fillOval(sourceX, legendY - 6, 6, 6);
        g2.setColor(Theme.FG_MUTED);
        g2.drawString("Source", sourceX + 10, legendY);

        g2.dispose();
    }

    // 8x8 Bayer ordered dithering matrix for smoother gradients
    private static final float[][] BAYER8 = new float[8][8];
    static {
        int[][] raw = {
            { 0, 32,  8, 40,  2, 34, 10, 42},
            {48, 16, 56, 24, 50, 18, 58, 26},
            {12, 44,  4, 36, 14, 46,  6, 38},
            {60, 28, 52, 20, 62, 30, 54, 22},
            { 3, 35, 11, 43,  1, 33,  9, 41},
            {51, 19, 59, 27, 49, 17, 57, 25},
            {15, 47,  7, 39, 13, 45,  5, 37},
            {63, 31, 55, 23, 61, 29, 53, 21}
        };
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                BAYER8[y][x] = raw[y][x] / 64f;
    }

    private static final Color SYNTH_INK        = new Color(0xD4, 0x85, 0x4A);
    private static final Color SYNTH_INK_LIGHT  = new Color(0xDF, 0xA0, 0x69);
    private static final Color SOURCE_INK       = new Color(0x5A, 0x8A, 0x6A);
    private static final Color SOURCE_INK_LIGHT = new Color(0x75, 0xA1, 0x85);

    // Extra shades for pitch extremes
    private static final Color SYNTH_XDARK  = new Color(0xB8, 0x70, 0x3A);
    private static final Color SYNTH_XLIGHT = new Color(0xEA, 0xB8, 0x88);
    private static final Color SOURCE_XDARK  = new Color(0x4A, 0x75, 0x58);
    private static final Color SOURCE_XLIGHT = new Color(0x95, 0xBD, 0xA5);

    // 4-shade palettes ordered dark→light
    private static final Color[] SYNTH_SHADES  = {SYNTH_XDARK, SYNTH_INK, SYNTH_INK_LIGHT, SYNTH_XLIGHT};
    private static final Color[] SOURCE_SHADES = {SOURCE_XDARK, SOURCE_INK, SOURCE_INK_LIGHT, SOURCE_XLIGHT};

    private static final Color SYNTH_TINT_DARK   = new Color(0xD4, 0x85, 0x4A, 45);
    private static final Color SYNTH_TINT_LIGHT  = new Color(0xEA, 0xB8, 0x88, 25);
    private static final Color SOURCE_TINT_DARK  = new Color(0x5A, 0x8A, 0x6A, 45);
    private static final Color SOURCE_TINT_LIGHT = new Color(0x90, 0xB8, 0xA0, 25);

    private void paintPaperDithered(Graphics2D g2, int w, int h) {
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, w - 1, h - 1,
                PaperMinimalistTokens.RADIUS_SM, PaperMinimalistTokens.RADIUS_SM);

        int pad = 2;
        int usableH = h - pad * 2;
        int usableW = w - pad * 2;
        int grainPx = 1;
        boolean dynOn = dynamicsEnabled.get();
        boolean pitchOn = pitchEnabled.get();

        java.awt.Shape oldClip = g2.getClip();
        g2.setClip(new java.awt.geom.RoundRectangle2D.Float(pad, pad, usableW, usableH,
                PaperMinimalistTokens.RADIUS_SM, PaperMinimalistTokens.RADIUS_SM));

        // Two-tone dithered tint background (full height)
        for (int gx = 0; gx < usableW; gx++) {
            double t = (double) gx / usableW;
            double mix = getEnvValue(mixEnv, t);
            int splitPx = pad + (int)(usableH * mix);
            for (int gy = pad; gy < pad + usableH; gy++) {
                int bx = ((int)(gx / 1.5)) & 7;
                int by = ((int)(gy / 1.5)) & 7;
                boolean dark = BAYER8[by][bx] < 0.4f;
                if (gy < splitPx) {
                    g2.setColor(dark ? SOURCE_TINT_DARK : SOURCE_TINT_LIGHT);
                } else {
                    g2.setColor(dark ? SYNTH_TINT_DARK : SYNTH_TINT_LIGHT);
                }
                g2.fillRect(pad + gx, gy, 1, 1);
            }
        }

        for (int gx = 0; gx < usableW; gx += grainPx) {
            double t = (double) gx / usableW;
            double density = getEnvValue(probEnv, t);
            double mix = getEnvValue(mixEnv, t);

            // Pitch → shade position (0=darkest, 1=lightest)
            double pitchRatio = pitchOn ? Math.max(0.25, interpolate(pitchEnv, t)) : 1.0;
            double semi = 12.0 * Math.log(pitchRatio) / Math.log(2);
            float pitchNorm = (float) clamp((semi + 24.0) / 48.0, 0.0, 1.0);

            // Dynamics → active height (dB scale)
            double dynH;
            if (dynOn) {
                double dynamics = Math.max(1e-6, interpolate(dynamicsEnv, t));
                double dB = 20 * Math.log10(dynamics);
                dynH = clamp((dB + 60.0) / 62.0, 0.0, 1.0);
            } else {
                dynH = 1.0;
            }
            int totalH = (int)(dynH * usableH);
            int topY = usableH - totalH;

            double splitInRegion = mix;
            int splitY = topY + (int)(totalH * splitInRegion);
            int colorBlendZone = grainPx * 8;

            for (int gy = topY; gy < usableH; gy += grainPx) {
                double blockCenterY = gy + grainPx / 2.0;
                double distFromSplit = blockCenterY - splitY;

                int bgx = (int)(gx / 1.5);
                int bgy = (int)(gy / 1.5);

                // Color-dithered boundary: which base color (synth vs source)
                boolean isSynth;
                if (distFromSplit > colorBlendZone / 2.0) {
                    isSynth = true;
                } else if (distFromSplit < -colorBlendZone / 2.0) {
                    isSynth = false;
                } else {
                    double colorBlend = (distFromSplit + colorBlendZone / 2.0) / colorBlendZone;
                    float colorThreshold = BAYER8[((bgy + 5) & 7)][((bgx + 3) & 7)];
                    isSynth = colorBlend > colorThreshold;
                }

                // Pitch-driven shade: dither between two adjacent shades from the 4-shade palette
                Color[] shades = isSynth ? SYNTH_SHADES : SOURCE_SHADES;
                float shadePos = pitchNorm * 3f; // 0..3 across 4 shades
                int shadeIdx = Math.min(2, (int) shadePos);
                float shadeFrac = shadePos - shadeIdx;
                float toneThreshold = BAYER8[((bgy + 2) & 7)][((bgx + 5) & 7)];
                Color ink = (shadeFrac > toneThreshold) ? shades[shadeIdx + 1] : shades[shadeIdx];

                // Fade near dynamics boundary
                int dynBlendZone = grainPx * 8;
                double fadedDensity = density;
                if (topY > 0 && gy - topY < dynBlendZone) {
                    fadedDensity = density * (double)(gy - topY) / dynBlendZone;
                }

                // Intensity dithering: density → ink vs white
                float threshold = BAYER8[(bgy & 7)][(bgx & 7)];

                if (fadedDensity > threshold) {
                    g2.setColor(ink);
                    g2.fillRect(pad + gx, pad + gy, grainPx, grainPx);
                }
            }
        }

        g2.setClip(oldClip);

        // Border
        g2.setColor(PaperMinimalistTokens.BORDER_FAINT);
        g2.drawRoundRect(0, 0, w - 1, h - 1,
                PaperMinimalistTokens.RADIUS_SM, PaperMinimalistTokens.RADIUS_SM);

        // Legend
        g2.setFont(PaperMinimalistTokens.FONT_BODY_XS);
        FontMetrics fm = g2.getFontMetrics();
        int legendY = fm.getAscent() + 4;

        g2.setColor(SYNTH_INK);
        g2.fillRect(6, legendY - 6, 6, 6);
        g2.setColor(PaperMinimalistTokens.INK_FAINT);
        g2.drawString("Synth", 16, legendY);

        int sourceX = 16 + fm.stringWidth("Synth") + 12;
        g2.setColor(SOURCE_INK);
        g2.fillRect(sourceX, legendY - 6, 6, 6);
        g2.setColor(PaperMinimalistTokens.INK_FAINT);
        g2.drawString("Source", sourceX + 10, legendY);
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
