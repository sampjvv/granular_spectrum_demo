package org.delightofcomposition.gui;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

/** Programmatically drawn app icon, one motif per theme. */
public final class AppIcon {

    private AppIcon() {}

    private static final double[] HEIGHTS = {0.40, 0.80, 0.55, 1.00, 0.35};

    // Paper: pastel multi-color bars on near-white (uses the theme's own pastel palette)
    private static final Color PAPER_BG     = new Color(252, 252, 252);
    private static final Color PAPER_PINK   = new Color(0xE8, 0x90, 0xB0);
    private static final Color PAPER_BLUE   = new Color(0xB8, 0xC4, 0xE8);
    private static final Color PAPER_GREEN  = new Color(0x9C, 0xD0, 0x9C);
    private static final Color PAPER_YELLOW = new Color(0xE0, 0xC8, 0x60);
    private static final Color[] PAPER_BARS = {
            PAPER_BLUE, PAPER_PINK, PAPER_GREEN, PAPER_YELLOW, PAPER_PINK };

    // Paper Minimalist: alt green/orange solid bars on beige
    private static final Color PMIN_BG     = new Color(238, 224, 195);
    private static final Color PMIN_GREEN  = new Color( 94, 140,  92);
    private static final Color PMIN_ORANGE = new Color(227, 127,  48);
    private static final Color[] PMIN_BARS = {
            PMIN_ORANGE, PMIN_GREEN, PMIN_ORANGE, PMIN_GREEN, PMIN_ORANGE };

    // Midnight Indigo: uniform indigo bars on near-black (monochrome, minimal)
    private static final Color INDIGO_BG   = new Color( 12,  12,  18);
    private static final Color INDIGO_BAR  = new Color( 99, 102, 241);
    private static final Color[] INDIGO_BARS = {
            INDIGO_BAR, INDIGO_BAR, INDIGO_BAR, INDIGO_BAR, INDIGO_BAR };

    // Synthwave: cyan→pink gradient bars on deep blue
    private static final Color SW_BG     = new Color( 20,  26,  40);
    private static final Color SW_TOP    = new Color(120, 220, 255);
    private static final Color SW_BOTTOM = new Color(255, 140, 210);

    public static List<Image> build() {
        return Arrays.asList(
                render(16), render(32), render(48),
                render(64), render(128), render(256));
    }

    private static BufferedImage render(int size) {
        ThemePreset preset = Theme.getPreset();
        Color bg;
        Color[] solidBars;
        Color gradTop = null, gradBot = null;

        switch (preset) {
            case PAPER:
                bg = PAPER_BG;  solidBars = PAPER_BARS;  break;
            case PAPER_MINIMALIST:
                bg = PMIN_BG;   solidBars = PMIN_BARS;   break;
            case NEON_OUTRUN:
                bg = SW_BG;     solidBars = null;
                gradTop = SW_TOP; gradBot = SW_BOTTOM;   break;
            case DEFAULT_DARK:
            default:
                bg = INDIGO_BG; solidBars = INDIGO_BARS; break;
        }

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int arc = size / 4;
        g.setColor(bg);
        g.fillRoundRect(0, 0, size, size, arc, arc);

        drawBars(g, size, solidBars, gradTop, gradBot);

        g.dispose();
        return img;
    }

    private static void drawBars(Graphics2D g, int size, Color[] solidBars,
                                 Color gradTop, Color gradBot) {
        int bars = HEIGHTS.length;
        int pad = Math.max(1, size / 6);
        int avail = size - 2 * pad;
        float gap = Math.max(1f, size / 24f);
        float barW = (avail - gap * (bars - 1)) / (float) bars;
        int baseY = size - pad;

        for (int i = 0; i < bars; i++) {
            int h = Math.max(1, (int) (HEIGHTS[i] * avail));
            int x = (int) (pad + i * (barW + gap));
            int w = Math.max(1, (int) barW);
            int r = Math.max(1, w / 2);

            if (solidBars != null) {
                g.setColor(solidBars[i]);
            } else {
                g.setPaint(new GradientPaint(0, baseY - h, gradTop, 0, baseY, gradBot));
            }
            g.fillRoundRect(x, baseY - h, w, h, r, r);
        }
    }
}
