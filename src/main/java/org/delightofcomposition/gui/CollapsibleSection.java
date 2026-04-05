package org.delightofcomposition.gui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * Wraps a content component with a small centered pull-tab button for collapse/expand.
 * Content slides smoothly in/out via an animated clipping viewport.
 */
public class CollapsibleSection extends JPanel {

    private static final int TAB_W = 50;
    private static final int TAB_H = 16;

    private boolean expanded = true;
    private final JComponent content;
    private final JLabel chevron;
    private final boolean tabBelow;
    private final SlideViewport viewport;

    private int contentFullH;
    private int animH;
    private int animTarget;
    private boolean animating = false;
    private Timer animTimer;
    private Runnable onAnimTick;
    private Runnable onCollapse;

    public CollapsibleSection(JComponent content, boolean tabBelow) {
        this.content = content;
        this.tabBelow = tabBelow;
        setLayout(new BorderLayout(0, 0));
        setOpaque(false);

        // Clipping viewport wraps the content
        viewport = new SlideViewport();
        viewport.setLayout(new BorderLayout());
        viewport.setOpaque(false);
        viewport.add(content, BorderLayout.CENTER);

        // Pull-tab button
        JPanel tab = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
            }
        };
        tab.setOpaque(false);
        tab.setPreferredSize(new Dimension(TAB_W, TAB_H));
        tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tab.setLayout(new BorderLayout());

        chevron = new JLabel(tabBelow ? "\u25B2" : "\u25BC");
        chevron.setFont(Theme.FONT_SMALL.deriveFont(9f));
        chevron.setForeground(Theme.FG_MUTED);
        chevron.setHorizontalAlignment(SwingConstants.CENTER);
        tab.add(chevron, BorderLayout.CENTER);

        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggle();
            }
        });

        JPanel tabWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        tabWrapper.setOpaque(false);
        tabWrapper.add(tab);

        if (tabBelow) {
            add(viewport, BorderLayout.CENTER);
            add(tabWrapper, BorderLayout.SOUTH);
        } else {
            add(tabWrapper, BorderLayout.NORTH);
            add(viewport, BorderLayout.CENTER);
        }

        // Start with natural content height
        contentFullH = -1;
        animH = -1;
    }

    private int getContentHeight() {
        int h = content.getHeight();
        if (h <= 0) h = content.getPreferredSize().height;
        if (h <= 0) h = 200;
        return h;
    }

    /** Start in collapsed state (no animation). */
    public void setCollapsed() {
        expanded = false;
        animH = 0;
        contentFullH = -1;
        chevron.setText(tabBelow ? "\u25BC" : "\u25B2");
        viewport.revalidate();
        revalidate();
    }

    public void setAnimationCallback(Runnable callback) {
        this.onAnimTick = callback;
    }

    public void setCollapseCallback(Runnable callback) {
        this.onCollapse = callback;
    }

    public void toggle() {
        // Stop any in-progress animation
        if (animTimer != null) {
            animTimer.stop();
            animating = false;
        }

        expanded = !expanded;

        if (expanded) {
            // Use the last known full height for animation target
            contentFullH = getContentHeight();
            animTarget = contentFullH;
        } else {
            // Capture actual laid-out height before collapsing
            contentFullH = getContentHeight();
            animH = contentFullH; // start from current height
            animTarget = 0;
            if (onCollapse != null) onCollapse.run();
        }

        if (tabBelow) {
            chevron.setText(expanded ? "\u25B2" : "\u25BC");
        } else {
            chevron.setText(expanded ? "\u25BC" : "\u25B2");
        }

        animating = true;
        animTimer = new Timer(12, e -> {
            int diff = animTarget - animH;
            if (Math.abs(diff) <= 3) {
                animH = animTarget;
                animating = false;
                ((Timer) e.getSource()).stop();
            } else {
                animH += diff / 3;
            }
            viewport.revalidate();
            viewport.repaint();
            revalidate();
            repaint();
            if (getParent() != null) {
                getParent().revalidate();
                getParent().repaint();
            }
            if (onAnimTick != null) onAnimTick.run();
        });
        animTimer.start();
    }

    public boolean isExpanded() {
        return expanded;
    }

    /**
     * Inner panel that clips content to the animated height,
     * creating a slide-in/slide-out effect.
     * When fully expanded and not animating, acts as a transparent wrapper.
     */
    private class SlideViewport extends JPanel {

        @Override
        public Dimension getPreferredSize() {
            if (expanded && !animating) {
                return super.getPreferredSize();
            }
            if (animH <= 0 && !expanded) {
                return new Dimension(super.getPreferredSize().width, 0);
            }
            return new Dimension(super.getPreferredSize().width, Math.max(0, animH));
        }

        @Override
        public Dimension getMinimumSize() {
            if (expanded && !animating) {
                return super.getMinimumSize();
            }
            return new Dimension(0, 0);
        }

        @Override
        public Dimension getMaximumSize() {
            if (expanded && !animating) {
                return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height);
            }
            return new Dimension(Integer.MAX_VALUE, Math.max(0, animH));
        }

        @Override
        protected void paintChildren(Graphics g) {
            // Fully expanded: no clipping, normal painting
            if (expanded && !animating) {
                super.paintChildren(g);
                return;
            }
            // Fully collapsed: don't paint
            if (animH <= 0) return;

            Graphics2D g2 = (Graphics2D) g.create();
            if (!tabBelow && contentFullH > 0) {
                // Slide-down effect: translate so top clips away
                int offset = contentFullH - animH;
                g2.translate(0, -offset);
            }
            g2.clipRect(0, 0, getWidth(), animH);
            super.paintChildren(g2);
            g2.dispose();
        }
    }
}
