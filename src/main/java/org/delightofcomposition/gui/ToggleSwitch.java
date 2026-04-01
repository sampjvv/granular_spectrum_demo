package org.delightofcomposition.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * iOS-style pill toggle switch.
 * 48x26px track, 20px white circle thumb, animated slide over ~150ms.
 */
public class ToggleSwitch extends JComponent {

    private static final int TRACK_W = 48;
    private static final int TRACK_H = 26;
    private static final int THUMB_SIZE = 20;
    private static final int PAD = (TRACK_H - THUMB_SIZE) / 2;
    private static final int ANIM_FRAMES = 8;
    private static final int ANIM_DELAY = 20;

    private boolean selected;
    private float thumbPos; // 0.0 = off (left), 1.0 = on (right)
    private Timer animTimer;
    private final List<ChangeListener> listeners = new ArrayList<>();

    public ToggleSwitch(boolean initialState) {
        this.selected = initialState;
        this.thumbPos = initialState ? 1f : 0f;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setOpaque(false);
        setFocusable(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
                toggle();
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) {
                    toggle();
                }
            }
        });
    }

    private void toggle() {
        selected = !selected;
        animateThumb();
        fireChangeListeners();
    }

    private void animateThumb() {
        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
        }
        float target = selected ? 1f : 0f;
        float start = thumbPos;
        float step = (target - start) / ANIM_FRAMES;

        animTimer = new Timer(ANIM_DELAY, null);
        final int[] frame = {0};
        animTimer.addActionListener(e -> {
            frame[0]++;
            if (frame[0] >= ANIM_FRAMES) {
                thumbPos = target;
                animTimer.stop();
            } else {
                thumbPos = start + step * frame[0];
            }
            repaint();
        });
        animTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int yOff = (getHeight() - TRACK_H) / 2;

        if (Theme.isSynthwave()) {
            // Track with LG corners to match the thumb's proportional roundedness
            Color trackColor = interpolateColor(Theme.SW_BG_DEEP, Theme.SW_GREEN, thumbPos);
            java.awt.Polygon trackOuter = SynthwavePainter.pixelCornerShape(0, yOff, TRACK_W, TRACK_H, SynthwavePainter.CornerSize.LG);
            g2.setColor(Theme.SW_PURPLE);
            g2.fillPolygon(trackOuter);
            java.awt.Polygon trackInner = SynthwavePainter.pixelCornerShapeInset(0, yOff, TRACK_W, TRACK_H, SynthwavePainter.CornerSize.LG, SynthwavePainter.BORDER_WIDTH);
            g2.setColor(trackColor);
            g2.fillPolygon(trackInner);

            // Thumb with matching corners
            int thumbRange = TRACK_W - THUMB_SIZE - PAD * 2;
            int thumbX = PAD + (int) (thumbPos * thumbRange);
            int thumbY = yOff + PAD;
            SynthwavePainter.fillShape(g2, thumbX, thumbY, THUMB_SIZE, THUMB_SIZE, Theme.SW_LAVENDER);
            SynthwavePainter.paintBevel(g2, thumbX, thumbY, THUMB_SIZE, THUMB_SIZE, true);

            // Green LED glow when on
            if (thumbPos > 0.5f) {
                SynthwavePainter.paintGlow(g2, thumbX, thumbY, THUMB_SIZE, THUMB_SIZE,
                        Theme.SW_GREEN, 3);
            }
        } else {
            // iOS-style pill
            Color trackColor = interpolateColor(Theme.ZINC_700, Theme.SUCCESS, thumbPos);
            g2.setColor(trackColor);
            g2.fillRoundRect(0, yOff, TRACK_W, TRACK_H, TRACK_H, TRACK_H);

            int thumbRange = TRACK_W - THUMB_SIZE - PAD * 2;
            int thumbX = PAD + (int) (thumbPos * thumbRange);
            int thumbY = yOff + PAD;
            g2.setColor(Theme.THUMB);
            g2.fillOval(thumbX, thumbY, THUMB_SIZE, THUMB_SIZE);

            if (isFocusOwner()) {
                g2.setColor(Theme.RING);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(0, yOff, TRACK_W - 1, TRACK_H - 1, TRACK_H, TRACK_H);
            }
        }

        g2.dispose();
    }

    private static Color interpolateColor(Color a, Color b, float t) {
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(TRACK_W, Theme.TOUCH_TARGET);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(TRACK_W, Theme.TOUCH_TARGET);
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean sel) {
        if (this.selected != sel) {
            this.selected = sel;
            this.thumbPos = sel ? 1f : 0f;
            repaint();
        }
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    private void fireChangeListeners() {
        ChangeEvent evt = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(evt);
    }
}
