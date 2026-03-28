package org.delightofcomposition.gui;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

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
    private static final int SLIDER_AREA_H = 30;
    private static final int HEADER_H = 18;
    private static final int GAP = 4;

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
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, HEADER_H));

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
        return new Dimension(200, HEADER_H + GAP + SLIDER_AREA_H);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, HEADER_H + GAP + SLIDER_AREA_H);
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
            setPreferredSize(new Dimension(200, SLIDER_AREA_H));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, SLIDER_AREA_H));
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

            // Track background
            g2.setColor(Theme.BG_MUTED);
            g2.fillRoundRect(padX, trackY, trackW, TRACK_H, TRACK_H, TRACK_H);

            // Filled portion
            float frac = getFraction();
            int fillW = (int) (frac * trackW);
            if (fillW > 0) {
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(padX, trackY, fillW, TRACK_H, TRACK_H, TRACK_H);
            }

            // Thumb
            int thumbX = padX + (int) (frac * trackW) - THUMB_SIZE / 2;
            int thumbY = (h - THUMB_SIZE) / 2;

            // Shadow
            g2.setColor(Theme.SHADOW);
            g2.fillOval(thumbX + 1, thumbY + 1, THUMB_SIZE, THUMB_SIZE);

            // Thumb fill
            g2.setColor(Theme.THUMB);
            g2.fillOval(thumbX, thumbY, THUMB_SIZE, THUMB_SIZE);

            // Accent border
            g2.setColor(Theme.ACCENT);
            g2.drawOval(thumbX, thumbY, THUMB_SIZE - 1, THUMB_SIZE - 1);

            // Focus ring
            if (isFocusOwner()) {
                g2.setColor(Theme.RING);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, h, h);
            }

            g2.dispose();
        }
    }
}
