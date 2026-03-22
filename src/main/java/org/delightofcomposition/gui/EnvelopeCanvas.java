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
 * filled area under curve, grid, snap crosshairs, and undo support.
 */
public class EnvelopeCanvas extends JComponent {

    private static final int NODE_SIZE = 12;
    private static final int NODE_HOVER_SIZE = 14;
    private static final int HIT_RADIUS = 8;
    private static final int MAX_UNDO = 30;
    private static final int QUANT_FACT = 10;

    // dB mode constants: y-axis spans +6 dB (top) to -60 dB (bottom)
    static final double DB_MAX = 6.0;
    static final double DB_RANGE = 66.0; // 6 - (-60)

    private final Envelope envelope;
    private final Supplier<Color> curveColorFn;
    final boolean dbMode;

    private int hoveredNodeIndex = -1;
    private int selectedCoordIndex = -1;
    private int[] selectedCoord = null;
    private int dragStartX, dragStartY;

    private final List<ArrayList<int[]>> undoStack = new ArrayList<>();
    private final List<ChangeListener> listeners = new ArrayList<>();

    // Default coords for reset
    private double[] defaultTimes;
    private double[] defaultValues;

    public EnvelopeCanvas(Envelope envelope, Supplier<Color> curveColorFn) {
        this(envelope, curveColorFn, false);
    }

    public EnvelopeCanvas(Envelope envelope, Supplier<Color> curveColorFn, boolean dbMode) {
        this.envelope = envelope;
        this.curveColorFn = curveColorFn;
        this.dbMode = dbMode;

        // Store defaults for reset
        if (envelope.times != null) {
            this.defaultTimes = envelope.times.clone();
            this.defaultValues = envelope.values.clone();
        }

        setOpaque(false);
        setPreferredSize(new Dimension(600, 200));

        setupMouseListeners();

        // Repaint timer (50ms matches existing behavior)
        Timer timer = new Timer(50, e -> repaint());
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int w = getWidth();
        int h = getHeight();

        // Background
        g2.setColor(Theme.BG_INPUT);
        g2.fillRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);

        // Grid: horizontal lines
        g2.setColor(Theme.BORDER_SUBTLE);
        g2.setStroke(new BasicStroke(1));
        if (dbMode) {
            // dB grid lines at 0, -6, -12, -24, -48 dB
            g2.setFont(Theme.FONT_SMALL);
            FontMetrics fm = g2.getFontMetrics();
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
        } else {
            for (int pct = 25; pct <= 75; pct += 25) {
                int gy = h * pct / 100;
                g2.drawLine(0, gy, w, gy);
            }
        }
        // Grid: vertical every 10 units (10% of envelope)
        for (int x = 10; x < 100; x += 10) {
            int gx = x * w / 100;
            g2.drawLine(gx, 0, gx, h);
        }

        if (envelope.coords == null || envelope.coords.size() < 2) {
            g2.dispose();
            return;
        }

        // Build path
        GeneralPath curvePath = new GeneralPath();
        GeneralPath fillPath = new GeneralPath();

        int firstX = coordToScreenX(envelope.coords.get(0)[0], w);
        int firstY = coordToScreenY(envelope.coords.get(0)[1], h);
        curvePath.moveTo(firstX, firstY);
        fillPath.moveTo(firstX, h);
        fillPath.lineTo(firstX, firstY);

        for (int i = 1; i < envelope.coords.size(); i++) {
            int sx = coordToScreenX(envelope.coords.get(i)[0], w);
            int sy = coordToScreenY(envelope.coords.get(i)[1], h);
            curvePath.lineTo(sx, sy);
            fillPath.lineTo(sx, sy);
        }

        int lastX = coordToScreenX(envelope.coords.get(envelope.coords.size() - 1)[0], w);
        fillPath.lineTo(lastX, h);
        fillPath.closePath();

        // Resolve current curve color from supplier
        Color curveColor = curveColorFn.get();
        Color fillColor = new Color(curveColor.getRed(), curveColor.getGreen(),
                curveColor.getBlue(), 30);

        // Fill area under curve
        g2.setColor(fillColor);
        g2.fill(fillPath);

        // Draw curve line (2px)
        g2.setColor(curveColor);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(curvePath);

        // Draw nodes
        for (int i = 0; i < envelope.coords.size(); i++) {
            int[] coord = envelope.coords.get(i);
            int sx = coordToScreenX(coord[0], w);
            int sy = coordToScreenY(coord[1], h);

            boolean isHovered = (i == hoveredNodeIndex);
            int size = isHovered ? NODE_HOVER_SIZE : NODE_SIZE;

            // White fill
            g2.setColor(Color.WHITE);
            g2.fillOval(sx - size / 2, sy - size / 2, size, size);

            // Border
            g2.setColor(isHovered ? Theme.ACCENT_HOVER : curveColor);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(sx - size / 2, sy - size / 2, size - 1, size - 1);
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

        // Update times/values arrays
        syncEnvelopeArrays();

        g2.dispose();
    }

    private int coordToScreenX(int coordX, int panelWidth) {
        return (int) (coordX * panelWidth / 100.0);
    }

    private int coordToScreenY(int coordY, int panelHeight) {
        return (int) (coordY * panelHeight / 500.0);
    }

    private int screenToCoordX(int screenX, int panelWidth) {
        return (int) Math.round((double) screenX / panelWidth * 100);
    }

    private int screenToCoordY(int screenY, int panelHeight) {
        return Math.max(0, Math.min(500, (int) (screenY * 500.0 / panelHeight)));
    }

    private void syncEnvelopeArrays() {
        envelope.times = new double[envelope.coords.size()];
        envelope.values = new double[envelope.coords.size()];
        for (int i = 0; i < envelope.coords.size(); i++) {
            int[] coord = envelope.coords.get(i);
            envelope.times[i] = coord[0] / 100.0;
            envelope.values[i] = coordToValue(coord[1]);
        }
    }

    /** Convert coord Y (0-500) to linear value. In dB mode, returns linear gain. */
    double coordToValue(int coordY) {
        if (!dbMode) {
            return (500 - coordY) / 500.0;
        }
        double dB = DB_MAX - (coordY / 500.0) * DB_RANGE;
        return Math.pow(10, dB / 20.0);
    }

    /** Convert linear value to coord Y (0-500). In dB mode, expects linear gain. */
    int valueToCoord(double value) {
        if (!dbMode) {
            return (int) (500 - value * 500);
        }
        if (value <= 0) return 500;
        double dB = 20 * Math.log10(value);
        dB = Math.max(DB_MAX - DB_RANGE, Math.min(DB_MAX, dB));
        return (int) ((DB_MAX - dB) / DB_RANGE * 500);
    }

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

    private void setupMouseListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int w = getWidth();
                int h = getHeight();
                int x = screenToCoordX(e.getX(), w);
                int y = screenToCoordY(e.getY(), h);

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
                    } else {
                        // Normal click: add/move point
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
                        envelope.coords.get(envelope.coords.size() - 1)[0] = 100;
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
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());

                if (oldHovered != hoveredNodeIndex) {
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (envelope.type == 1 && selectedCoord != null && selectedCoordIndex >= 0) {
                    int w = getWidth();
                    int h = getHeight();
                    int qx = screenToCoordX(e.getX(), w);
                    int y = screenToCoordY(e.getY(), h);

                    boolean isAlt = (e.getModifiersEx() & MouseEvent.ALT_DOWN_MASK) != 0;

                    // Keep x within bounds of neighbors; pin first/last to edges
                    int sidx = selectedCoordIndex;
                    if (sidx == 0) {
                        selectedCoord[0] = 0;
                    } else if (sidx == envelope.coords.size() - 1) {
                        selectedCoord[0] = 100;
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
    }

    // ── Public API ──

    public void reset(double[] resetTimes, double[] resetValues) {
        pushUndo();
        envelope.coords.clear();
        if (resetTimes != null && resetTimes.length > 0) {
            for (int i = 0; i < resetTimes.length; i++) {
                int x = (int) (resetTimes[i] * 100);
                int y = valueToCoord(resetValues[i]);
                envelope.coords.add(new int[]{x, y});
            }
        } else {
            envelope.coords.add(new int[]{0, 500});
            envelope.coords.add(new int[]{100, 500});
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
            int x = (int) (t * 100);
            // val is 0-1 canvas position; convert through value domain for dB correctness
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

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    private void fireChangeListeners() {
        ChangeEvent evt = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(evt);
    }
}
