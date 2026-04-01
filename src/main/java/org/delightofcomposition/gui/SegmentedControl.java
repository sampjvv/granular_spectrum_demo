package org.delightofcomposition.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
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
 * Pill-shaped segmented control (like iOS UISegmentedControl).
 * Full-width pill container with equal-width segments.
 * Active segment has animated sliding accent fill.
 */
public class SegmentedControl extends JComponent {

    private static int height() { return Theme.isSynthwave() ? 40 : 34; }
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
        Theme.tagFont(this, "small");
        setFocusable(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
                int segW = (getWidth() - INSET * 2) / labels.length;
                int clickedIndex = (e.getX() - INSET) / segW;
                clickedIndex = Math.max(0, Math.min(labels.length - 1, clickedIndex));
                if (clickedIndex != selectedIndex) {
                    setSelectedIndexAnimated(clickedIndex);
                }
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_LEFT && selectedIndex > 0) {
                    setSelectedIndexAnimated(selectedIndex - 1);
                } else if (e.getKeyCode() == KeyEvent.VK_RIGHT && selectedIndex < labels.length - 1) {
                    setSelectedIndexAnimated(selectedIndex + 1);
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

        if (Theme.isSynthwave()) {
            // Pixel-corner container
            SynthwavePainter.fillPanel(g2, 0, 0, w, h, Theme.BG_MUTED, Theme.SW_PURPLE);

            // Segment dimensions
            int innerW = w - INSET * 2;
            int segW = innerW / labels.length;
            int segH = h - INSET * 2;

            // Clip active fill to container's inner polygon so corners align
            SynthwavePainter.CornerSize sz = SynthwavePainter.chooseSizeForDimensions(w, h);
            java.awt.Polygon innerClip = SynthwavePainter.pixelCornerShapeInset(
                    0, 0, w, h, sz, SynthwavePainter.BORDER_WIDTH);
            java.awt.Shape origClip = g2.getClip();
            g2.clip(innerClip);

            // Active segment — gradient fill with strong edge shading
            int activeX = INSET + (int) (animPos * segW);
            // Extend last segment to fill any rounding gap
            int activeW = (Math.round(animPos) >= labels.length - 1)
                    ? (INSET + innerW - activeX) : segW;
            Color accentBright = new Color(
                    Math.min(255, Theme.ACCENT.getRed() + 40),
                    Math.min(255, Theme.ACCENT.getGreen() + 30),
                    Math.min(255, Theme.ACCENT.getBlue() + 30));
            int arc = 8;
            java.awt.GradientPaint gp = new java.awt.GradientPaint(
                    0, INSET, accentBright, 0, INSET + segH, Theme.ACCENT);
            g2.setPaint(gp);
            g2.fillRoundRect(activeX, INSET, activeW, segH, arc, arc);

            // Edge shading — clip to top-left or bottom-right halves
            // so highlight and shadow don't bleed onto the wrong edges
            java.awt.Composite origComp = g2.getComposite();

            // Top + left highlight: clip to top-left diagonal half
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));
            g2.setColor(Color.WHITE);
            for (int d = 0; d < 3; d++) {
                java.awt.Polygon hlClip = new java.awt.Polygon(
                    new int[] { activeX, activeX + activeW, activeX },
                    new int[] { INSET, INSET, INSET + segH },
                    3);
                g2.setClip(innerClip);
                g2.clip(hlClip);
                g2.drawRoundRect(activeX + d, INSET + d, activeW - 2 * d - 1, segH - 2 * d - 1, arc - d, arc - d);
            }

            // Bottom + right shadow: clip to bottom-right diagonal half
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.4f));
            g2.setColor(Color.BLACK);
            for (int d = 0; d < 3; d++) {
                java.awt.Polygon shClip = new java.awt.Polygon(
                    new int[] { activeX + activeW, activeX + activeW, activeX },
                    new int[] { INSET, INSET + segH, INSET + segH },
                    3);
                g2.setClip(innerClip);
                g2.clip(shClip);
                g2.drawRoundRect(activeX + d, INSET + d, activeW - 2 * d - 1, segH - 2 * d - 1, arc - d, arc - d);
            }

            g2.setClip(origClip);
            g2.setComposite(origComp);
            g2.setClip(origClip);

            // Cyan bottom accent line with glow (outside clip)
            g2.setColor(Theme.SW_CYAN);
            g2.fillRect(activeX + 2, h - INSET - 2, activeW - 4, 2);
            SynthwavePainter.paintGlow(g2, activeX + 2, h - INSET - 2, activeW - 4, 2, Theme.SW_CYAN, 3);

            // Labels
            FontMetrics fm = g2.getFontMetrics(getFont());
            g2.setFont(getFont());
            for (int i = 0; i < labels.length; i++) {
                int segX = INSET + i * segW;
                boolean active = i == selectedIndex;
                g2.setColor(active ? Theme.BG : Theme.FG_MUTED);
                int tx = segX + (segW - fm.stringWidth(labels[i])) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(labels[i], tx, ty);
            }
        } else {
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
                g2.setColor(active ? Theme.THUMB : Theme.FG_MUTED);
                int tx = segX + (segW - fm.stringWidth(labels[i])) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(labels[i], tx, ty);
            }

            // Focus ring
            if (isFocusOwner() && !Theme.isSynthwave()) {
                g2.setColor(Theme.RING);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
            }
        }

        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, height());
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, height());
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(100, height());
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
