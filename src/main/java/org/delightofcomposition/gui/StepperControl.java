package org.delightofcomposition.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Stepper control: [ − ] value [ + ]
 * Two 36x36 rounded-square buttons flanking a centered editable text field.
 * Supports long-press auto-repeat.
 */
public class StepperControl extends JPanel {

    private static final int BTN_SIZE = 36;
    private static final int LONG_PRESS_DELAY = 400;
    private static final int REPEAT_DELAY = 80;

    private double value;
    private final double min;
    private final double max;
    private final double step;
    private final String format;
    private final boolean intMode;
    private final JTextField valueField;
    private final List<ChangeListener> listeners = new ArrayList<>();
    private String[] labels; // optional named labels for discrete values

    /** Double constructor with format string. */
    public StepperControl(double val, double min, double max, double step, String format) {
        this.value = val;
        this.min = min;
        this.max = max;
        this.step = step;
        this.format = format;
        this.intMode = false;
        this.valueField = createValueField();
        buildUI();
    }

    /** Integer constructor. */
    public StepperControl(int val, int min, int max, int step) {
        this.value = val;
        this.min = min;
        this.max = max;
        this.step = step;
        this.format = "%d";
        this.intMode = true;
        this.valueField = createValueField();
        buildUI();
    }

    private JTextField createValueField() {
        JTextField f = new JTextField(formatValue()) {
            @Override
            protected void paintComponent(Graphics g) {
                if (Theme.isSynthwave()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    SynthwavePainter.fillPanel(g2, 0, 0, getWidth(), getHeight(),
                            Theme.SW_BG_DEEP, Theme.SW_PURPLE);
                    SynthwavePainter.paintBevel(g2, 0, 0, getWidth(), getHeight(), false);
                    g2.dispose();
                    // Paint text clipped to pixel corners, without background fill
                    java.awt.Shape origClip = g.getClip();
                    java.awt.Polygon shape = SynthwavePainter.pixelCornerShape(
                            0, 0, getWidth(), getHeight(),
                            SynthwavePainter.chooseSizeForDimensions(getWidth(), getHeight()));
                    g.setClip(shape);
                    boolean wasOpaque = isOpaque();
                    setOpaque(false);
                    super.paintComponent(g);
                    setOpaque(wasOpaque);
                    ((Graphics2D) g).setClip(origClip);
                } else {
                    super.paintComponent(g);
                }
            }
        };
        // Use theme-aware border (null/-1 = read Theme.BORDER/RADIUS_SM dynamically)
        f.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(null, -1, new java.awt.Insets(0, 0, 0, 0)),
                javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        Theme.tagFont(f, "value");
        Theme.tagFg(f, "fg");
        f.setHorizontalAlignment(JTextField.CENTER);

        f.addActionListener(e -> parseField());
        f.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                parseField();
            }
        });
        return f;
    }

    private void parseField() {
        try {
            double parsed = Double.parseDouble(valueField.getText().replaceAll("[^\\d.\\-]", ""));
            setValue(parsed);
        } catch (NumberFormatException ex) {
            valueField.setText(formatValue());
        }
    }

    private void buildUI() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(false);

        StepperButton minus = new StepperButton(false);
        StepperButton plus = new StepperButton(true);

        add(minus);
        add(Box.createHorizontalStrut(6));
        add(valueField);
        add(Box.createHorizontalStrut(6));
        add(plus);

        valueField.setMaximumSize(new Dimension(Integer.MAX_VALUE, BTN_SIZE));
        valueField.setPreferredSize(new Dimension(80, BTN_SIZE));
    }

    /** Set named labels for discrete values (index 0 = min value). */
    public void setLabels(String[] labels) {
        this.labels = labels;
        valueField.setText(formatValue());
    }

    private String formatValue() {
        if (labels != null) {
            int idx = (int) value - (int) min;
            if (idx >= 0 && idx < labels.length) return labels[idx];
        }
        if (intMode) return String.format(format, (int) value);
        return String.format(format, value);
    }

    private void increment(int direction) {
        setValue(value + direction * step);
    }

    public double getDoubleValue() {
        return value;
    }

    public int getIntValue() {
        return (int) value;
    }

    public void setValue(double v) {
        v = Math.max(min, Math.min(max, v));
        if (intMode) v = Math.round(v);
        if (v != value) {
            value = v;
            valueField.setText(formatValue());
            fireChangeListeners();
        } else {
            valueField.setText(formatValue());
        }
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    private void fireChangeListeners() {
        ChangeEvent evt = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(evt);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, BTN_SIZE);
    }

    /**
     * Custom painted − or + button with hover/pressed states and long-press repeat.
     */
    private class StepperButton extends JComponent {
        private final boolean isPlus;
        private boolean hover;
        private boolean pressed;
        private Timer repeatTimer;

        StepperButton(boolean isPlus) {
            this.isPlus = isPlus;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(BTN_SIZE, BTN_SIZE));
            setMinimumSize(new Dimension(BTN_SIZE, BTN_SIZE));
            setMaximumSize(new Dimension(BTN_SIZE, BTN_SIZE));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    pressed = false;
                    stopRepeat();
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    pressed = true;
                    repaint();
                    increment(isPlus ? 1 : -1);
                    startRepeat();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    pressed = false;
                    stopRepeat();
                    repaint();
                }
            });
        }

        private void startRepeat() {
            repeatTimer = new Timer(LONG_PRESS_DELAY, e -> {
                repeatTimer.setDelay(REPEAT_DELAY);
                increment(isPlus ? 1 : -1);
            });
            repeatTimer.start();
        }

        private void stopRepeat() {
            if (repeatTimer != null) {
                repeatTimer.stop();
                repeatTimer = null;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color bg = pressed ? Theme.ZINC_600 : hover ? Theme.ZINC_700 : Theme.BG_MUTED;

            if (Theme.isSynthwave()) {
                Color border = hover ? Theme.SW_LAVENDER : Theme.SW_PURPLE;
                SynthwavePainter.fillPanel(g2, 0, 0, getWidth(), getHeight(), bg, border);
                SynthwavePainter.paintBevel(g2, 0, 0, getWidth(), getHeight(), !pressed);
                if (hover) {
                    SynthwavePainter.paintGlow(g2, 0, 0, getWidth(), getHeight(), Theme.SW_CYAN, 2);
                }
            } else {
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.RADIUS_SM, Theme.RADIUS_SM);
            }

            // Draw glyph
            g2.setColor(Theme.FG);
            g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int half = 6;

            g2.drawLine(cx - half, cy, cx + half, cy);
            if (isPlus) {
                g2.drawLine(cx, cy - half, cx, cy + half);
            }

            g2.dispose();
        }
    }
}
