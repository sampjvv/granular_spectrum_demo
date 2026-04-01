package org.delightofcomposition.gui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

/**
 * CRT scanline overlay for the synthwave theme.
 * Paints subtle horizontal lines and a vignette effect over the entire window.
 * Fully transparent to mouse events.
 */
public class SynthwaveScanlinePane extends JComponent {

    private static final int SCANLINE_SPACING = 3;
    private static final float SCANLINE_ALPHA = 0.04f;
    private static final float VIGNETTE_ALPHA = 0.25f;

    private TexturePaint scanlinePaint;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private BufferedImage vignetteCache;

    public SynthwaveScanlinePane() {
        setOpaque(false);
        buildScanlineTile();
    }

    private void buildScanlineTile() {
        int tileH = SCANLINE_SPACING;
        BufferedImage tile = new BufferedImage(1, tileH, BufferedImage.TYPE_INT_ARGB);
        int scanlineColor = new Color(0, 0, 0, (int) (255 * SCANLINE_ALPHA)).getRGB();
        int transparent = new Color(0, 0, 0, 0).getRGB();
        tile.setRGB(0, 0, scanlineColor);
        for (int y = 1; y < tileH; y++) {
            tile.setRGB(0, y, transparent);
        }
        scanlinePaint = new TexturePaint(tile, new Rectangle(0, 0, 1, tileH));
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        Graphics2D g2 = (Graphics2D) g.create();

        // Scanlines
        g2.setPaint(scanlinePaint);
        g2.fillRect(0, 0, w, h);

        // Vignette (cached)
        if (vignetteCache == null || cachedWidth != w || cachedHeight != h) {
            cachedWidth = w;
            cachedHeight = h;
            vignetteCache = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D vg = vignetteCache.createGraphics();
            Point2D center = new Point2D.Float(w / 2f, h / 2f);
            float radius = (float) Math.sqrt(w * w + h * h) / 2f;
            float[] dist = {0.4f, 1.0f};
            Color[] colors = {
                new Color(0, 0, 0, 0),
                new Color(0, 0, 0, (int) (255 * VIGNETTE_ALPHA))
            };
            RadialGradientPaint vignette = new RadialGradientPaint(center, radius, dist, colors);
            vg.setPaint(vignette);
            vg.fillRect(0, 0, w, h);
            vg.dispose();
        }
        g2.drawImage(vignetteCache, 0, 0, null);

        g2.dispose();
    }

    @Override
    public boolean contains(int x, int y) {
        // Fully transparent to mouse events
        return false;
    }
}
