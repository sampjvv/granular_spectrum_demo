package org.delightofcomposition.gui;

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
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Pill-shaped segmented control (like iOS UISegmentedControl).
 * Full-width pill container with equal-width segments.
 * Active segment has animated sliding accent fill.
 */
public class SegmentedControl extends JComponent {

    private static final int HEIGHT = 34;
    private static final int INSET = 3;
    private static final int ANIM_FRAMES = 8;
    private static final int ANIM_DELAY = 15;

    private final String[] labels;
    private int selectedIndex;
    private float animPos; // animated position (index-based, fractional during animation)
    private Timer animTimer;
    private final List<ChangeListener> listeners = new ArrayList<>();

    public SegmentedControl(String[] labels, int initialIndex) {
        this.labels = labels;
        this.selectedIndex = initialIndex;
        this.animPos = initialIndex;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.FONT_SMALL);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int segW = (getWidth() - INSET * 2) / labels.length;
                int clickedIndex = (e.getX() - INSET) / segW;
                clickedIndex = Math.max(0, Math.min(labels.length - 1, clickedIndex));
                if (clickedIndex != selectedIndex) {
                    setSelectedIndexAnimated(clickedIndex);
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();
        int radius = h;

        // Container pill
        g2.setColor(Theme.BG_MUTED);
        g2.fillRoundRect(0, 0, w, h, radius, radius);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);

        // Segment dimensions
        int innerW = w - INSET * 2;
        int segW = innerW / labels.length;
        int segH = h - INSET * 2;
        int segRadius = segH;

        // Active segment pill (animated)
        int activeX = INSET + (int) (animPos * segW);
        g2.setColor(Theme.ACCENT);
        g2.fillRoundRect(activeX, INSET, segW, segH, segRadius, segRadius);

        // Labels
        FontMetrics fm = g2.getFontMetrics(getFont());
        g2.setFont(getFont());
        for (int i = 0; i < labels.length; i++) {
            int segX = INSET + i * segW;
            boolean active = i == selectedIndex;
            g2.setColor(active ? Color.WHITE : Theme.FG_MUTED);
            int tx = segX + (segW - fm.stringWidth(labels[i])) / 2;
            int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(labels[i], tx, ty);
        }

        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(100, HEIGHT);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= labels.length) return;
        if (index == selectedIndex) return;
        selectedIndex = index;
        // Jump immediately (no animation) for programmatic changes
        animPos = index;
        repaint();
        fireChangeListeners();
    }

    /** Set index with animated slide (used for mouse clicks). */
    private void setSelectedIndexAnimated(int index) {
        if (index < 0 || index >= labels.length || index == selectedIndex) return;
        selectedIndex = index;
        animateToIndex(index);
        fireChangeListeners();
    }

    private void animateToIndex(int target) {
        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
        }
        float start = animPos;
        float step = (target - start) / ANIM_FRAMES;

        animTimer = new Timer(ANIM_DELAY, null);
        final int[] frame = {0};
        animTimer.addActionListener(e -> {
            frame[0]++;
            if (frame[0] >= ANIM_FRAMES) {
                animPos = target;
                animTimer.stop();
            } else {
                animPos = start + step * frame[0];
            }
            repaint();
        });
        animTimer.start();
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    private void fireChangeListeners() {
        ChangeEvent evt = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(evt);
    }
}
