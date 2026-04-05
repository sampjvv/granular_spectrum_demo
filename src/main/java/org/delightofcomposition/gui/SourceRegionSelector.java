package org.delightofcomposition.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.sound.WaveWriter;

/**
 * Displays the full source waveform with draggable start/end region markers.
 * Shows total duration, selected duration, and pitch-adjusted output duration.
 */
public class SourceRegionSelector extends JComponent {

    private static final int HANDLE_W = 8;
    private static final int HIT_ZONE = 10;
    private static final int LABEL_PAD = 6;
    private static final int PAD_H = 6; // horizontal padding to keep handles in bounds

    private final SynthParameters params;
    private float[] waveformData;
    private final List<ChangeListener> listeners = new ArrayList<>();

    private enum Drag { NONE, START, END, REGION }
    private Drag dragging = Drag.NONE;
    private double dragOffset; // for region drag: offset from mouse to start marker

    public SourceRegionSelector(SynthParameters params) {
        this.params = params;
        setPreferredSize(new Dimension(0, 80));
        setMinimumSize(new Dimension(0, 60));
        setOpaque(false);
        setFocusable(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (waveformData == null) return;
                int w = getWidth();
                int waveW = w - 2 * PAD_H;
                double frac = Math.max(0, Math.min(1, (e.getX() - PAD_H) / (double) waveW));
                double startX = PAD_H + params.sourceStartFraction * waveW;
                double endX = PAD_H + params.sourceEndFraction * waveW;

                if (Math.abs(e.getX() - startX) <= HIT_ZONE) {
                    dragging = Drag.START;
                } else if (Math.abs(e.getX() - endX) <= HIT_ZONE) {
                    dragging = Drag.END;
                } else if (frac > params.sourceStartFraction && frac < params.sourceEndFraction) {
                    dragging = Drag.REGION;
                    dragOffset = frac - params.sourceStartFraction;
                } else {
                    dragging = Drag.NONE;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging == Drag.NONE || waveformData == null) return;
                int waveW = getWidth() - 2 * PAD_H;
                double frac = Math.max(0, Math.min(1, (e.getX() - PAD_H) / (double) waveW));
                double minRegion = 0.01;

                switch (dragging) {
                    case START:
                        params.sourceStartFraction = Math.min(frac, params.sourceEndFraction - minRegion);
                        break;
                    case END:
                        params.sourceEndFraction = Math.max(frac, params.sourceStartFraction + minRegion);
                        break;
                    case REGION:
                        double regionLen = params.sourceEndFraction - params.sourceStartFraction;
                        double newStart = frac - dragOffset;
                        newStart = Math.max(0, Math.min(1 - regionLen, newStart));
                        params.sourceStartFraction = newStart;
                        params.sourceEndFraction = newStart + regionLen;
                        break;
                    default:
                        break;
                }
                fireChange();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = Drag.NONE;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (waveformData == null) return;
                int waveW = getWidth() - 2 * PAD_H;
                double startX = PAD_H + params.sourceStartFraction * waveW;
                double endX = PAD_H + params.sourceEndFraction * waveW;
                if (Math.abs(e.getX() - startX) <= HIT_ZONE || Math.abs(e.getX() - endX) <= HIT_ZONE) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                } else {
                    double frac = Math.max(0, Math.min(1, (e.getX() - PAD_H) / (double) waveW));
                    if (frac > params.sourceStartFraction && frac < params.sourceEndFraction) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    } else {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setWaveformData(float[] samples) {
        this.waveformData = samples;
        repaint();
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    private void fireChange() {
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(e);
    }

    /**
     * Computes the average pitch ratio from the pitch envelope.
     */
    private double getAvgPitchRatio() {
        if (!params.usePitchEnv || params.pitchEnv == null) return 1.0;
        double sum = 0;
        int N = 200;
        for (int i = 0; i <= N; i++) {
            sum += params.pitchEnv.getValue(i / (double) N);
        }
        return sum / (N + 1);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Background card
        if (Theme.isSynthwave()) {
            SynthwavePainter.fillPanel(g2, 0, 0, w, h, Theme.BG_CARD, Theme.BORDER_SUBTLE);
        } else {
            g2.setColor(Theme.BG_CARD);
            g2.fillRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
            g2.setColor(Theme.BORDER_SUBTLE);
            g2.drawRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
        }

        if (waveformData == null || waveformData.length == 0) {
            g2.setFont(Theme.FONT_SMALL);
            g2.setColor(Theme.FG_DIM);
            String msg = "No source sample loaded";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2 + fm.getAscent() / 2);
            g2.dispose();
            return;
        }

        int padTop = 16;
        int padBot = 16;
        int waveH = h - padTop - padBot;
        int centerY = padTop + waveH / 2;

        // Draw waveform (within padded area)
        int waveW = w - 2 * PAD_H;
        Color waveColor = Theme.isPaper() ? new Color(0x90, 0xB8, 0xA0, 140) : new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(),
                Theme.ACCENT.getBlue(), 100);
        g2.setColor(waveColor);
        for (int px = 0; px < waveW; px++) {
            int s0 = (int) ((long) px * waveformData.length / waveW);
            int s1 = (int) ((long) (px + 1) * waveformData.length / waveW);
            s0 = Math.max(0, Math.min(waveformData.length - 1, s0));
            s1 = Math.max(s0 + 1, Math.min(waveformData.length, s1));
            float minVal = 0, maxVal = 0;
            for (int s = s0; s < s1; s++) {
                if (waveformData[s] < minVal) minVal = waveformData[s];
                if (waveformData[s] > maxVal) maxVal = waveformData[s];
            }
            int yMin = centerY - (int) (maxVal * (waveH / 2));
            int yMax = centerY - (int) (minVal * (waveH / 2));
            if (yMax - yMin > 0) g2.fillRect(PAD_H + px, yMin, 1, yMax - yMin);
        }

        // Dim outside selected region
        int startX = PAD_H + (int) (params.sourceStartFraction * waveW);
        int endX = PAD_H + (int) (params.sourceEndFraction * waveW);

        Color dim = new Color(Theme.BG.getRed(), Theme.BG.getGreen(), Theme.BG.getBlue(), 160);
        if (startX > 0) {
            g2.setColor(dim);
            g2.fillRect(0, 0, startX, h);
        }
        if (endX < w) {
            g2.setColor(dim);
            g2.fillRect(endX, 0, w - endX, h);
        }

        // Marker lines
        Color markerColor = Theme.isPaper() ? new Color(0x44, 0x44, 0x44) : Theme.ACCENT;
        g2.setColor(markerColor);
        g2.fillRect(startX - 1, 0, 2, h);
        g2.fillRect(endX - 1, 0, 2, h);

        // Marker handles (small rectangles at top)
        if (Theme.isSynthwave()) {
            SynthwavePainter.fillShape(g2, startX - HANDLE_W / 2, 0, HANDLE_W, 14, Theme.ACCENT);
            SynthwavePainter.fillShape(g2, endX - HANDLE_W / 2, 0, HANDLE_W, 14, Theme.ACCENT);
        } else {
            g2.fillRoundRect(startX - HANDLE_W / 2, 0, HANDLE_W, 14, 4, 4);
            g2.fillRoundRect(endX - HANDLE_W / 2, 0, HANDLE_W, 14, 4, 4);
        }

        // Duration labels
        g2.setFont(Theme.FONT_SMALL);
        FontMetrics fm = g2.getFontMetrics();
        double totalSec = waveformData.length / (double) WaveWriter.SAMPLE_RATE;
        double regionFrac = params.sourceEndFraction - params.sourceStartFraction;
        double regionSec = regionFrac * totalSec;
        double avgRatio = getAvgPitchRatio();
        double outputSec = regionSec / avgRatio;

        // Total duration (top left)
        g2.setColor(Theme.FG_DIM);
        String totalStr = formatDuration(totalSec);
        g2.drawString(totalStr, PAD_H + LABEL_PAD, fm.getAscent() + 2);

        // Selected duration (bottom center)
        String selStr = "Selection: " + formatDuration(regionSec);
        int selW = fm.stringWidth(selStr);
        int selX = (startX + endX - selW) / 2;
        selX = Math.max(PAD_H + LABEL_PAD, Math.min(w - PAD_H - selW - LABEL_PAD, selX));
        g2.setColor(Theme.FG);
        g2.drawString(selStr, selX, h - padBot / 2 + fm.getAscent() / 2 - 4);

        // Output duration (top right)
        String outStr = "Output: " + formatDuration(outputSec);
        g2.setColor(Theme.FG_DIM);
        g2.drawString(outStr, w - PAD_H - fm.stringWidth(outStr) - LABEL_PAD, fm.getAscent() + 2);

        // Focus ring
        if (isFocusOwner() && !Theme.isSynthwave()) {
            if (Theme.isSynthwave()) {
                SynthwavePainter.strokeShape(g2, 1, 1, w - 2, h - 2, Theme.RING);
            } else {
                g2.setColor(Theme.RING);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, Theme.RADIUS, Theme.RADIUS);
            }
        }

        g2.dispose();
    }

    private static String formatDuration(double seconds) {
        if (seconds < 60) {
            return String.format("%.1fs", seconds);
        }
        int min = (int) (seconds / 60);
        double sec = seconds - min * 60;
        return String.format("%d:%04.1f", min, sec);
    }
}
