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
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.sound.WaveWriter;

/**
 * Palindrome crossfade editor: toggle + interactive waveform visualization
 * showing forward and reversed regions with draggable crossfade controls.
 */
public class PalindromeCrossfadePanel extends JPanel {

    private static final int HEADER_H = 44;
    private static final int VIZ_H = 88;
    private static final int PAD_H = 6;
    private static final int HIT_ZONE = 10;

    private final SynthParameters params;
    private final ToggleSwitch palindromeToggle;
    private final JButton modeBtn;
    private final CrossfadeCanvas canvas;
    private final List<ChangeListener> listeners = new ArrayList<>();
    private boolean overlapMode = false; // false = crossfade curve, true = overlap plateau

    // Animation
    private int animTargetH = HEADER_H;
    private int animCurrentH = HEADER_H;
    private Timer animTimer;

    public PalindromeCrossfadePanel(SynthParameters params) {
        this.params = params;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        // Header row with toggle
        palindromeToggle = new ToggleSwitch(params.usePalindrome);
        palindromeToggle.addChangeListener(e -> {
            params.usePalindrome = palindromeToggle.isSelected();
            animateTo(params.usePalindrome ? HEADER_H + VIZ_H : HEADER_H);
            fireChangeListeners();
        });
        // Mode button
        modeBtn = Theme.ghostButton("Crossfade");
        Theme.tagFont(modeBtn, "small");
        modeBtn.setPreferredSize(new Dimension(80, 24));
        modeBtn.setFocusable(false);
        modeBtn.addActionListener(e -> {
            overlapMode = !overlapMode;
            modeBtn.setText(overlapMode ? "Overlap" : "Crossfade");
            repaint();
        });

        JPanel headerRow = new JPanel(new java.awt.BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(0);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, HEADER_H));
        headerRow.setPreferredSize(new Dimension(200, HEADER_H));
        headerRow.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        javax.swing.JLabel lbl = Theme.paramLabel("Palindrome");
        lbl.setVerticalAlignment(javax.swing.SwingConstants.CENTER);
        headerRow.add(lbl, java.awt.BorderLayout.WEST);
        JPanel rightControls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        rightControls.setOpaque(false);
        rightControls.add(modeBtn);
        rightControls.add(palindromeToggle);
        headerRow.add(rightControls, java.awt.BorderLayout.EAST);
        add(headerRow);

        // Crossfade canvas
        canvas = new CrossfadeCanvas();
        canvas.setAlignmentX(0);
        canvas.setVisible(params.usePalindrome);
        add(canvas);

        animCurrentH = params.usePalindrome ? HEADER_H + VIZ_H : HEADER_H;
        animTargetH = animCurrentH;
    }

    private void animateTo(int targetH) {
        animTargetH = targetH;
        if (animTimer != null) animTimer.stop();
        animTimer = new Timer(12, e -> {
            int diff = animTargetH - animCurrentH;
            if (Math.abs(diff) <= 4) {
                animCurrentH = animTargetH;
                animTimer.stop();
            } else {
                animCurrentH += diff / 3;
            }
            canvas.setVisible(animCurrentH > HEADER_H + 10);
            revalidate();
            repaint();
            if (getParent() != null) {
                getParent().revalidate();
                getParent().repaint();
            }
        });
        animTimer.start();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(0, animCurrentH);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, animCurrentH);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, animCurrentH);
    }

    public void setWaveformData(float[] data) {
        canvas.setWaveformData(data);
    }

    public void syncToParams() {
        palindromeToggle.setSelected(params.usePalindrome);
        animCurrentH = params.usePalindrome ? HEADER_H + VIZ_H : HEADER_H;
        animTargetH = animCurrentH;
        canvas.setVisible(params.usePalindrome);
        canvas.repaint();
        revalidate();
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    private void fireChangeListeners() {
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(e);
    }

    // ─────────────────────────────────────────────────────────
    // Crossfade visualization canvas
    // ─────────────────────────────────────────────────────────
    private class CrossfadeCanvas extends JComponent {

        private float[] waveformData;

        private enum Drag { NONE, DURATION, DIAMOND }
        private Drag dragging = Drag.NONE;
        private int dragStartX;
        private double dragStartDuration;

        CrossfadeCanvas() {
            setPreferredSize(new Dimension(0, VIZ_H));
            setMinimumSize(new Dimension(0, VIZ_H));
            setOpaque(false);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int x = e.getX(), y = e.getY();
                    int w = getWidth();
                    int drawW = w - 2 * PAD_H;

                    double regionSec = getRegionDurationSec();
                    if (regionSec <= 0) return;
                    double cfDur = Math.min(params.crossfadeDuration, regionSec * 0.95);
                    double totalPalinSec = 2 * regionSec - cfDur;
                    if (totalPalinSec <= 0) totalPalinSec = regionSec;

                    double cfStartFrac = (regionSec - cfDur) / totalPalinSec;
                    double cfEndFrac = regionSec / totalPalinSec;
                    int cfStartX = PAD_H + (int) (cfStartFrac * drawW);
                    int cfEndX = PAD_H + (int) (cfEndFrac * drawW);

                    // Check duration handle (left edge of crossfade)
                    if (Math.abs(x - cfStartX) < HIT_ZONE) {
                        dragging = Drag.DURATION;
                        dragStartX = x;
                        dragStartDuration = params.crossfadeDuration;
                        return;
                    }

                    // Check diamond handle (midpoint of crossfade zone)
                    if (cfEndX - cfStartX > 20) {
                        int handleX = (cfStartX + cfEndX) / 2;
                        int handleY = getDiamondHandleY(getHeight());
                        if (Math.abs(x - handleX) < HIT_ZONE && Math.abs(y - handleY) < HIT_ZONE) {
                            dragging = Drag.DIAMOND;
                            return;
                        }
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    int w = getWidth();
                    int drawW = w - 2 * PAD_H;
                    double regionSec = getRegionDurationSec();
                    if (regionSec <= 0) return;

                    if (dragging == Drag.DURATION) {
                        int deltaX = e.getX() - dragStartX;
                        double secPerPixel = regionSec / (drawW / 2.0);
                        double duration = dragStartDuration - deltaX * secPerPixel;
                        duration = Math.max(0, Math.min(regionSec * 0.95, duration));
                        params.crossfadeDuration = Math.round(duration * 20) / 20.0;
                        repaint();
                        fireChangeListeners();
                    } else if (dragging == Drag.DIAMOND) {
                        int h = getHeight();
                        int padTop = 16;
                        int padBot = 14;
                        int waveH = h - padTop - padBot;
                        double frac = (e.getY() - padTop) / (double) waveH;
                        frac = Math.max(0, Math.min(1, frac));
                        if (overlapMode) {
                            // Top = 1.0 (full overlap), bottom = 0.0 (standard crossfade)
                            params.crossfadeOverlap = 1.0 - frac;
                        } else {
                            // Top = +1.0 (convex/slow), bottom = -1.0 (concave/fast)
                            params.crossfadeCurve = 1.0 - 2.0 * frac;
                        }
                        repaint();
                        fireChangeListeners();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragging = Drag.NONE;
                    setCursor(Cursor.getDefaultCursor());
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    int x = e.getX(), y = e.getY();
                    int w = getWidth();
                    int drawW = w - 2 * PAD_H;
                    double regionSec = getRegionDurationSec();
                    if (regionSec <= 0) return;
                    double cfDur = Math.min(params.crossfadeDuration, regionSec * 0.95);
                    double totalPalinSec = 2 * regionSec - cfDur;
                    if (totalPalinSec <= 0) totalPalinSec = regionSec;

                    double cfStartFrac = (regionSec - cfDur) / totalPalinSec;
                    double cfEndFrac = regionSec / totalPalinSec;
                    int cfStartX = PAD_H + (int) (cfStartFrac * drawW);
                    int cfEndX = PAD_H + (int) (cfEndFrac * drawW);

                    if (Math.abs(x - cfStartX) < HIT_ZONE) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                    } else if (cfEndX - cfStartX > 20) {
                        int handleX = (cfStartX + cfEndX) / 2;
                        int handleY = getDiamondHandleY(getHeight());
                        if (Math.abs(x - handleX) < HIT_ZONE && Math.abs(y - handleY) < HIT_ZONE) {
                            setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                        } else {
                            setCursor(Cursor.getDefaultCursor());
                        }
                    } else {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void setWaveformData(float[] data) {
            this.waveformData = data;
            repaint();
        }

        private double getRegionDurationSec() {
            if (waveformData == null || waveformData.length == 0) return 0;
            double totalSec = waveformData.length / (double) WaveWriter.SAMPLE_RATE;
            return (params.sourceEndFraction - params.sourceStartFraction) * totalSec;
        }

        private int getDiamondHandleY(int h) {
            int padTop = 16;
            int padBot = 14;
            int waveH = h - padTop - padBot;
            double frac;
            if (overlapMode) {
                // overlap: 1.0 = top, 0.0 = bottom
                frac = 1.0 - params.crossfadeOverlap;
            } else {
                // curve: +1.0 = top, -1.0 = bottom
                frac = (1.0 - params.crossfadeCurve) / 2.0;
            }
            return padTop + (int) (frac * waveH);
        }

        /** Compute gain values based on current mode. */
        private double[] computeGains(double linearPos, double curveExp) {
            double fwdGain, revGain;
            if (overlapMode) {
                // Three-zone overlap plateau model
                double overlap = params.crossfadeOverlap;
                double fadeZone = (1.0 - overlap) / 2.0;
                if (fadeZone < 0.001) {
                    fwdGain = 1.0; revGain = 1.0;
                } else {
                    if (linearPos < fadeZone) {
                        revGain = Math.pow(linearPos / fadeZone, curveExp);
                    } else {
                        revGain = 1.0;
                    }
                    if (linearPos < fadeZone + overlap) {
                        fwdGain = 1.0;
                    } else {
                        double f = (linearPos - fadeZone - overlap) / fadeZone;
                        fwdGain = 1.0 - Math.pow(Math.min(1, f), curveExp);
                    }
                }
            } else {
                // Classic crossfade with curve shaping
                double shapedMix = Math.pow(linearPos, curveExp);
                fwdGain = 1.0 - shapedMix;
                revGain = shapedMix;
            }
            return new double[]{fwdGain, revGain};
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Waveform inset background (lighter than parent card)
            Color waveBg = Theme.isPaper()
                    ? new Color(0xFF, 0xFE, 0xFD)
                    : Theme.BG_INPUT;
            g2.setColor(waveBg);
            int bgInset = 6;
            g2.fillRoundRect(0, bgInset, w - 1, h - 1 - 2 * bgInset, Theme.RADIUS_SM, Theme.RADIUS_SM);

            if (waveformData == null || waveformData.length == 0) {
                g2.dispose();
                return;
            }

            double regionSec = getRegionDurationSec();
            if (regionSec <= 0) { g2.dispose(); return; }

            double cfDur = Math.min(params.crossfadeDuration, regionSec * 0.95);
            double totalPalinSec = 2 * regionSec - cfDur;

            int padTop = 16;
            int padBot = 14;
            int waveH = h - padTop - padBot;
            int centerY = padTop + waveH / 2;
            int drawW = w - 2 * PAD_H;

            // Compute crossfade zone pixel boundaries
            double cfStartFrac = (regionSec - cfDur) / totalPalinSec;
            double cfEndFrac = regionSec / totalPalinSec;
            int cfStartPx = PAD_H + (int) (cfStartFrac * drawW);
            int cfEndPx = PAD_H + (int) (cfEndFrac * drawW);

            // Shade crossfade zone (full background height)
            int bgTop = bgInset;
            int bgH = h - 1 - 2 * bgInset;
            Color cfShade = new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(),
                    Theme.ACCENT.getBlue(), 25);
            g2.setColor(cfShade);
            g2.fillRect(cfStartPx, bgTop, cfEndPx - cfStartPx, bgH);

            // Extract region samples for waveform drawing
            int regionStart = (int) (params.sourceStartFraction * waveformData.length);
            int regionEnd = (int) (params.sourceEndFraction * waveformData.length);
            int regionLen = regionEnd - regionStart;
            if (regionLen < 2) { g2.dispose(); return; }

            // Forward waveform color
            Color fwdColor = Theme.isPaper()
                    ? new Color(0x90, 0xB8, 0xA0, 120)
                    : new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(),
                            Theme.ACCENT.getBlue(), 90);

            // Reversed waveform color
            Color revColor = Theme.isPaper()
                    ? new Color(0xA0, 0x90, 0xB8, 120)
                    : new Color(Theme.SUCCESS.getRed(), Theme.SUCCESS.getGreen(),
                            Theme.SUCCESS.getBlue(), 90);

            // ── Draw forward waveform (left to cfEnd) ──
            drawWaveformRange(g2, fwdColor, PAD_H, cfEndPx, drawW, centerY, waveH,
                    regionStart, regionLen, false);

            // ── Draw reversed waveform (cfStart to right edge) ──
            drawWaveformRange(g2, revColor, cfStartPx, PAD_H + drawW, drawW, centerY, waveH,
                    regionStart, regionLen, true);

            // ── Gain curves ──
            if (cfEndPx - cfStartPx > 4) {
                drawGainCurves(g2, cfStartPx, cfEndPx, padTop, waveH, fwdColor, revColor);
            }

            // ── Duration handle (left edge of crossfade, full background height) ──
            Color handleColor = Theme.isPaper() ? new Color(0x44, 0x44, 0x44) : Theme.ACCENT;
            g2.setColor(handleColor);
            g2.fillRect(cfStartPx - 1, bgTop, 2, bgH);
            g2.fillRoundRect(cfStartPx - 4, bgTop, 8, 14, 4, 4);

            // ── Dashed line at right edge of crossfade zone (full background height) ──
            g2.setColor(handleColor);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{4f, 4f}, 0f));
            g2.drawLine(cfEndPx, bgTop, cfEndPx, bgTop + bgH);
            g2.setStroke(new BasicStroke(1f));

            // ── Overlap handle (diamond at midpoint of crossfade zone) ──
            if (cfEndPx - cfStartPx > 20) {
                int handleX = (cfStartPx + cfEndPx) / 2;
                int handleY = getDiamondHandleY(h);
                g2.setColor(handleColor);
                int ds = 5;
                g2.fillPolygon(
                        new int[]{handleX, handleX + ds, handleX, handleX - ds},
                        new int[]{handleY - ds, handleY, handleY + ds, handleY},
                        4);
            }

            // ── Labels ──
            g2.setFont(Theme.FONT_SMALL);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.FG_DIM);
            String cfLabel = String.format("Crossfade: %.2fs", cfDur);
            int labelX = Math.max(PAD_H + 4, cfStartPx - fm.stringWidth(cfLabel) - 6);
            g2.drawString(cfLabel, labelX, h - 3);

            // Forward / Reverse labels
            if (drawW > 200) {
                g2.setColor(new Color(fwdColor.getRed(), fwdColor.getGreen(), fwdColor.getBlue(), 200));
                g2.drawString("Forward", PAD_H + 4, padTop + fm.getAscent() + 2);
                g2.setColor(new Color(revColor.getRed(), revColor.getGreen(), revColor.getBlue(), 200));
                String revStr = "Reversed";
                g2.drawString(revStr, PAD_H + drawW - fm.stringWidth(revStr) - 4, padTop + fm.getAscent() + 2);
            }

            g2.dispose();
        }

        private void drawWaveformRange(Graphics2D g2, Color color,
                int pxStart, int pxEnd, int drawW,
                int centerY, int waveH,
                int regionStart, int regionLen, boolean reversed) {
            g2.setColor(color);
            double regionSec = getRegionDurationSec();
            double cfDur = Math.min(params.crossfadeDuration, regionSec * 0.95);
            double totalPalinSec = 2 * regionSec - cfDur;

            for (int px = pxStart; px < pxEnd; px++) {
                double palinFrac = (px - PAD_H) / (double) drawW;
                double palinTimeSec = palinFrac * totalPalinSec;

                double sourceFrac;
                if (palinTimeSec >= regionSec - cfDur && palinTimeSec <= regionSec) {
                    // Crossfade zone — both contribute
                    if (!reversed) {
                        sourceFrac = palinTimeSec / regionSec;
                    } else {
                        double revTime = palinTimeSec - (regionSec - cfDur);
                        sourceFrac = 1.0 - revTime / regionSec;
                    }
                } else if (palinTimeSec < regionSec - cfDur) {
                    if (reversed) continue;
                    sourceFrac = palinTimeSec / regionSec;
                } else {
                    if (!reversed) continue;
                    double revTime = palinTimeSec - (regionSec - cfDur);
                    sourceFrac = 1.0 - revTime / regionSec;
                }

                sourceFrac = Math.max(0, Math.min(1, sourceFrac));
                int sampleIdx = regionStart + (int) (sourceFrac * (regionLen - 1));

                int range = Math.max(1, regionLen / drawW);
                int s0 = Math.max(regionStart, sampleIdx - range / 2);
                int s1 = Math.min(regionStart + regionLen, sampleIdx + range / 2 + 1);
                float minVal = 0, maxVal = 0;
                for (int s = s0; s < s1; s++) {
                    if (s >= 0 && s < waveformData.length) {
                        if (waveformData[s] < minVal) minVal = waveformData[s];
                        if (waveformData[s] > maxVal) maxVal = waveformData[s];
                    }
                }

                int yMin = centerY - (int) (maxVal * (waveH / 2));
                int yMax = centerY - (int) (minVal * (waveH / 2));
                if (yMax - yMin > 0) g2.fillRect(px, yMin, 1, yMax - yMin);
            }
        }

        private void drawGainCurves(Graphics2D g2, int cfStartPx, int cfEndPx,
                int padTop, int waveH, Color fwdColor, Color revColor) {
            int cfW = cfEndPx - cfStartPx;
            if (cfW < 2) return;

            double curveExp = Math.exp(-params.crossfadeCurve);

            Path2D fwdCurve = new Path2D.Double();
            Path2D revCurve = new Path2D.Double();
            int bottom = padTop + waveH;

            for (int px = 0; px <= cfW; px++) {
                double linearPos = px / (double) cfW;
                double[] gains = computeGains(linearPos, curveExp);

                int fwdY = bottom - (int) (gains[0] * waveH);
                int revY = bottom - (int) (gains[1] * waveH);
                int x = cfStartPx + px;

                if (px == 0) {
                    fwdCurve.moveTo(x, fwdY);
                    revCurve.moveTo(x, revY);
                } else {
                    fwdCurve.lineTo(x, fwdY);
                    revCurve.lineTo(x, revY);
                }
            }

            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(fwdColor.getRed(), fwdColor.getGreen(), fwdColor.getBlue(), 200));
            g2.draw(fwdCurve);
            g2.setColor(new Color(revColor.getRed(), revColor.getGreen(), revColor.getBlue(), 200));
            g2.draw(revCurve);
            g2.setStroke(new BasicStroke(1f));
        }
    }
}
