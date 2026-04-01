package org.delightofcomposition.gui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicToolTipUI;

/**
 * shadcn/ui tooltip: dark rounded pill with a small rotated-square arrow,
 * matching the Radix TooltipPrimitive.Arrow style (size-2.5, rotate-45,
 * rounded-[2px], same fill as body).
 */
public class RadixToolTipUI extends BasicToolTipUI {

    private static final int PAD_Y = 6;
    private static final int PAD_X = 12;
    private static final int ARC = 6;             // rounded-md
    private static final int ARROW_SIZE = 10;      // size-2.5 ≈ 10px
    private static final int ARROW_RADIUS = 2;     // rounded-[2px]
    private static final int ARROW_OVERLAP = 4;    // how far arrow tucks into body

    private static final RadixToolTipUI SHARED = new RadixToolTipUI();

    public static ComponentUI createUI(JComponent c) {
        return SHARED;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = c.getWidth();
        int bodyH = c.getHeight() - (ARROW_SIZE / 2 - ARROW_OVERLAP);

        // Draw rotated-square arrow first (behind body)
        int arrowCx = w / 2;
        int arrowCy = bodyH - ARROW_OVERLAP;
        Graphics2D ag = (Graphics2D) g2.create();
        ag.setColor(Theme.ZINC_950);
        ag.translate(arrowCx, arrowCy);
        ag.rotate(Math.PI / 4);
        ag.fillRoundRect(-ARROW_SIZE / 2, -ARROW_SIZE / 2, ARROW_SIZE, ARROW_SIZE,
                ARROW_RADIUS, ARROW_RADIUS);
        ag.dispose();

        // Draw body on top (covers upper half of arrow)
        if (Theme.isSynthwave()) {
            SynthwavePainter.fillPanel(g2, 0, 0, w, bodyH, Theme.ZINC_950, Theme.SW_CYAN);
        } else {
            g2.setColor(Theme.ZINC_950);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, bodyH, ARC, ARC));
        }

        // Text
        String text = ((javax.swing.JToolTip) c).getTipText();
        if (text != null) {
            g2.setFont(Theme.FONT_SMALL);
            g2.setColor(Theme.ZINC_100);
            FontMetrics fm = g2.getFontMetrics();
            int tx = PAD_X;
            int ty = PAD_Y + fm.getAscent();
            g2.drawString(text, tx, ty);
        }

        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        String text = ((javax.swing.JToolTip) c).getTipText();
        if (text == null || text.isEmpty()) {
            return new Dimension(0, 0);
        }
        FontMetrics fm = c.getFontMetrics(Theme.FONT_SMALL);
        int textW = fm.stringWidth(text);
        int textH = fm.getHeight();
        int arrowExtra = ARROW_SIZE / 2 - ARROW_OVERLAP;
        return new Dimension(
                textW + PAD_X * 2,
                textH + PAD_Y * 2 + arrowExtra);
    }
}
