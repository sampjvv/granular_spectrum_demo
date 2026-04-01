package org.delightofcomposition.gui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.GeneralPath;

/**
 * Static painting utilities for the synthwave theme.
 * Provides pixel-art corner shapes, glow effects, and bevel styling.
 *
 * All public methods check {@link Theme#isSynthwave()} internally —
 * callers don't need to guard; non-synthwave themes get standard rendering.
 */
public class SynthwavePainter {

    /** Border thickness for synthwave panels/components (in pixels). */
    public static final int BORDER_WIDTH = 3;

    /** Corner size presets matching the CSS clip-path definitions. */
    public enum CornerSize {
        /** ~3px staircase — for small controls, buttons */
        SM,
        /** ~5px staircase — for panels, cards */
        MD,
        /** ~9px staircase — for large containers */
        LG
    }

    // ── Pixel-corner polygon generation ──

    /**
     * Create a pixel-art corner polygon for the given bounds and corner size.
     * The polygon traces a Bresenham-style staircase at each corner,
     * matching the CSS clip-paths from synthwave tokens.css.
     */
    public static Polygon pixelCornerShape(int x, int y, int w, int h, CornerSize size) {
        switch (size) {
            case SM: return pixelCornerSM(x, y, w, h);
            case MD: return pixelCornerMD(x, y, w, h);
            case LG: return pixelCornerLG(x, y, w, h);
            default: return pixelCornerMD(x, y, w, h);
        }
    }

    /**
     * Create an inset pixel-corner polygon (for border effect: fill outer with
     * border color, then fill inner with bg color).
     * @param inset pixels to inset (typically 1 or 2)
     */
    public static Polygon pixelCornerShapeInset(int x, int y, int w, int h,
                                                  CornerSize size, int inset) {
        // For the inset shape, reduce the staircase proportionally
        switch (size) {
            case SM: return pixelCornerSM_inner(x + inset, y + inset, w - 2 * inset, h - 2 * inset, inset);
            case MD: return pixelCornerMD_inner(x + inset, y + inset, w - 2 * inset, h - 2 * inset, inset);
            case LG: return pixelCornerLG_inner(x + inset, y + inset, w - 2 * inset, h - 2 * inset, inset);
            default: return pixelCornerMD_inner(x + inset, y + inset, w - 2 * inset, h - 2 * inset, inset);
        }
    }

    // SM corner: 3px flat, 2px step, 2px side
    // Matches --sw-corner-sm from tokens.css
    private static Polygon pixelCornerSM(int x, int y, int w, int h) {
        int r = x + w;
        int b = y + h;
        return new Polygon(
            new int[] {
                // top-left corner
                x, x + 1, x + 1, x + 3, x + 3,
                // top edge → top-right corner
                r - 3, r - 3, r - 1, r - 1, r,
                // right edge → bottom-right corner
                r, r - 1, r - 1, r - 3, r - 3,
                // bottom edge → bottom-left corner
                x + 3, x + 3, x + 1, x + 1, x
            },
            new int[] {
                // top-left corner
                y + 3, y + 3, y + 1, y + 1, y,
                // top edge → top-right corner
                y, y + 1, y + 1, y + 3, y + 3,
                // right edge → bottom-right corner
                b - 3, b - 3, b - 1, b - 1, b,
                // bottom edge → bottom-left corner
                b, b - 1, b - 1, b - 3, b - 3
            },
            20
        );
    }

    // SM inner (for 1px or 2px inset)
    private static Polygon pixelCornerSM_inner(int x, int y, int w, int h, int inset) {
        int r = x + w;
        int b = y + h;
        if (inset >= 2) {
            // Heavily simplified — just a rectangle with 1px corner notches
            return new Polygon(
                new int[] { x, x + 1, x + 1, r - 1, r - 1, r, r, r - 1, r - 1, x + 1, x + 1, x },
                new int[] { y + 1, y + 1, y, y, y + 1, y + 1, b - 1, b - 1, b, b, b - 1, b - 1 },
                12
            );
        }
        // 1px inset
        return new Polygon(
            new int[] { x, x + 1, x + 1, r - 1, r - 1, r, r, r - 1, r - 1, x + 1, x + 1, x },
            new int[] { y + 2, y + 2, y, y, y + 2, y + 2, b - 2, b - 2, b, b, b - 2, b - 2 },
            12
        );
    }

    // MD corner: 5px flat, 2px step, 1px, 1px, 2px side
    // Matches --sw-corner-md from tokens.css
    private static Polygon pixelCornerMD(int x, int y, int w, int h) {
        int r = x + w;
        int b = y + h;
        return new Polygon(
            new int[] {
                // top-left corner
                x, x + 1, x + 1, x + 2, x + 2, x + 3, x + 3, x + 5, x + 5,
                // top edge → top-right corner
                r - 5, r - 5, r - 3, r - 3, r - 2, r - 2, r - 1, r - 1, r,
                // right edge → bottom-right corner
                r, r - 1, r - 1, r - 2, r - 2, r - 3, r - 3, r - 5, r - 5,
                // bottom edge → bottom-left corner
                x + 5, x + 5, x + 3, x + 3, x + 2, x + 2, x + 1, x + 1, x
            },
            new int[] {
                // top-left corner
                y + 5, y + 5, y + 3, y + 3, y + 2, y + 2, y + 1, y + 1, y,
                // top edge → top-right corner
                y, y + 1, y + 1, y + 2, y + 2, y + 3, y + 3, y + 5, y + 5,
                // right edge → bottom-right corner
                b - 5, b - 5, b - 3, b - 3, b - 2, b - 2, b - 1, b - 1, b,
                // bottom edge → bottom-left corner
                b, b - 1, b - 1, b - 2, b - 2, b - 3, b - 3, b - 5, b - 5
            },
            36
        );
    }

    // MD inner for 1px inset
    private static Polygon pixelCornerMD_inner(int x, int y, int w, int h, int inset) {
        int r = x + w;
        int b = y + h;
        if (inset >= 2) {
            // Simplified: rectangle with small 2-step corner
            return new Polygon(
                new int[] {
                    x, x + 1, x + 1, x + 2, x + 2,
                    r - 2, r - 2, r - 1, r - 1, r,
                    r, r - 1, r - 1, r - 2, r - 2,
                    x + 2, x + 2, x + 1, x + 1, x
                },
                new int[] {
                    y + 3, y + 3, y + 1, y + 1, y,
                    y, y + 1, y + 1, y + 3, y + 3,
                    b - 3, b - 3, b - 1, b - 1, b,
                    b, b - 1, b - 1, b - 3, b - 3
                },
                20
            );
        }
        // 1px inset
        return new Polygon(
            new int[] {
                x, x + 1, x + 1, x + 2, x + 2, x + 3, x + 3,
                r - 3, r - 3, r - 2, r - 2, r - 1, r - 1, r,
                r, r - 1, r - 1, r - 2, r - 2, r - 3, r - 3,
                x + 3, x + 3, x + 2, x + 2, x + 1, x + 1, x
            },
            new int[] {
                y + 4, y + 4, y + 2, y + 2, y + 1, y + 1, y,
                y, y + 1, y + 1, y + 2, y + 2, y + 4, y + 4,
                b - 4, b - 4, b - 2, b - 2, b - 1, b - 1, b,
                b, b - 1, b - 1, b - 2, b - 2, b - 4, b - 4
            },
            28
        );
    }

    // LG corner: 9px flat, multiple steps
    // Matches --sw-corner-lg from tokens.css
    private static Polygon pixelCornerLG(int x, int y, int w, int h) {
        int r = x + w;
        int b = y + h;
        return new Polygon(
            new int[] {
                // top-left corner
                x, x + 1, x + 1, x + 2, x + 2, x + 3, x + 3, x + 5, x + 5, x + 7, x + 7, x + 9, x + 9,
                // top edge → top-right corner
                r - 9, r - 9, r - 7, r - 7, r - 5, r - 5, r - 3, r - 3, r - 2, r - 2, r - 1, r - 1, r,
                // right edge → bottom-right corner
                r, r - 1, r - 1, r - 2, r - 2, r - 3, r - 3, r - 5, r - 5, r - 7, r - 7, r - 9, r - 9,
                // bottom edge → bottom-left corner
                x + 9, x + 9, x + 7, x + 7, x + 5, x + 5, x + 3, x + 3, x + 2, x + 2, x + 1, x + 1, x
            },
            new int[] {
                // top-left corner
                y + 8, y + 8, y + 5, y + 5, y + 4, y + 4, y + 3, y + 3, y + 2, y + 2, y + 1, y + 1, y,
                // top edge → top-right corner
                y, y + 1, y + 1, y + 2, y + 2, y + 3, y + 3, y + 4, y + 4, y + 5, y + 5, y + 8, y + 8,
                // right edge → bottom-right corner
                b - 8, b - 8, b - 5, b - 5, b - 4, b - 4, b - 3, b - 3, b - 2, b - 2, b - 1, b - 1, b,
                // bottom edge → bottom-left corner
                b, b - 1, b - 1, b - 2, b - 2, b - 3, b - 3, b - 4, b - 4, b - 5, b - 5, b - 8, b - 8
            },
            52
        );
    }

    // LG inner for inset
    private static Polygon pixelCornerLG_inner(int x, int y, int w, int h, int inset) {
        int r = x + w;
        int b = y + h;
        if (inset >= 2) {
            return new Polygon(
                new int[] {
                    x, x + 1, x + 1, x + 2, x + 2, x + 3, x + 3, x + 5, x + 5, x + 7, x + 7,
                    r - 7, r - 7, r - 5, r - 5, r - 3, r - 3, r - 2, r - 2, r - 1, r - 1, r,
                    r, r - 1, r - 1, r - 2, r - 2, r - 3, r - 3, r - 5, r - 5, r - 7, r - 7,
                    x + 7, x + 7, x + 5, x + 5, x + 3, x + 3, x + 2, x + 2, x + 1, x + 1, x
                },
                new int[] {
                    y + 6, y + 6, y + 3, y + 3, y + 2, y + 2, y + 1, y + 1, y, y, y,
                    y, y, y, y + 1, y + 1, y + 2, y + 2, y + 3, y + 3, y + 6, y + 6,
                    b - 6, b - 6, b - 3, b - 3, b - 2, b - 2, b - 1, b - 1, b, b, b,
                    b, b, b, b - 1, b - 1, b - 2, b - 2, b - 3, b - 3, b - 6, b - 6
                },
                44
            );
        }
        // 1px inset — keep most of the staircase
        return pixelCornerLG(x, y, w, h); // close enough at 1px
    }

    // ── Pixel-art circle (for knobs, LEDs) ──

    /**
     * Create a pixel-art circle polygon using percentage-based staircase.
     * Centered at (cx, cy) with the given diameter.
     */
    public static Polygon pixelCircle(int cx, int cy, int diameter) {
        int r = diameter / 2;
        // 8-step staircase approximation
        double[] fracs = {0.30, 0.20, 0.12, 0.06, 0.02};
        int n = fracs.length;

        // Build top-right quadrant, then mirror
        int totalPoints = 4 + 4 * (2 * n);
        int[] xp = new int[totalPoints];
        int[] yp = new int[totalPoints];
        int idx = 0;

        // Top flat
        int flatHalf = (int)(0.30 * r);
        xp[idx] = cx - flatHalf; yp[idx] = cy - r; idx++;
        xp[idx] = cx + flatHalf; yp[idx] = cy - r; idx++;

        // Top-right staircase
        for (int i = n - 1; i >= 0; i--) {
            int px = (int)((1.0 - fracs[i]) * r);
            int py = (int)((1.0 - fracs[n - 1 - i]) * r);
            xp[idx] = cx + px; yp[idx] = cy - py; idx++;
            if (i > 0) {
                xp[idx] = cx + px; yp[idx] = cy - (int)((1.0 - fracs[n - i]) * r); idx++;
            }
        }

        // Right flat
        xp[idx] = cx + r; yp[idx] = cy - flatHalf; idx++;
        xp[idx] = cx + r; yp[idx] = cy + flatHalf; idx++;

        // Bottom-right staircase
        for (int i = 0; i < n; i++) {
            int px = (int)((1.0 - fracs[i]) * r);
            int py = (int)((1.0 - fracs[n - 1 - i]) * r);
            xp[idx] = cx + px; yp[idx] = cy + py; idx++;
            if (i < n - 1) {
                xp[idx] = cx + (int)((1.0 - fracs[i + 1]) * r); yp[idx] = cy + py; idx++;
            }
        }

        // Bottom flat
        xp[idx] = cx + flatHalf; yp[idx] = cy + r; idx++;
        xp[idx] = cx - flatHalf; yp[idx] = cy + r; idx++;

        // Bottom-left staircase
        for (int i = n - 1; i >= 0; i--) {
            int px = (int)((1.0 - fracs[i]) * r);
            int py = (int)((1.0 - fracs[n - 1 - i]) * r);
            xp[idx] = cx - px; yp[idx] = cy + py; idx++;
            if (i > 0) {
                xp[idx] = cx - px; yp[idx] = cy + (int)((1.0 - fracs[n - i]) * r); idx++;
            }
        }

        // Left flat
        xp[idx] = cx - r; yp[idx] = cy + flatHalf; idx++;
        xp[idx] = cx - r; yp[idx] = cy - flatHalf; idx++;

        // Top-left staircase
        for (int i = 0; i < n; i++) {
            int px = (int)((1.0 - fracs[i]) * r);
            int py = (int)((1.0 - fracs[n - 1 - i]) * r);
            xp[idx] = cx - px; yp[idx] = cy - py; idx++;
            if (i < n - 1) {
                xp[idx] = cx - (int)((1.0 - fracs[i + 1]) * r); yp[idx] = cy - py; idx++;
            }
        }

        return new Polygon(xp, yp, idx);
    }

    // ── High-level painting methods ──

    /**
     * Fill a panel/card shape. If synthwave: pixel corners + border.
     * Otherwise: standard fillRoundRect.
     */
    public static void fillPanel(Graphics2D g2, int x, int y, int w, int h,
                                   Color bg, Color border) {
        if (Theme.isSynthwave()) {
            CornerSize size = chooseSizeForDimensions(w, h);
            Polygon outer = pixelCornerShape(x, y, w, h, size);
            g2.setColor(border);
            g2.fillPolygon(outer);
            Polygon inner = pixelCornerShapeInset(x, y, w, h, size, 3);
            g2.setColor(bg);
            g2.fillPolygon(inner);
        } else {
            g2.setColor(bg);
            g2.fillRoundRect(x, y, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
            g2.setColor(border);
            g2.drawRoundRect(x, y, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
        }
    }

    /**
     * Fill a shape with only background (no border stroke).
     */
    public static void fillShape(Graphics2D g2, int x, int y, int w, int h, Color bg) {
        if (Theme.isSynthwave()) {
            CornerSize size = chooseSizeForDimensions(w, h);
            Polygon shape = pixelCornerShape(x, y, w, h, size);
            g2.setColor(bg);
            g2.fillPolygon(shape);
        } else {
            g2.setColor(bg);
            g2.fillRoundRect(x, y, w, h, Theme.RADIUS, Theme.RADIUS);
        }
    }

    /**
     * Stroke a shape outline only.
     */
    public static void strokeShape(Graphics2D g2, int x, int y, int w, int h, Color color) {
        if (Theme.isSynthwave()) {
            CornerSize size = chooseSizeForDimensions(w, h);
            Polygon shape = pixelCornerShape(x, y, w, h, size);
            g2.setColor(color);
            g2.setStroke(new java.awt.BasicStroke(BORDER_WIDTH));
            g2.drawPolygon(shape);
        } else {
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
        }
    }

    /**
     * Paint a glow effect around a shape.
     * Draws the shape multiple times at increasing sizes with decreasing alpha.
     */
    public static void paintGlow(Graphics2D g2, int x, int y, int w, int h,
                                   Color glowColor, int spread) {
        Composite origComposite = g2.getComposite();
        for (int i = spread; i >= 1; i--) {
            float alpha = 0.08f * (1.0f - (float) i / (spread + 1));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(glowColor);
            int expand = i * 2;
            if (Theme.isSynthwave()) {
                CornerSize size = chooseSizeForDimensions(w + expand, h + expand);
                Polygon shape = pixelCornerShape(x - i, y - i, w + expand, h + expand, size);
                g2.fillPolygon(shape);
            } else {
                g2.fillRoundRect(x - i, y - i, w + expand, h + expand,
                        Theme.RADIUS + i, Theme.RADIUS + i);
            }
        }
        g2.setComposite(origComposite);
    }

    /**
     * Paint a glow around a specific color area (e.g., accent button).
     * Higher intensity than the general glow.
     */
    public static void paintAccentGlow(Graphics2D g2, int x, int y, int w, int h,
                                         Color glowColor) {
        paintGlow(g2, x, y, w, h, glowColor, 4);
    }

    /**
     * Paint bevel effect — raised or inset 3D look.
     * Top-left edge gets a highlight, bottom-right gets a shadow.
     */
    public static void paintBevel(Graphics2D g2, int x, int y, int w, int h, boolean raised) {
        Composite origComposite = g2.getComposite();
        java.awt.Shape origClip = g2.getClip();

        // Clip to the inner polygon so bevel lines don't leak past pixel corners
        CornerSize size = chooseSizeForDimensions(w, h);
        Polygon innerClip = pixelCornerShapeInset(x, y, w, h, size, BORDER_WIDTH);
        g2.setClip(innerClip);

        // Highlight (top-left) — 2px wide for visibility with thicker borders
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                raised ? 0.18f : 0.06f));
        g2.setColor(raised ? Color.WHITE : Color.BLACK);
        for (int d = 1; d <= 2; d++) {
            g2.drawLine(x + d, y + d, x + w - d - 1, y + d); // top
            g2.drawLine(x + d, y + d, x + d, y + h - d - 1); // left
        }

        // Shadow (bottom-right)
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                raised ? 0.28f : 0.18f));
        g2.setColor(raised ? Color.BLACK : Color.WHITE);
        for (int d = 1; d <= 2; d++) {
            g2.drawLine(x + d, y + h - d - 1, x + w - d - 1, y + h - d - 1); // bottom
            g2.drawLine(x + w - d - 1, y + d, x + w - d - 1, y + h - d - 1); // right
        }

        g2.setClip(origClip);
        g2.setComposite(origComposite);
    }

    // ── Button shading ──

    private static final int SHADE_ARC = 8;

    /**
     * Paint a button with gradient fill, rounded corners, and diagonal-clipped
     * highlight/shadow edges (matching the SegmentedControl active tab style).
     *
     * @param border if non-null, paints a border ring around the button
     */
    public static void paintShadedButton(Graphics2D g2, int x, int y, int w, int h,
                                           Color fill, Color border) {
        // Gradient: brighter at top
        Color bright = new Color(
                Math.min(255, fill.getRed() + 35),
                Math.min(255, fill.getGreen() + 25),
                Math.min(255, fill.getBlue() + 25));
        java.awt.GradientPaint gp = new java.awt.GradientPaint(0, y, bright, 0, y + h, fill);
        g2.setPaint(gp);
        g2.fillRoundRect(x, y, w, h, SHADE_ARC, SHADE_ARC);

        // Border ring
        if (border != null) {
            g2.setColor(border);
            g2.setStroke(new java.awt.BasicStroke(2f));
            g2.drawRoundRect(x, y, w - 1, h - 1, SHADE_ARC, SHADE_ARC);
        }

        // Diagonal-clipped highlight (top-left) and shadow (bottom-right)
        Composite origComp = g2.getComposite();
        java.awt.Shape origClip = g2.getClip();

        // Highlight: white on top-left half
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g2.setColor(Color.WHITE);
        java.awt.Polygon hlClip = new java.awt.Polygon(
                new int[] { x, x + w, x },
                new int[] { y, y, y + h }, 3);
        g2.clip(hlClip);
        for (int d = 0; d < 2; d++) {
            g2.drawRoundRect(x + d, y + d, w - 2 * d - 1, h - 2 * d - 1,
                    SHADE_ARC - d, SHADE_ARC - d);
        }

        // Shadow: black on bottom-right half
        g2.setClip(origClip);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g2.setColor(Color.BLACK);
        java.awt.Polygon shClip = new java.awt.Polygon(
                new int[] { x + w, x + w, x },
                new int[] { y, y + h, y + h }, 3);
        g2.clip(shClip);
        for (int d = 0; d < 2; d++) {
            g2.drawRoundRect(x + d, y + d, w - 2 * d - 1, h - 2 * d - 1,
                    SHADE_ARC - d, SHADE_ARC - d);
        }

        g2.setClip(origClip);
        g2.setComposite(origComp);
    }

    // ── Stepper-style button helpers ──

    /**
     * Paint a button in the flat pixel-corner style (matching StepperControl).
     * fillPanel + bevel + optional hover glow.
     */
    public static void paintStepperStyleButton(Graphics2D g2, int x, int y, int w, int h,
                                                boolean pressed, boolean hover, boolean enabled,
                                                Color border, Color hoverBorder, Color glowColor) {
        if (!enabled) {
            fillPanel(g2, x, y, w, h, Theme.BG_MUTED, Theme.ZINC_700);
            return;
        }
        Color bg = pressed ? Theme.ZINC_600 : hover ? Theme.ZINC_700 : Theme.BG_MUTED;
        Color bdr = hover ? hoverBorder : border;
        fillPanel(g2, x, y, w, h, bg, bdr);
        paintBevel(g2, x, y, w, h, !pressed);
        if (hover && glowColor != null) {
            paintGlow(g2, x, y, w, h, glowColor, 2);
        }
    }

    public static void paintPrimaryButton(Graphics2D g2, int x, int y, int w, int h,
                                           boolean pressed, boolean hover, boolean enabled) {
        paintStepperStyleButton(g2, x, y, w, h, pressed, hover, enabled,
                Theme.FG, Theme.SW_LAVENDER, Theme.FG);
    }

    public static void paintSecondaryButton(Graphics2D g2, int x, int y, int w, int h,
                                             boolean pressed, boolean hover, boolean enabled) {
        paintStepperStyleButton(g2, x, y, w, h, pressed, hover, enabled,
                Theme.SW_PURPLE, Theme.SW_LAVENDER, Theme.SW_CYAN);
    }

    public static void paintGhostButton(Graphics2D g2, int x, int y, int w, int h,
                                          boolean pressed, boolean hover, boolean enabled) {
        if (!enabled) return;  // ghost stays invisible when disabled; text handles dimming
        if (!hover && !pressed) return;
        paintStepperStyleButton(g2, x, y, w, h, pressed, hover, enabled,
                Theme.SW_PURPLE, Theme.SW_PURPLE, Theme.SW_CYAN);
    }

    // ── Utility ──

    /** Choose appropriate corner size based on component dimensions. */
    public static CornerSize chooseSizeForDimensions(int w, int h) {
        int min = Math.min(w, h);
        if (min < 20) return CornerSize.SM;
        if (min < 60) return CornerSize.MD;
        return CornerSize.LG;
    }

    /** Check if we should degrade to plain rectangles (too small for pixel corners). */
    public static boolean tooSmallForPixelCorners(int w, int h) {
        return Math.min(w, h) < 10;
    }
}
