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
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.sptc.uilab.tokens.PaperMinimalistTokens;

import org.delightofcomposition.envelopes.Envelope;

/**
 * Enhanced envelope editor canvas with anti-aliased rendering, larger nodes,
 * filled area under curve, grid, snap crosshairs, undo, and horizontal zoom support.
 */
public class EnvelopeCanvas extends JComponent {

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

    private static final int NODE_SIZE = 12;
    private static final int NODE_HOVER_SIZE = 14;
    private static final int HIT_RADIUS = 8;
    private static final int MAX_UNDO = 30;
    private static final int QUANT_FACT = 10;
    private static final int MINIMAP_HEIGHT = 4;

    /** X coordinate range: 0 to COORD_X_MAX. Higher = finer zoom precision. */
    static final int COORD_X_MAX = 10000;

    // dB mode constants: y-axis spans +6 dB (top) to -60 dB (bottom)
    static final double DB_MAX = 6.0;
    static final double DB_RANGE = 66.0; // 6 - (-60)

    // Pitch mode constants: y-axis spans +24 semitones (top) to -24 semitones (bottom)
    static final double PITCH_SEMI_MAX = 24.0;
    static final double PITCH_SEMI_RANGE = 48.0;

    private final Envelope envelope;
    private final Supplier<Color> curveColorFn;
    final boolean dbMode;
    final boolean pitchMode;

    private int hoveredNodeIndex = -1;
    private int selectedCoordIndex = -1;
    private int[] selectedCoord = null;
    private int dragStartX, dragStartY;

    // Segment dragging (move both endpoints)
    private int draggedSegmentIndex = -1;
    private int segDragLastX, segDragLastY;

    // Curve adjustment (Shift+drag on segment)
    private int curveAdjustSegIndex = -1;
    private int curveAdjustStartY;

    // Zoom state (horizontal, in coord space 0-COORD_X_MAX)
    private double viewX0 = 0;
    private double viewX1 = COORD_X_MAX;
    private static final double MIN_VIEW_RANGE = 500;   // ~5% of full range
    private static final double MAX_VIEW_RANGE = COORD_X_MAX;

    // Zoom state (vertical, in coord space 0-500)
    private double viewY0 = 0;
    private double viewY1 = 500;
    private static final double MIN_VIEW_Y_RANGE = 25;
    private static final double MAX_VIEW_Y_RANGE = 500;

    // Pan state (right-click drag)
    private boolean panning = false;
    private int panStartScreenX, panStartScreenY;
    private double panStartViewX0, panStartViewX1;
    private double panStartViewY0, panStartViewY1;

    // Pitch snap-to-grid
    private boolean snapToGrid = false;

    private static class UndoSnapshot {
        final ArrayList<int[]> coords;
        final ArrayList<Double> curves;
        UndoSnapshot(ArrayList<int[]> coords, ArrayList<Double> curves) {
            this.coords = coords;
            this.curves = curves;
        }
    }

    private final List<UndoSnapshot> undoStack = new ArrayList<>();
    private final List<ChangeListener> listeners = new ArrayList<>();

    // Source waveform for background rendering
    private float[] waveformData;

    // Default coords for reset
    private double[] defaultTimes;
    private double[] defaultValues;

    public EnvelopeCanvas(Envelope envelope, Supplier<Color> curveColorFn) {
        this(envelope, curveColorFn, false, false);
    }

    public EnvelopeCanvas(Envelope envelope, Supplier<Color> curveColorFn, boolean dbMode) {
        this(envelope, curveColorFn, dbMode, false);
    }

    public EnvelopeCanvas(Envelope envelope, Supplier<Color> curveColorFn, boolean dbMode, boolean pitchMode) {
        this.envelope = envelope;
        this.curveColorFn = curveColorFn;
        this.dbMode = dbMode;
        this.pitchMode = pitchMode;

        // Store defaults for reset
        if (envelope.times != null) {
            this.defaultTimes = envelope.times.clone();
            this.defaultValues = envelope.values.clone();
        }

        setOpaque(false);
        setPreferredSize(new Dimension(600, 200));
        setFocusable(true);

        setupMouseListeners();

        // Repaint timer (50ms matches existing behavior)
        Timer timer = new Timer(50, e -> repaint());
        timer.start();
    }

    // ── Coordinate conversions (zoom-aware) ──

    private int coordToScreenX(double coordX, int panelWidth) {
        return (int) ((coordX - viewX0) / (viewX1 - viewX0) * panelWidth);
    }

    private int coordToScreenY(int coordY, int panelHeight) {
        return (int) ((coordY - viewY0) / (viewY1 - viewY0) * panelHeight);
    }

    private double screenToCoordYDouble(int screenY, int panelHeight) {
        return viewY0 + (screenY / (double) panelHeight) * (viewY1 - viewY0);
    }

    private double screenToCoordXDouble(int screenX, int panelWidth) {
        return viewX0 + (screenX / (double) panelWidth) * (viewX1 - viewX0);
    }

    private int screenToCoordX(int screenX, int panelWidth) {
        double raw = screenToCoordXDouble(screenX, panelWidth);
        // Snap to a grid proportional to zoom level so zoomed-out edits aren't jittery.
        // At full zoom (10000 range, ~600px), ~17 coord units per pixel → snap to ~10.
        // When zoomed in, the snap gets finer automatically.
        double viewRange = viewX1 - viewX0;
        int snap = Math.max(1, (int) (viewRange / panelWidth * 0.6));
        return (int) (Math.round(raw / snap) * snap);
    }

    private int screenToCoordY(int screenY, int panelHeight) {
        double raw = viewY0 + (screenY / (double) panelHeight) * (viewY1 - viewY0);
        return Math.max(0, Math.min(500, (int) raw));
    }

    /** Coord X for the minimap (always full range, ignores zoom). */
    private int minimapCoordToScreenX(double coordX, int panelWidth) {
        return (int) (coordX / (double) COORD_X_MAX * panelWidth);
    }

    private boolean isZoomed() {
        return (viewX1 - viewX0) < MAX_VIEW_RANGE - 0.1
                || (viewY1 - viewY0) < MAX_VIEW_Y_RANGE - 0.1;
    }

    // ── Painting ──

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int w = getWidth();
        int h = getHeight();

        // Background
        if (Theme.isSynthwave()) {
            SynthwavePainter.fillPanel(g2, 0, 0, w, h, Theme.BG_INPUT, Theme.SW_PURPLE);
        } else if (Theme.isPaper()) {
            g2.setColor(Theme.BG_CARD);
            g2.fillRoundRect(0, 0, w - 1, h - 1,
                    PaperMinimalistTokens.RADIUS_SM, PaperMinimalistTokens.RADIUS_SM);
        } else {
            g2.setColor(Theme.BG_INPUT);
            g2.fillRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
        }

        // Minimap when zoomed
        if (isZoomed()) {
            paintMinimap(g2, w, h);
        }

        // Grid: horizontal lines (adaptive to vertical zoom)
        boolean paper = Theme.isPaper();
        Color gridColor = paper ? PaperMinimalistTokens.BORDER_FAINT : Theme.BORDER_SUBTLE;
        Color gridZeroColor = paper ? PaperMinimalistTokens.INK_GHOST : Theme.FG_DIM;
        Color gridLabelColor = paper ? PaperMinimalistTokens.INK_FAINT : Theme.FG_DIM;
        g2.setStroke(new BasicStroke(1));
        g2.setFont(paper ? PaperMinimalistTokens.FONT_BODY_XS : Theme.FONT_SMALL);
        if (dbMode) {
            double visibleDbRange = (viewY1 - viewY0) / 500.0 * DB_RANGE;
            int dbStep;
            if (visibleDbRange > 40) dbStep = 12;
            else if (visibleDbRange > 20) dbStep = 6;
            else if (visibleDbRange > 10) dbStep = 3;
            else dbStep = 1;
            // Draw dB lines within visible range
            double dbTop = DB_MAX - (viewY0 / 500.0) * DB_RANGE;
            double dbBot = DB_MAX - (viewY1 / 500.0) * DB_RANGE;
            int dbStart = (int) (Math.floor(dbTop / dbStep) * dbStep);
            int dbEnd = (int) (Math.ceil(dbBot / dbStep) * dbStep);
            for (int db = dbStart; db >= dbEnd; db -= dbStep) {
                int coordY = (int) ((DB_MAX - db) / DB_RANGE * 500);
                int gy = coordToScreenY(coordY, h);
                if (gy < -10 || gy > h + 10) continue;
                g2.setColor(db == 0 ? gridZeroColor : gridColor);
                g2.drawLine(0, gy, w, gy);
                g2.setColor(gridLabelColor);
                g2.drawString(db + " dB", 4, gy - 2);
            }
        } else if (pitchMode) {
            double visibleSemiRange = (viewY1 - viewY0) / 500.0 * PITCH_SEMI_RANGE;
            int semiStep;
            if (visibleSemiRange > 36) semiStep = 12;
            else if (visibleSemiRange > 18) semiStep = 6;
            else semiStep = 1;
            // Draw semitone lines within visible range
            double semiTop = PITCH_SEMI_MAX - (viewY0 / 500.0) * PITCH_SEMI_RANGE;
            double semiBot = PITCH_SEMI_MAX - (viewY1 / 500.0) * PITCH_SEMI_RANGE;
            int sStart = (int) (Math.floor(semiTop / semiStep) * semiStep);
            int sEnd = (int) (Math.ceil(semiBot / semiStep) * semiStep);
            for (int semi = sStart; semi >= sEnd; semi -= semiStep) {
                if (semi < -PITCH_SEMI_MAX || semi > PITCH_SEMI_MAX) continue;
                int coordY = (int) ((PITCH_SEMI_MAX - semi) / PITCH_SEMI_RANGE * 500);
                int gy = coordToScreenY(coordY, h);
                if (gy < -10 || gy > h + 10) continue;
                boolean isZeroLine = (semi == 0);
                boolean isOctave = (semi % 12 == 0);
                g2.setColor(isZeroLine ? gridZeroColor : gridColor);
                g2.drawLine(0, gy, w, gy);
                // Label
                String label;
                if (semiStep >= 12) {
                    if (semi == 0) label = "0";
                    else if (semi > 0) label = "+" + (semi / 12) + " oct";
                    else label = (semi / 12) + " oct";
                } else {
                    label = (semi >= 0 ? "+" : "") + semi + " st";
                }
                g2.setColor(gridLabelColor);
                g2.drawString(label, 4, gy - 2);
            }
        } else {
            // Normal mode: adaptive percentage lines
            double visiblePct = (viewY1 - viewY0) / 500.0 * 100;
            int pctStep;
            if (visiblePct > 60) pctStep = 25;
            else if (visiblePct > 30) pctStep = 10;
            else pctStep = 5;
            double pctTop = (1.0 - viewY0 / 500.0) * 100;
            double pctBot = (1.0 - viewY1 / 500.0) * 100;
            int pStart = (int) (Math.floor(pctTop / pctStep) * pctStep);
            int pEnd = (int) (Math.ceil(pctBot / pctStep) * pctStep);
            for (int pct = pStart; pct >= pEnd; pct -= pctStep) {
                if (pct <= 0 || pct >= 100) continue;
                int coordY = (int) (500 - pct * 5);
                int gy = coordToScreenY(coordY, h);
                if (gy < -10 || gy > h + 10) continue;
                g2.setColor(gridColor);
                g2.drawLine(0, gy, w, gy);
                if (pctStep <= 10) {
                    g2.setColor(gridLabelColor);
                    g2.drawString(pct + "%", 4, gy - 2);
                }
            }
        }

        // Grid: vertical lines (adaptive to zoom level)
        g2.setColor(gridColor);
        g2.setStroke(new BasicStroke(1));
        int gridStep = pickGridStep(viewX1 - viewX0);
        int gridStart = (int) (Math.ceil(viewX0 / gridStep) * gridStep);
        for (int x = gridStart; x <= (int) viewX1; x += gridStep) {
            if (x <= 0 || x >= COORD_X_MAX) continue;
            int gx = coordToScreenX(x, w);
            g2.drawLine(gx, 0, gx, h);
        }

        // Draw source waveform background
        if (waveformData != null && waveformData.length > 0) {
            Color waveColor = new Color(Theme.FG_DIM.getRed(), Theme.FG_DIM.getGreen(),
                    Theme.FG_DIM.getBlue(), 70);
            g2.setColor(waveColor);
            int centerY = h / 2;
            for (int px = 0; px < w; px++) {
                double t0 = (viewX0 + (px / (double) w) * (viewX1 - viewX0)) / COORD_X_MAX;
                double t1 = (viewX0 + ((px + 1) / (double) w) * (viewX1 - viewX0)) / COORD_X_MAX;
                int s0 = Math.max(0, Math.min(waveformData.length - 1, (int) (t0 * waveformData.length)));
                int s1 = Math.max(s0 + 1, Math.min(waveformData.length, (int) (t1 * waveformData.length)));
                float minVal = 0, maxVal = 0;
                for (int s = s0; s < s1; s++) {
                    if (waveformData[s] < minVal) minVal = waveformData[s];
                    if (waveformData[s] > maxVal) maxVal = waveformData[s];
                }
                int yMin = Math.max(0, Math.min(h - 1, centerY - (int) (maxVal * centerY)));
                int yMax = Math.max(0, Math.min(h - 1, centerY - (int) (minVal * centerY)));
                int lineH = yMax - yMin;
                if (lineH > 0) g2.fillRect(px, yMin, 1, lineH);
            }
        }

        if (envelope.coords == null || envelope.coords.size() < 2) {
            g2.dispose();
            return;
        }

        // Build path
        GeneralPath curvePath = new GeneralPath();
        GeneralPath fillPath = new GeneralPath();

        int firstSX = coordToScreenX(envelope.coords.get(0)[0], w);
        int firstSY = coordToScreenY(envelope.coords.get(0)[1], h);
        curvePath.moveTo(firstSX, firstSY);
        fillPath.moveTo(firstSX, h);
        fillPath.lineTo(firstSX, firstSY);

        for (int i = 1; i < envelope.coords.size(); i++) {
            int sx = coordToScreenX(envelope.coords.get(i)[0], w);
            int sy = coordToScreenY(envelope.coords.get(i)[1], h);
            double curve = (i - 1 < envelope.segmentCurves.size())
                    ? envelope.segmentCurves.get(i - 1) : 0.0;
            if (curve == 0.0) {
                curvePath.lineTo(sx, sy);
                fillPath.lineTo(sx, sy);
            } else {
                int prevSX = coordToScreenX(envelope.coords.get(i - 1)[0], w);
                int prevSY = coordToScreenY(envelope.coords.get(i - 1)[1], h);
                int steps = Math.max(8, Math.abs(sx - prevSX) / 4);
                for (int s = 1; s <= steps; s++) {
                    double frac = s / (double) steps;
                    double shaped = Envelope.applyCurve(frac, curve);
                    float px = (float) (prevSX + frac * (sx - prevSX));
                    float py = (float) (prevSY + shaped * (sy - prevSY));
                    curvePath.lineTo(px, py);
                    fillPath.lineTo(px, py);
                }
            }
        }

        int lastSX = coordToScreenX(envelope.coords.get(envelope.coords.size() - 1)[0], w);
        fillPath.lineTo(lastSX, h);
        fillPath.closePath();

        // Resolve current curve color from supplier
        Color curveColor = curveColorFn.get();
        Color fillColor = new Color(curveColor.getRed(), curveColor.getGreen(),
                curveColor.getBlue(), 30);

        // Clip to canvas bounds for zoomed view
        g2.clipRect(0, 0, w, h);

        // Fill area under curve
        if (Theme.isSynthwave()) {
            java.awt.GradientPaint gp = new java.awt.GradientPaint(
                    0, 0, new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 50),
                    0, h, new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 5));
            g2.setPaint(gp);
            g2.fill(fillPath);
        } else if (paper) {
            // Layer 1: smooth alpha gradient
            java.awt.GradientPaint gp = new java.awt.GradientPaint(
                    0, 0, new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 35),
                    0, h, new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 3));
            g2.setPaint(gp);
            g2.fill(fillPath);

            // Layer 2: dithered darker tone with steeper falloff
            java.awt.Shape oldClip2 = g2.getClip();
            g2.clip(fillPath);
            Color darkerTone = new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 25);
            g2.setColor(darkerTone);
            java.awt.Rectangle bounds = fillPath.getBounds();
            for (int py = bounds.y; py < bounds.y + bounds.height; py++) {
                float vertFrac = (float)(py - bounds.y) / bounds.height;
                float density = 1.3f - vertFrac * 1.6f;
                if (density <= 0f) break;
                for (int px = bounds.x; px < bounds.x + bounds.width; px++) {
                    int bx = ((int)(px / 1.5) + 4) & 7;
                    int by = ((int)(py / 1.5) + 3) & 7;
                    if (density > BAYER8[by][bx]) {
                        g2.fillRect(px, py, 1, 1);
                    }
                }
            }
            g2.setClip(oldClip2);
        } else {
            g2.setColor(fillColor);
            g2.fill(fillPath);
        }

        // Draw curve line — synthwave gets glow effect
        if (Theme.isSynthwave()) {
            // Glow pass: wider, semi-transparent
            g2.setColor(new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 40));
            g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(curvePath);
        }
        g2.setColor(curveColor);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(curvePath);

        // Draw nodes
        for (int i = 0; i < envelope.coords.size(); i++) {
            int[] coord = envelope.coords.get(i);
            int sx = coordToScreenX(coord[0], w);
            int sy = coordToScreenY(coord[1], h);

            // Skip nodes well outside visible area
            if (sx < -NODE_HOVER_SIZE || sx > w + NODE_HOVER_SIZE) continue;
            if (sy < -NODE_HOVER_SIZE || sy > h + NODE_HOVER_SIZE) continue;

            boolean isHovered = (i == hoveredNodeIndex);
            int size = isHovered ? NODE_HOVER_SIZE : NODE_SIZE;

            if (Theme.isSynthwave()) {
                // Square nodes with pixel corners
                SynthwavePainter.fillPanel(g2, sx - size / 2, sy - size / 2, size, size,
                        Theme.SW_LAVENDER, isHovered ? Theme.ACCENT_HOVER : curveColor);
            } else if (paper) {
                int sz = isHovered ? 10 : 8;
                g2.setColor(PaperMinimalistTokens.PAPER);
                g2.fillOval(sx - sz / 2, sy - sz / 2, sz, sz);
                g2.setStroke(new BasicStroke(PaperMinimalistTokens.BORDER_WIDTH));
                g2.setColor(isHovered ? curveColor : PaperMinimalistTokens.INK);
                g2.drawOval(sx - sz / 2, sy - sz / 2, sz - 1, sz - 1);
            } else {
                g2.setColor(Theme.THUMB);
                g2.fillOval(sx - size / 2, sy - size / 2, size, size);
                g2.setColor(isHovered ? Theme.ACCENT_HOVER : curveColor);
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(sx - size / 2, sy - size / 2, size - 1, size - 1);
            }
        }

        // Snap crosshairs when dragging
        if (selectedCoord != null && selectedCoordIndex >= 0) {
            int sx = coordToScreenX(selectedCoord[0], w);
            int sy = coordToScreenY(selectedCoord[1], h);
            g2.setColor(new Color(curveColor.getRed(), curveColor.getGreen(),
                    curveColor.getBlue(), 80));
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1f, new float[]{4f, 4f}, 0));
            g2.drawLine(sx, 0, sx, h);
            g2.drawLine(0, sy, w, sy);
        }

        // Zoom range labels when zoomed
        if (isZoomed()) {
            g2.setFont(paper ? PaperMinimalistTokens.FONT_BODY_XS : Theme.FONT_SMALL);
            g2.setColor(gridLabelColor);
            String leftLabel = String.format("%.1f%%", viewX0 / COORD_X_MAX * 100);
            String rightLabel = String.format("%.1f%%", viewX1 / COORD_X_MAX * 100);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(leftLabel, 4, h - 4);
            g2.drawString(rightLabel, w - fm.stringWidth(rightLabel) - 4, h - 4);
        }

        // Focus ring
        if (isFocusOwner() && !Theme.isSynthwave()) {
            g2.setClip(null); // reset clip from zoom
            if (Theme.isSynthwave()) {
                SynthwavePainter.strokeShape(g2, 1, 1, w - 2, h - 2, Theme.RING);
            } else {
                g2.setColor(Theme.RING);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, Theme.RADIUS, Theme.RADIUS);
            }
        }

        // Update times/values arrays
        syncEnvelopeArrays();

        g2.dispose();
    }

    private void paintMinimap(Graphics2D g2, int w, int h) {
        // Minimap background
        g2.setColor(new Color(Theme.BG_MUTED.getRed(), Theme.BG_MUTED.getGreen(),
                Theme.BG_MUTED.getBlue(), 180));
        g2.fillRect(0, 0, w, MINIMAP_HEIGHT);

        // Draw simplified envelope curve in minimap
        if (envelope.coords != null && envelope.coords.size() >= 2) {
            Color curveColor = curveColorFn.get();
            g2.setColor(new Color(curveColor.getRed(), curveColor.getGreen(),
                    curveColor.getBlue(), 120));
            g2.setStroke(new BasicStroke(1f));
            for (int i = 0; i < envelope.coords.size() - 1; i++) {
                int x1 = minimapCoordToScreenX(envelope.coords.get(i)[0], w);
                int y1 = (int) (envelope.coords.get(i)[1] / 500.0 * MINIMAP_HEIGHT);
                int x2 = minimapCoordToScreenX(envelope.coords.get(i + 1)[0], w);
                int y2 = (int) (envelope.coords.get(i + 1)[1] / 500.0 * MINIMAP_HEIGHT);
                g2.drawLine(x1, y1, x2, y2);
            }
        }

        // Viewport indicator
        int vpLeft = minimapCoordToScreenX(viewX0, w);
        int vpRight = minimapCoordToScreenX(viewX1, w);
        g2.setColor(new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(),
                Theme.ACCENT.getBlue(), 60));
        g2.fillRect(vpLeft, 0, vpRight - vpLeft, MINIMAP_HEIGHT);
        g2.setColor(Theme.ACCENT);
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(vpLeft, 0, vpLeft, MINIMAP_HEIGHT);
        g2.drawLine(vpRight, 0, vpRight, MINIMAP_HEIGHT);
    }

    /** Pick a grid step that gives roughly 5-10 vertical grid lines. */
    private int pickGridStep(double viewRange) {
        int[] steps = {100, 200, 500, 1000, 2000, 2500, 5000};
        for (int step : steps) {
            if (viewRange / step <= 12) return step;
        }
        return 5000;
    }

    // ── Zoom and pan ──

    private void zoomAtCursor(int screenX, int panelWidth, double factor) {
        double cursorCoord = screenToCoordXDouble(screenX, panelWidth);
        double newX0 = cursorCoord - (cursorCoord - viewX0) * factor;
        double newX1 = cursorCoord + (viewX1 - cursorCoord) * factor;

        double range = newX1 - newX0;
        if (range < MIN_VIEW_RANGE) {
            double center = (newX0 + newX1) / 2;
            newX0 = center - MIN_VIEW_RANGE / 2;
            newX1 = center + MIN_VIEW_RANGE / 2;
        } else if (range > MAX_VIEW_RANGE) {
            newX0 = 0;
            newX1 = COORD_X_MAX;
        }

        // Clamp to [0, COORD_X_MAX]
        if (newX0 < 0) { newX1 -= newX0; newX0 = 0; }
        if (newX1 > COORD_X_MAX) { newX0 -= (newX1 - COORD_X_MAX); newX1 = COORD_X_MAX; }
        newX0 = Math.max(0, newX0);
        newX1 = Math.min(COORD_X_MAX, newX1);

        viewX0 = newX0;
        viewX1 = newX1;
        repaint();
    }

    private void zoomYAtCursor(int screenY, int panelHeight, double factor) {
        double cursorCoord = screenToCoordYDouble(screenY, panelHeight);
        double newY0 = cursorCoord - (cursorCoord - viewY0) * factor;
        double newY1 = cursorCoord + (viewY1 - cursorCoord) * factor;

        double range = newY1 - newY0;
        if (range < MIN_VIEW_Y_RANGE) {
            double center = (newY0 + newY1) / 2;
            newY0 = center - MIN_VIEW_Y_RANGE / 2;
            newY1 = center + MIN_VIEW_Y_RANGE / 2;
        } else if (range > MAX_VIEW_Y_RANGE) {
            newY0 = 0;
            newY1 = 500;
        }

        if (newY0 < 0) { newY1 -= newY0; newY0 = 0; }
        if (newY1 > 500) { newY0 -= (newY1 - 500); newY1 = 500; }
        newY0 = Math.max(0, newY0);
        newY1 = Math.min(500, newY1);

        viewY0 = newY0;
        viewY1 = newY1;
        repaint();
    }

    private void resetZoom() {
        viewX0 = 0;
        viewX1 = COORD_X_MAX;
        viewY0 = 0;
        viewY1 = 500;
        repaint();
    }

    // ── Value conversions ──

    private void syncEnvelopeArrays() {
        int n = envelope.coords.size();
        envelope.times = new double[n];
        envelope.values = new double[n];
        for (int i = 0; i < n; i++) {
            int[] coord = envelope.coords.get(i);
            envelope.times[i] = coord[0] / (double) COORD_X_MAX;
            envelope.values[i] = coordToValue(coord[1]);
        }
        // Sync curves array from segmentCurves list
        envelope.curves = new double[n];
        for (int i = 0; i < n && i < envelope.segmentCurves.size(); i++) {
            envelope.curves[i] = envelope.segmentCurves.get(i);
        }
    }

    /** Convert coord Y (0-500) to value. In dB mode returns linear gain, in pitch mode returns frequency ratio. */
    double coordToValue(int coordY) {
        if (pitchMode) {
            double semi = PITCH_SEMI_MAX - (coordY / 500.0) * PITCH_SEMI_RANGE;
            return Math.pow(2, semi / 12.0);
        }
        if (!dbMode) {
            return (500 - coordY) / 500.0;
        }
        double dB = DB_MAX - (coordY / 500.0) * DB_RANGE;
        return Math.pow(10, dB / 20.0);
    }

    /** Convert value to coord Y (0-500). */
    int valueToCoord(double value) {
        if (pitchMode) {
            if (value <= 0) return 500;
            double semi = 12.0 * Math.log(value) / Math.log(2);
            semi = Math.max(-PITCH_SEMI_MAX, Math.min(PITCH_SEMI_MAX, semi));
            return (int) ((PITCH_SEMI_MAX - semi) / PITCH_SEMI_RANGE * 500);
        }
        if (!dbMode) {
            return (int) (500 - value * 500);
        }
        if (value <= 0) return 500;
        double dB = 20 * Math.log10(value);
        dB = Math.max(DB_MAX - DB_RANGE, Math.min(DB_MAX, dB));
        return (int) ((DB_MAX - dB) / DB_RANGE * 500);
    }

    // ── Undo ──

    private void pushUndo() {
        ArrayList<int[]> coordSnap = new ArrayList<>();
        for (int[] c : envelope.coords) {
            coordSnap.add(new int[]{c[0], c[1]});
        }
        ArrayList<Double> curveSnap = new ArrayList<>(envelope.segmentCurves);
        undoStack.add(new UndoSnapshot(coordSnap, curveSnap));
        if (undoStack.size() > MAX_UNDO) {
            undoStack.remove(0);
        }
    }

    // ── Segment hit detection ──

    /**
     * Find which segment the screen point is on (within tolerance).
     * Returns segment index (segment i connects node i to node i+1), or -1.
     */
    private int findSegmentAt(int screenX, int screenY) {
        int w = getWidth();
        int h = getHeight();
        int tolerance = HIT_RADIUS;

        for (int i = 0; i < envelope.coords.size() - 1; i++) {
            int x1 = coordToScreenX(envelope.coords.get(i)[0], w);
            int x2 = coordToScreenX(envelope.coords.get(i + 1)[0], w);
            if (screenX < Math.min(x1, x2) - tolerance || screenX > Math.max(x1, x2) + tolerance)
                continue;

            int y1 = coordToScreenY(envelope.coords.get(i)[1], h);
            int y2 = coordToScreenY(envelope.coords.get(i + 1)[1], h);

            // Interpolate Y at this screen X, accounting for curve
            double frac = (x2 == x1) ? 0.5 : (double) (screenX - x1) / (x2 - x1);
            frac = Math.max(0, Math.min(1, frac));
            double curve = (i < envelope.segmentCurves.size()) ? envelope.segmentCurves.get(i) : 0.0;
            double shapedFrac = Envelope.applyCurve(frac, curve);
            double expectedY = y1 + shapedFrac * (y2 - y1);

            if (Math.abs(screenY - expectedY) <= tolerance) {
                return i;
            }
        }
        return -1;
    }

    // ── Mouse interaction ──

    private void setupMouseListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                // Right-click: start panning (don't add nodes)
                if (e.getButton() == MouseEvent.BUTTON3) {
                    if (e.getClickCount() == 2) {
                        resetZoom();
                    } else if (isZoomed()) {
                        panning = true;
                        panStartScreenX = e.getX();
                        panStartScreenY = e.getY();
                        panStartViewX0 = viewX0;
                        panStartViewX1 = viewX1;
                        panStartViewY0 = viewY0;
                        panStartViewY1 = viewY1;
                    }
                    return;
                }

                int w = getWidth();
                int h = getHeight();
                int x = screenToCoordX(e.getX(), w);
                int y = screenToCoordY(e.getY(), h);

                // Clamp x to valid coord range for node placement
                x = Math.max(0, Math.min(COORD_X_MAX, x));

                if (envelope.type == 1) {
                    boolean isCtrl = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0;
                    boolean isMeta = (e.getModifiersEx() & MouseEvent.META_DOWN_MASK) != 0;
                    boolean isAlt = (e.getModifiersEx() & MouseEvent.ALT_DOWN_MASK) != 0;
                    boolean isShift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;

                    if (isCtrl || isMeta) {
                        // Ctrl/Cmd click: delete node if hovering one, else add node
                        pushUndo();
                        if (hoveredNodeIndex >= 0
                                && envelope.coords.size() > 2
                                && hoveredNodeIndex != 0
                                && hoveredNodeIndex != envelope.coords.size() - 1) {
                            // Delete hovered node and its curve entry
                            envelope.coords.remove(hoveredNodeIndex);
                            // When deleting node i, merge segments i-1 and i: remove curve at i-1
                            int curveIdx = Math.max(0, hoveredNodeIndex - 1);
                            if (curveIdx < envelope.segmentCurves.size()) {
                                envelope.segmentCurves.remove(curveIdx);
                            }
                            hoveredNodeIndex = -1;
                        } else {
                            // Add new node
                            int[] coord = {x, snapCoordY(y)};
                            int index = Collections.binarySearch(envelope.coords, coord,
                                    (c1, c2) -> c1[0] - c2[0]);
                            if (index >= 0) {
                                envelope.coords.remove(index);
                                if (index < envelope.segmentCurves.size()) {
                                    envelope.segmentCurves.remove(index);
                                }
                            } else {
                                index = -(index + 1);
                            }
                            envelope.coords.add(index, coord);
                            // Insert a 0.0 curve for the new segment
                            if (index <= envelope.segmentCurves.size()) {
                                envelope.segmentCurves.add(index, 0.0);
                            }
                            // Pin first/last node x-positions
                            envelope.coords.get(0)[0] = 0;
                            envelope.coords.get(envelope.coords.size() - 1)[0] = COORD_X_MAX;
                            selectedCoord = coord;
                            selectedCoordIndex = index;
                        }
                        fireChangeListeners();
                    } else if (isAlt) {
                        // Alt click: flat line from previous node
                        pushUndo();
                        int[] lastNode = null;
                        int index = 0;
                        for (int i = 0; i < envelope.coords.size(); i++) {
                            int[] coord = envelope.coords.get(i);
                            if (coord[0] > x) break;
                            lastNode = coord;
                            index = i;
                        }
                        if (lastNode != null) {
                            int[] coord = new int[]{x, lastNode[1]};
                            envelope.coords.add(index + 1, coord);
                            envelope.segmentCurves.add(index + 1, 0.0);
                            selectedCoord = coord;
                            selectedCoordIndex = index + 1;
                        }
                        fireChangeListeners();
                    } else if (hoveredNodeIndex >= 0) {
                        // Clicking on an existing node: grab it for dragging
                        pushUndo();
                        selectedCoord = envelope.coords.get(hoveredNodeIndex);
                        selectedCoordIndex = hoveredNodeIndex;
                        selectedCoord[1] = snapCoordY(y);
                        fireChangeListeners();
                    } else if (isShift) {
                        // Shift+click on segment: begin curve adjustment
                        int segIdx = findSegmentAt(e.getX(), e.getY());
                        if (segIdx >= 0) {
                            pushUndo();
                            curveAdjustSegIndex = segIdx;
                            curveAdjustStartY = e.getY();
                        }
                    } else {
                        // Plain click on segment: begin segment drag (move both endpoints)
                        int segIdx = findSegmentAt(e.getX(), e.getY());
                        if (segIdx >= 0) {
                            pushUndo();
                            draggedSegmentIndex = segIdx;
                            segDragLastX = e.getX();
                            segDragLastY = e.getY();
                        }
                    }
                }
                dragStartX = e.getX();
                dragStartY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                panning = false;
                selectedCoord = null;
                selectedCoordIndex = -1;
                draggedSegmentIndex = -1;
                curveAdjustSegIndex = -1;
                repaint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int w = getWidth();
                int h = getHeight();
                int oldHovered = hoveredNodeIndex;
                hoveredNodeIndex = -1;

                for (int i = 0; i < envelope.coords.size(); i++) {
                    int[] coord = envelope.coords.get(i);
                    int sx = coordToScreenX(coord[0], w);
                    int sy = coordToScreenY(coord[1], h);
                    double dist = Math.sqrt(Math.pow(e.getX() - sx, 2) + Math.pow(e.getY() - sy, 2));
                    if (dist <= HIT_RADIUS) {
                        hoveredNodeIndex = i;
                        break;
                    }
                }

                if (hoveredNodeIndex >= 0) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                } else if (findSegmentAt(e.getX(), e.getY()) >= 0) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                } else {
                    setCursor(Cursor.getDefaultCursor());
                }

                if (oldHovered != hoveredNodeIndex) {
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panning) {
                    int w = getWidth();
                    int h = getHeight();
                    // Horizontal pan
                    double deltaX = (panStartScreenX - e.getX()) / (double) w * (panStartViewX1 - panStartViewX0);
                    double newX0 = panStartViewX0 + deltaX;
                    double newX1 = panStartViewX1 + deltaX;
                    double rangeX = newX1 - newX0;
                    if (newX0 < 0) { newX0 = 0; newX1 = rangeX; }
                    if (newX1 > COORD_X_MAX) { newX1 = COORD_X_MAX; newX0 = COORD_X_MAX - rangeX; }
                    viewX0 = Math.max(0, newX0);
                    viewX1 = Math.min(COORD_X_MAX, newX1);

                    // Vertical pan
                    double deltaY = (panStartScreenY - e.getY()) / (double) h * (panStartViewY1 - panStartViewY0);
                    double newY0 = panStartViewY0 + deltaY;
                    double newY1 = panStartViewY1 + deltaY;
                    double rangeY = newY1 - newY0;
                    if (newY0 < 0) { newY0 = 0; newY1 = rangeY; }
                    if (newY1 > 500) { newY1 = 500; newY0 = 500 - rangeY; }
                    viewY0 = Math.max(0, newY0);
                    viewY1 = Math.min(500, newY1);

                    repaint();
                    return;
                }

                // Curve adjustment: Shift+drag on segment
                if (curveAdjustSegIndex >= 0 && curveAdjustSegIndex < envelope.segmentCurves.size()) {
                    int deltaY = curveAdjustStartY - e.getY(); // up = positive
                    double newCurve = Math.max(-1.0, Math.min(1.0,
                            envelope.segmentCurves.get(curveAdjustSegIndex) + deltaY * 0.01));
                    envelope.segmentCurves.set(curveAdjustSegIndex, newCurve);
                    curveAdjustStartY = e.getY();
                    syncEnvelopeArrays();
                    fireChangeListeners();
                    repaint();
                    return;
                }

                // Segment drag: move both endpoint nodes
                if (draggedSegmentIndex >= 0) {
                    int w = getWidth();
                    int h = getHeight();
                    int i0 = draggedSegmentIndex;
                    int i1 = draggedSegmentIndex + 1;
                    int[] c0 = envelope.coords.get(i0);
                    int[] c1 = envelope.coords.get(i1);

                    // Vertical movement
                    int deltaScreenY = e.getY() - segDragLastY;
                    int deltaCoordY = (int) (deltaScreenY * (viewY1 - viewY0) / h);
                    if (deltaCoordY != 0) {
                        c0[1] = snapCoordY(Math.max(0, Math.min(500, c0[1] + deltaCoordY)));
                        c1[1] = snapCoordY(Math.max(0, Math.min(500, c1[1] + deltaCoordY)));
                        segDragLastY = e.getY();
                    }

                    // Horizontal movement — clamp to neighbors, pin first/last
                    double deltaCoordX = (e.getX() - segDragLastX) / (double) w * (viewX1 - viewX0);
                    int dx = (int) deltaCoordX;
                    if (dx != 0) {
                        // Find left/right bounds from neighboring nodes
                        int leftBound = (i0 == 0) ? 0
                                : envelope.coords.get(i0 - 1)[0] + 1;
                        int rightBound = (i1 == envelope.coords.size() - 1) ? COORD_X_MAX
                                : envelope.coords.get(i1 + 1)[0] - 1;

                        // Pin first/last nodes to edges
                        boolean leftPinned = (i0 == 0);
                        boolean rightPinned = (i1 == envelope.coords.size() - 1);

                        if (!leftPinned && !rightPinned) {
                            // Both can move: clamp delta so neither crosses its neighbor
                            int maxLeft = leftBound - c0[0];
                            int maxRight = rightBound - c1[0];
                            dx = Math.max(maxLeft, Math.min(maxRight, dx));
                            c0[0] += dx;
                            c1[0] += dx;
                        } else if (!leftPinned) {
                            // Only left node moves (right is pinned to edge)
                            dx = Math.max(leftBound - c0[0], Math.min(c1[0] - 1 - c0[0], dx));
                            c0[0] += dx;
                        } else if (!rightPinned) {
                            // Only right node moves (left is pinned to edge)
                            dx = Math.max(c0[0] + 1 - c1[0], Math.min(rightBound - c1[0], dx));
                            c1[0] += dx;
                        }
                        // Both pinned: no horizontal movement possible
                        segDragLastX = e.getX();
                    }

                    fireChangeListeners();
                    repaint();
                    return;
                }

                // Individual node drag
                if (envelope.type == 1 && selectedCoord != null && selectedCoordIndex >= 0) {
                    int w = getWidth();
                    int h = getHeight();
                    int qx = screenToCoordX(e.getX(), w);
                    int y = screenToCoordY(e.getY(), h);

                    // Clamp to valid range
                    qx = Math.max(0, Math.min(COORD_X_MAX, qx));

                    boolean isAlt = (e.getModifiersEx() & MouseEvent.ALT_DOWN_MASK) != 0;

                    // Keep x within bounds of neighbors; pin first/last to edges
                    int sidx = selectedCoordIndex;
                    if (sidx == 0) {
                        selectedCoord[0] = 0;
                    } else if (sidx == envelope.coords.size() - 1) {
                        selectedCoord[0] = COORD_X_MAX;
                    } else if (envelope.coords.get(sidx - 1)[0] < qx
                            && qx < envelope.coords.get(sidx + 1)[0]) {
                        selectedCoord[0] = qx;
                    }
                    if (!isAlt) {
                        selectedCoord[1] = snapCoordY(y);
                    }
                    fireChangeListeners();
                    repaint();
                }
            }
        });

        // Scroll wheel: zoom (Shift = vertical, else horizontal)
        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = e.getWheelRotation() < 0 ? 0.8 : 1.25;
                boolean isShift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;
                if (isShift) {
                    zoomYAtCursor(e.getY(), getHeight(), factor);
                } else {
                    zoomAtCursor(e.getX(), getWidth(), factor);
                }
            }
        });
    }

    // ── Public API ──

    public void reset(double[] resetTimes, double[] resetValues) {
        pushUndo();
        envelope.coords.clear();
        envelope.segmentCurves.clear();
        if (resetTimes != null && resetTimes.length > 0) {
            for (int i = 0; i < resetTimes.length; i++) {
                int x = (int) (resetTimes[i] * COORD_X_MAX);
                int y = valueToCoord(resetValues[i]);
                envelope.coords.add(new int[]{x, y});
            }
            for (int i = 0; i < resetTimes.length; i++) {
                envelope.segmentCurves.add(0.0);
            }
        } else {
            envelope.coords.add(new int[]{0, 500});
            envelope.coords.add(new int[]{COORD_X_MAX, 500});
            envelope.segmentCurves.add(0.0);
        }
        syncEnvelopeArrays();
        fireChangeListeners();
        repaint();
    }

    public void reset() {
        reset(defaultTimes, defaultValues);
    }

    public void applySine(int cycles, double amplitude, double phaseDeg) {
        pushUndo();
        envelope.coords.clear();
        envelope.segmentCurves.clear();
        int numPoints = Math.max(cycles * 10, 20);
        double phaseRad = Math.toRadians(phaseDeg);
        for (int i = 0; i <= numPoints; i++) {
            double t = (double) i / numPoints;
            int x = (int) (t * COORD_X_MAX);
            double val = amplitude * (Math.sin(phaseRad + 2 * Math.PI * cycles * t) + 1) / 2;
            int y = dbMode ? valueToCoord(val) : (int) (500 - val * 500);
            envelope.coords.add(new int[]{x, y});
            envelope.segmentCurves.add(0.0);
        }
        syncEnvelopeArrays();
        fireChangeListeners();
        repaint();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        UndoSnapshot prev = undoStack.remove(undoStack.size() - 1);
        envelope.coords.clear();
        envelope.coords.addAll(prev.coords);
        envelope.segmentCurves.clear();
        envelope.segmentCurves.addAll(prev.curves);
        syncEnvelopeArrays();
        fireChangeListeners();
        repaint();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public Envelope getEnvelope() {
        return envelope;
    }

    public void setSnapToGrid(boolean snap) {
        this.snapToGrid = snap;
        repaint();
    }

    public boolean isSnapToGrid() {
        return snapToGrid;
    }

    /** Snap coord Y to nearest semitone (pitch mode only). */
    private int snapCoordY(int coordY) {
        if (!pitchMode || !snapToGrid) return coordY;
        double semi = PITCH_SEMI_MAX - (coordY / 500.0) * PITCH_SEMI_RANGE;
        semi = Math.round(semi);
        semi = Math.max(-PITCH_SEMI_MAX, Math.min(PITCH_SEMI_MAX, semi));
        return (int) ((PITCH_SEMI_MAX - semi) / PITCH_SEMI_RANGE * 500);
    }

    public void setWaveformData(float[] samples) {
        this.waveformData = samples;
        repaint();
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    public void clearChangeListeners() {
        listeners.clear();
    }

    private void fireChangeListeners() {
        ChangeEvent evt = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(evt);
    }
}
