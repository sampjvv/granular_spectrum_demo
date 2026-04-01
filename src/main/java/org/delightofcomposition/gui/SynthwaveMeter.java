package org.delightofcomposition.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

/**
 * Vertical segmented level meter.
 * When synthwave: segments colored green→yellow→red with lit/dim states.
 * Otherwise: simple colored bar.
 */
public class SynthwaveMeter extends JComponent {

    private static final int SEGMENTS = 12;
    private static final int SEG_GAP = 2;
    private static final int PREF_WIDTH = 10;
    private static final int PREF_HEIGHT = 80;

    private float level; // 0.0 to 1.0

    public SynthwaveMeter() {
        setOpaque(false);
    }

    /** Set level from 0.0 (silent) to 1.0 (max). */
    public void setLevel(float level) {
        float clamped = Math.max(0f, Math.min(1f, level));
        if (Math.abs(this.level - clamped) > 0.005f) {
            this.level = clamped;
            repaint();
        }
    }

    public float getLevel() {
        return level;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (Theme.isSynthwave()) {
            paintSegmented(g2, w, h);
        } else {
            paintSimpleBar(g2, w, h);
        }

        g2.dispose();
    }

    private void paintSegmented(Graphics2D g2, int w, int h) {
        int totalGap = (SEGMENTS - 1) * SEG_GAP;
        int segH = (h - totalGap) / SEGMENTS;
        int litSegments = (int) (level * SEGMENTS);

        for (int i = 0; i < SEGMENTS; i++) {
            // Segments are drawn bottom-to-top: i=0 is bottom
            int segIndex = SEGMENTS - 1 - i;
            int sy = i * (segH + SEG_GAP);

            // Color based on position
            Color bright, dim;
            if (segIndex >= SEGMENTS - 2) {
                // Top 2 = red
                bright = Theme.SW_RED != null ? Theme.SW_RED : Theme.DESTRUCTIVE;
                dim = new Color(bright.getRed() / 5, bright.getGreen() / 5, bright.getBlue() / 5);
            } else if (segIndex >= SEGMENTS - 4) {
                // Next 2 = yellow
                bright = Theme.SW_YELLOW != null ? Theme.SW_YELLOW : Theme.AMBER;
                dim = new Color(bright.getRed() / 5, bright.getGreen() / 5, bright.getBlue() / 5);
            } else {
                // Rest = green
                bright = Theme.SW_GREEN != null ? Theme.SW_GREEN : Theme.SUCCESS;
                dim = new Color(bright.getRed() / 5, bright.getGreen() / 5, bright.getBlue() / 5);
            }

            boolean lit = segIndex < litSegments;
            g2.setColor(lit ? bright : dim);
            g2.fillRect(1, sy, w - 2, segH);
        }

        // Outer border
        if (Theme.isSynthwave() && Theme.SW_PURPLE != null) {
            g2.setColor(Theme.SW_PURPLE);
            g2.setStroke(new java.awt.BasicStroke(2f));
            g2.drawRect(0, 0, w - 1, h - 1);
        }
    }

    private void paintSimpleBar(Graphics2D g2, int w, int h) {
        // Background
        g2.setColor(Theme.BG_MUTED);
        g2.fillRoundRect(0, 0, w, h, 4, 4);

        // Fill from bottom
        int fillH = (int) (level * h);
        if (fillH > 0) {
            g2.setColor(Theme.SUCCESS);
            g2.fillRoundRect(0, h - fillH, w, fillH, 4, 4);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(PREF_WIDTH, PREF_HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(PREF_WIDTH, 40);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(PREF_WIDTH, Integer.MAX_VALUE);
    }
}
