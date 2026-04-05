package org.delightofcomposition.gui;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import com.sptc.uilab.tokens.PaperMinimalistTokens;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Full-width slider with label/value header row.
 * Custom painted track (6px, rounded ends, accent fill) and 18px circular thumb.
 */
public class LabeledSlider extends JPanel {

    private static final int TRACK_H = 6;
    private static final int THUMB_SIZE = 18;
    private static final int GAP = 4;

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

    private static int headerH() { return Theme.isSynthwave() ? 26 : 18; }
    private static int sliderAreaH() { return Theme.isSynthwave() ? 34 : 30; }
    private static int totalH() { return headerH() + GAP + sliderAreaH(); }

    private int value;
    private final int min;
    private final int max;
    private final IntFunction<String> formatter;
    private final JLabel valueLabel;
    private final SliderTrack track;
    private final List<ChangeListener> listeners = new ArrayList<>();

    public LabeledSlider(String label, int min, int max, int initial, IntFunction<String> formatter) {
        this.min = min;
        this.max = max;
        this.value = initial;
        this.formatter = formatter;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        // Header row
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, headerH()));

        JLabel nameLabel = Theme.paramLabel(label);
        nameLabel.setAlignmentY(0.5f);
        header.add(nameLabel);
        header.add(Box.createHorizontalGlue());
        valueLabel = Theme.valueLabel(formatter.apply(initial));
        valueLabel.setAlignmentY(0.5f);
        header.add(valueLabel);

        add(header);
        add(Box.createVerticalStrut(GAP));

        track = new SliderTrack();
        add(track);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, totalH());
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, totalH());
    }

    public int getValue() {
        return value;
    }

    public void setValue(int v) {
        v = Math.max(min, Math.min(max, v));
        if (v != value) {
            value = v;
            valueLabel.setText(formatter.apply(value));
            track.repaint();
            fireChangeListeners();
        }
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    private void fireChangeListeners() {
        ChangeEvent evt = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(evt);
    }

    private float getFraction() {
        if (max == min) return 0;
        return (float) (value - min) / (max - min);
    }

    private int valueFromX(int x, int trackWidth) {
        int padX = THUMB_SIZE / 2;
        float frac = (float) (x - padX) / (trackWidth - THUMB_SIZE);
        frac = Math.max(0, Math.min(1, frac));
        return min + Math.round(frac * (max - min));
    }

    /**
     * Custom painted slider track component.
     */
    private class SliderTrack extends JComponent {
        private boolean dragging;

        SliderTrack() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(200, sliderAreaH()));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, sliderAreaH()));
            setFocusable(true);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    dragging = true;
                    updateFromMouse(e.getX());
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragging = false;
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragging) updateFromMouse(e.getX());
                }
            });

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_DOWN) {
                        setValue(value - 1);
                    } else if (e.getKeyCode() == KeyEvent.VK_RIGHT || e.getKeyCode() == KeyEvent.VK_UP) {
                        setValue(value + 1);
                    }
                }
            });
        }

        private void updateFromMouse(int x) {
            setValue(valueFromX(x, getWidth()));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int padX = THUMB_SIZE / 2;
            int trackW = w - THUMB_SIZE;
            int trackY = (h - TRACK_H) / 2;
            float frac = getFraction();
            int fillW = (int) (frac * trackW);

            if (Theme.isSynthwave()) {
                // Rectangular track with pixel-art styling
                SynthwavePainter.fillPanel(g2, padX, trackY, trackW, TRACK_H,
                        Theme.BG_MUTED, Theme.SW_PURPLE);

                // Accent fill with glow
                if (fillW > 2) {
                    g2.setColor(Theme.ACCENT);
                    g2.fillRect(padX + 1, trackY + 1, fillW - 1, TRACK_H - 2);
                    SynthwavePainter.paintGlow(g2, padX, trackY, fillW, TRACK_H, Theme.ACCENT, 2);
                }

                // Square thumb with pixel corners
                int thumbX = padX + fillW - THUMB_SIZE / 2;
                int thumbY = (h - THUMB_SIZE) / 2;
                SynthwavePainter.fillPanel(g2, thumbX, thumbY, THUMB_SIZE, THUMB_SIZE,
                        Theme.SW_LAVENDER, Theme.SW_PURPLE);
                SynthwavePainter.paintBevel(g2, thumbX, thumbY, THUMB_SIZE, THUMB_SIZE, true);
            } else if (Theme.isPaper()) {
                // Paper: dithered track gradient (sparse left → dense right)
                g2.setColor(Theme.BG_MUTED);
                g2.fillRoundRect(padX, trackY, trackW, TRACK_H, TRACK_H, TRACK_H);

                if (fillW > 0) {
                    java.awt.Color dark = new java.awt.Color(0x55, 0x55, 0x55);
                    java.awt.Color mid  = new java.awt.Color(0x99, 0x99, 0x99);
                    Shape oldClip = g2.getClip();
                    g2.setClip(new java.awt.geom.RoundRectangle2D.Float(padX, trackY, fillW, TRACK_H, TRACK_H, TRACK_H));
                    for (int py = trackY; py < trackY + TRACK_H; py++) {
                        for (int px = padX; px < padX + fillW; px++) {
                            float intensity = (float)(px - padX) / Math.max(1, fillW + trackW);
                            int bx = ((int)((px - padX) / 1.5)) & 7;
                            int by = ((int)((py - trackY) / 1.5)) & 7;
                            float threshold = BAYER8[by][bx];
                            // 2-shade dither: transparent → mid → dark (left to right)
                            float shadePos = intensity * 2f;
                            if (shadePos < 1f) {
                                if (shadePos > threshold) {
                                    g2.setColor(mid);
                                    g2.fillRect(px, py, 1, 1);
                                }
                            } else {
                                g2.setColor((shadePos - 1f) > threshold ? dark : mid);
                                g2.fillRect(px, py, 1, 1);
                            }
                        }
                    }
                    g2.setClip(oldClip);
                }

                // Thumb matching envelope node style
                int paperThumb = 8;
                int thumbX = padX + fillW - paperThumb / 2;
                int thumbY = (h - paperThumb) / 2;
                g2.setColor(PaperMinimalistTokens.PAPER);
                g2.fillOval(thumbX, thumbY, paperThumb, paperThumb);
                g2.setStroke(new BasicStroke(PaperMinimalistTokens.BORDER_WIDTH));
                g2.setColor(PaperMinimalistTokens.INK);
                g2.drawOval(thumbX, thumbY, paperThumb - 1, paperThumb - 1);
            } else {
                // Standard rounded track
                g2.setColor(Theme.BG_MUTED);
                g2.fillRoundRect(padX, trackY, trackW, TRACK_H, TRACK_H, TRACK_H);

                if (fillW > 0) {
                    g2.setColor(Theme.ACCENT);
                    g2.fillRoundRect(padX, trackY, fillW, TRACK_H, TRACK_H, TRACK_H);
                }

                // Circular thumb
                int thumbX = padX + fillW - THUMB_SIZE / 2;
                int thumbY = (h - THUMB_SIZE) / 2;
                g2.setColor(Theme.SHADOW);
                g2.fillOval(thumbX + 1, thumbY + 1, THUMB_SIZE, THUMB_SIZE);
                g2.setColor(Theme.THUMB);
                g2.fillOval(thumbX, thumbY, THUMB_SIZE, THUMB_SIZE);
                g2.setColor(Theme.ACCENT);
                g2.drawOval(thumbX, thumbY, THUMB_SIZE - 1, THUMB_SIZE - 1);
            }

            // Focus ring
            if (isFocusOwner() && !Theme.isSynthwave() && !Theme.isPaper()) {
                g2.setColor(Theme.RING);
                g2.setStroke(new BasicStroke(2f));
                if (Theme.isSynthwave()) {
                    SynthwavePainter.strokeShape(g2, 1, 1, w - 2, h - 2, Theme.RING);
                } else {
                    g2.drawRoundRect(1, 1, w - 3, h - 3, h, h);
                }
            }

            g2.dispose();
        }
    }
}
