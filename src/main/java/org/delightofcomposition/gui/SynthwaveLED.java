package org.delightofcomposition.gui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Small LED status indicator.
 * When synthwave: pixel-circle with glow and optional blink.
 * Otherwise: simple colored circle.
 */
public class SynthwaveLED extends JComponent {

    public enum LedColor {
        CYAN, PINK, GREEN, YELLOW, RED
    }

    private static final int SIZE = 12;

    private LedColor ledColor;
    private boolean on;
    private boolean blinking;
    private Timer blinkTimer;

    public SynthwaveLED(LedColor color) {
        this.ledColor = color;
        this.on = false;
        setOpaque(false);
    }

    public void setOn(boolean on) {
        if (this.on != on) {
            this.on = on;
            repaint();
        }
    }

    public boolean isOn() {
        return on;
    }

    public void setBlinking(boolean blink) {
        if (this.blinking == blink) return;
        this.blinking = blink;
        if (blink) {
            if (blinkTimer == null) {
                blinkTimer = new Timer(500, e -> {
                    on = !on;
                    repaint();
                });
            }
            blinkTimer.start();
        } else {
            if (blinkTimer != null) blinkTimer.stop();
        }
    }

    public void setLedColor(LedColor color) {
        this.ledColor = color;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bright = resolveColor();
        Color dim = new Color(bright.getRed() / 3, bright.getGreen() / 3, bright.getBlue() / 3);
        Color fill = on ? bright : dim;

        int cx = getWidth() / 2;
        int cy = getHeight() / 2;

        if (Theme.isSynthwave()) {
            // Pixel circle
            Polygon circle = SynthwavePainter.pixelCircle(cx, cy, SIZE);
            g2.setColor(fill);
            g2.fillPolygon(circle);

            // Glow when on
            if (on) {
                Composite orig = g2.getComposite();
                for (int i = 3; i >= 1; i--) {
                    float alpha = 0.12f * (1.0f - (float) i / 4);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    g2.setColor(bright);
                    Polygon glow = SynthwavePainter.pixelCircle(cx, cy, SIZE + i * 4);
                    g2.fillPolygon(glow);
                }
                g2.setComposite(orig);
            }
        } else {
            // Simple circle
            g2.setColor(fill);
            g2.fillOval(cx - SIZE / 2, cy - SIZE / 2, SIZE, SIZE);
        }

        g2.dispose();
    }

    private Color resolveColor() {
        if (Theme.isSynthwave()) {
            switch (ledColor) {
                case CYAN:   return Theme.SW_CYAN;
                case PINK:   return Theme.SW_HOT_PINK;
                case GREEN:  return Theme.SW_GREEN;
                case YELLOW: return Theme.SW_YELLOW;
                case RED:    return Theme.SW_RED;
            }
        }
        switch (ledColor) {
            case CYAN:   return Theme.SUCCESS;
            case PINK:   return Theme.ACCENT;
            case GREEN:  return Theme.SUCCESS;
            case YELLOW: return Theme.AMBER;
            case RED:    return Theme.DESTRUCTIVE;
        }
        return Theme.ACCENT;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SIZE + 8, SIZE + 8);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}
