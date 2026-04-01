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

import org.delightofcomposition.envelopes.Envelope;

/**
 * Enhanced envelope editor canvas with anti-aliased rendering, larger nodes,
 * filled area under curve, grid, snap crosshairs, undo, and horizontal zoom support.
 */
public class EnvelopeCanvas extends JComponent {

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

    // Zoom state (horizontal only, in coord space 0-COORD_X_MAX)
    private double viewX0 = 0;
    private double viewX1 = COORD_X_MAX;
    private static final double MIN_VIEW_RANGE = 500;   // ~5% of full range
    private static final double MAX_VIEW_RANGE = COORD_X_MAX;

    // Pan state (right-click drag)
    private boolean panning = false;
    private int panStartScreenX;
    private double panStartViewX0, panStartViewX1;

    private final List<ArrayList<int[]>> undoStack = new ArrayList<>();
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
        return (int) (coordY * panelHeight / 500.0);
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
        return Math.max(0, Math.min(500, (int) (screenY * 500.0 / panelHeight)));
    }

    /** Coord X for the minimap (always full range, ignores zoom). */
    private int minimapCoordToScreenX(double coordX, int panelWidth) {
        return (int) (coordX / (double) COORD_X_MAX * panelWidth);
    }

    private boolean isZoomed() {
        return (viewX1 - viewX0) < MAX_VIEW_RANGE - 0.1;
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
        } else {
            g2.setColor(Theme.BG_INPUT);
            g2.fillRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
        }

        // Minimap when zoomed
        if (isZoomed()) {
            paintMinimap(g2, w, h);
        }

        // Grid: horizontal lines
        g2.setColor(Theme.BORDER_SUBTLE);
        g2.setStroke(new BasicStroke(1));
        if (dbMode) {
            g2.setFont(Theme.FONT_SMALL);
            int[] dbLines = {0, -6, -12, -24, -48};
            for (int db : dbLines) {
                int coordY = (int) ((DB_MAX - db) / DB_RANGE * 500);
                int gy = coordToScreenY(coordY, h);
                g2.setColor(Theme.BORDER_SUBTLE);
                g2.drawLine(0, gy, w, gy);
                String label = db + " dB";
                g2.setColor(Theme.FG_DIM);
                g2.drawString(label, 4, gy - 2);
            }
        } else if (pitchMode) {
            g2.setFont(Theme.FONT_SMALL);
            int[] semiLines = {24, 12, 0, -12, -24};
            String[] labels = {"+2 oct", "+1 oct", "0", "-1 oct", "-2 oct"};
            for (int s = 0; s < semiLines.length; s++) {
                int coordY = (int) ((PITCH_SEMI_MAX - semiLines[s]) / PITCH_SEMI_RANGE * 500);
                int gy = coordToScreenY(coordY, h);
                g2.setColor(semiLines[s] == 0 ? Theme.FG_DIM : Theme.BORDER_SUBTLE);
                g2.drawLine(0, gy, w, gy);
                g2.setColor(Theme.FG_DIM);
                g2.drawString(labels[s], 4, gy - 2);
            }
        } else {
            for (int pct = 25; pct <= 75; pct += 25) {
                int gy = h * pct / 100;
                g2.drawLine(0, gy, w, gy);
            }
        }

        // Grid: vertical lines (adaptive to zoom level)
        g2.setColor(Theme.BORDER_SUBTLE);
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
            curvePath.lineTo(sx, sy);
            fillPath.lineTo(sx, sy);
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
            // Gradient fill: accent to transparent
            java.awt.GradientPaint gp = new java.awt.GradientPaint(
                    0, 0, new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 50),
                    0, h, new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 5));
            g2.setPaint(gp);
        } else {
            g2.setColor(fillColor);
        }
        g2.fill(fillPath);

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

            boolean isHovered = (i == hoveredNodeIndex);
            int size = isHovered ? NODE_HOVER_SIZE : NODE_SIZE;

            if (Theme.isSynthwave()) {
                // Square nodes with pixel corners
                SynthwavePainter.fillPanel(g2, sx - size / 2, sy - size / 2, size, size,
                        Theme.SW_LAVENDER, isHovered ? Theme.ACCENT_HOVER : curveColor);
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
            g2.setFont(Theme.FONT_SMALL);
            g2.setColor(Theme.FG_DIM);
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

    private void resetZoom() {
        viewX0 = 0;
        viewX1 = COORD_X_MAX;
        repaint();
    }

    // ── Value conversions ──

    private void syncEnvelopeArrays() {
        envelope.times = new double[envelope.coords.size()];
        envelope.values = new double[envelope.coords.size()];
        for (int i = 0; i < envelope.coords.size(); i++) {
            int[] coord = envelope.coords.get(i);
            envelope.times[i] = coord[0] / (double) COORD_X_MAX;
            envelope.values[i] = coordToValue(coord[1]);
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
        ArrayList<int[]> snapshot = new ArrayList<>();
        for (int[] c : envelope.coords) {
            snapshot.add(new int[]{c[0], c[1]});
        }
        undoStack.add(snapshot);
        if (undoStack.size() > MAX_UNDO) {
            undoStack.remove(0);
        }
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
                        panStartViewX0 = viewX0;
                        panStartViewX1 = viewX1;
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

                    if (isCtrl || isMeta) {
                        // Ctrl/Cmd click: delete nearest node
                        pushUndo();
                        int shortestDist = Integer.MAX_VALUE;
                        int[] closest = null;
                        for (int[] coord : envelope.coords) {
                            int dist = Math.abs(coord[0] - x);
                            if (dist < shortestDist) {
                                shortestDist = dist;
                                closest = coord;
                            }
                        }
                        if (closest != null && envelope.coords.size() > 2
                                && closest != envelope.coords.get(0)
                                && closest != envelope.coords.get(envelope.coords.size() - 1)) {
                            envelope.coords.remove(closest);
                        }
                        fireChangeListeners();
                    } else if (isAlt) {
                        // Option click: flat line from previous node
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
                            selectedCoord = coord;
                            selectedCoordIndex = index + 1;
                        }
                        fireChangeListeners();
                    } else if (hoveredNodeIndex >= 0) {
                        // Clicking on an existing node: grab it for dragging
                        pushUndo();
                        selectedCoord = envelope.coords.get(hoveredNodeIndex);
                        selectedCoordIndex = hoveredNodeIndex;
                        selectedCoord[1] = y;
                        fireChangeListeners();
                    } else {
                        // Normal click on empty space: add new point
                        pushUndo();
                        int[] coord = {x, y};
                        int index = Collections.binarySearch(envelope.coords, coord,
                                (c1, c2) -> c1[0] - c2[0]);
                        if (index >= 0) {
                            envelope.coords.remove(index);
                        } else {
                            index = -(index + 1);
                        }
                        envelope.coords.add(index, coord);
                        // Pin first/last node x-positions
                        envelope.coords.get(0)[0] = 0;
                        envelope.coords.get(envelope.coords.size() - 1)[0] = COORD_X_MAX;
                        selectedCoord = coord;
                        selectedCoordIndex = index;
                        fireChangeListeners();
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

                setCursor(hoveredNodeIndex >= 0
                        ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                        : Cursor.getDefaultCursor());

                if (oldHovered != hoveredNodeIndex) {
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panning) {
                    int w = getWidth();
                    double deltaCoord = (panStartScreenX - e.getX()) / (double) w * (panStartViewX1 - panStartViewX0);
                    double newX0 = panStartViewX0 + deltaCoord;
                    double newX1 = panStartViewX1 + deltaCoord;
                    double range = newX1 - newX0;

                    if (newX0 < 0) { newX0 = 0; newX1 = range; }
                    if (newX1 > COORD_X_MAX) { newX1 = COORD_X_MAX; newX0 = COORD_X_MAX - range; }

                    viewX0 = Math.max(0, newX0);
                    viewX1 = Math.min(COORD_X_MAX, newX1);
                    repaint();
                    return;
                }

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
                        selectedCoord[1] = y;
                    }
                    fireChangeListeners();
                    repaint();
                }
            }
        });

        // Scroll wheel: zoom
        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = e.getWheelRotation() < 0 ? 0.8 : 1.25;
                zoomAtCursor(e.getX(), getWidth(), factor);
            }
        });
    }

    // ── Public API ──

    public void reset(double[] resetTimes, double[] resetValues) {
        pushUndo();
        envelope.coords.clear();
        if (resetTimes != null && resetTimes.length > 0) {
            for (int i = 0; i < resetTimes.length; i++) {
                int x = (int) (resetTimes[i] * COORD_X_MAX);
                int y = valueToCoord(resetValues[i]);
                envelope.coords.add(new int[]{x, y});
            }
        } else {
            envelope.coords.add(new int[]{0, 500});
            envelope.coords.add(new int[]{COORD_X_MAX, 500});
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
        int numPoints = Math.max(cycles * 10, 20);
        double phaseRad = Math.toRadians(phaseDeg);
        for (int i = 0; i <= numPoints; i++) {
            double t = (double) i / numPoints;
            int x = (int) (t * COORD_X_MAX);
            double val = amplitude * (Math.sin(phaseRad + 2 * Math.PI * cycles * t) + 1) / 2;
            int y = dbMode ? valueToCoord(val) : (int) (500 - val * 500);
            envelope.coords.add(new int[]{x, y});
        }
        syncEnvelopeArrays();
        fireChangeListeners();
        repaint();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        ArrayList<int[]> prev = undoStack.remove(undoStack.size() - 1);
        envelope.coords.clear();
        envelope.coords.addAll(prev);
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

    public void setWaveformData(float[] samples) {
        this.waveformData = samples;
        repaint();
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    private void fireChangeListeners() {
        ChangeEvent evt = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(evt);
    }
}
