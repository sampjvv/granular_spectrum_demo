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
        JTextField f = new JTextField(formatValue());
        f.setFont(Theme.FONT_VALUE);
        f.setHorizontalAlignment(JTextField.CENTER);
        f.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER, Theme.RADIUS_SM, new java.awt.Insets(0, 0, 0, 0)),
                javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6)));

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
        add(Box.createHorizontalStrut(4));
        add(valueField);
        add(Box.createHorizontalStrut(4));
        add(plus);

        valueField.setMaximumSize(new Dimension(Integer.MAX_VALUE, BTN_SIZE));
        valueField.setPreferredSize(new Dimension(80, BTN_SIZE));
    }

    private String formatValue() {
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
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.RADIUS_SM, Theme.RADIUS_SM);

            // Draw glyph
            g2.setColor(Theme.FG);
            g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int half = 6;

            // Horizontal line (both minus and plus)
            g2.drawLine(cx - half, cy, cx + half, cy);
            if (isPlus) {
                g2.drawLine(cx, cy - half, cx, cy + half);
            }

            g2.dispose();
        }
    }
}
