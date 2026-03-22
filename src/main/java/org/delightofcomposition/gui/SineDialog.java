package org.delightofcomposition.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.GeneralPath;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Modal dialog for generating sine wave envelope shapes.
 * Provides controls for cycles, amplitude, and phase with live preview.
 */
public class SineDialog extends JDialog {

    private final StepperControl cyclesStepper;
    private final LabeledSlider amplitudeSlider;
    private final LabeledSlider phaseSlider;
    private final PreviewCanvas previewCanvas;

    private boolean applied = false;

    public SineDialog(Window owner) {
        super(owner, "Sine Wave Generator", ModalityType.APPLICATION_MODAL);
        setSize(360, 400);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG_CARD);
        setLayout(new BorderLayout(0, 8));

        // Controls panel
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setOpaque(false);
        controls.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        // Cycles
        JPanel cyclesRow = Theme.toggleRow("Cycles", null);
        cyclesStepper = new StepperControl(3, 1, 20, 1);
        cyclesRow.removeAll();
        cyclesRow.setLayout(new BorderLayout());
        cyclesRow.add(Theme.paramLabel("Cycles"), BorderLayout.WEST);
        cyclesRow.add(cyclesStepper, BorderLayout.CENTER);
        cyclesRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        controls.add(cyclesRow);
        controls.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        // Amplitude
        amplitudeSlider = new LabeledSlider("Amplitude", 0, 100, 100, v -> v + "%");
        controls.add(amplitudeSlider);
        controls.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        // Phase
        phaseSlider = new LabeledSlider("Phase", 0, 360, 0, v -> v + "\u00B0");
        controls.add(phaseSlider);
        controls.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        add(controls, BorderLayout.NORTH);

        // Preview
        previewCanvas = new PreviewCanvas();
        previewCanvas.setPreferredSize(new Dimension(300, 80));
        JPanel previewWrapper = new JPanel(new BorderLayout());
        previewWrapper.setOpaque(false);
        previewWrapper.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        previewWrapper.add(previewCanvas, BorderLayout.CENTER);
        add(previewWrapper, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttonPanel.setOpaque(false);

        JButton cancelBtn = Theme.ghostButton("Cancel");
        cancelBtn.setPreferredSize(new Dimension(80, 34));
        cancelBtn.addActionListener(e -> {
            applied = false;
            dispose();
        });
        buttonPanel.add(cancelBtn);

        JButton applyBtn = Theme.primaryButton("Apply");
        applyBtn.setPreferredSize(new Dimension(80, 34));
        applyBtn.addActionListener(e -> {
            applied = true;
            dispose();
        });
        buttonPanel.add(applyBtn);

        add(buttonPanel, BorderLayout.SOUTH);

        // Wire up live preview updates
        cyclesStepper.addChangeListener(e -> previewCanvas.repaint());
        amplitudeSlider.addChangeListener(e -> previewCanvas.repaint());
        phaseSlider.addChangeListener(e -> previewCanvas.repaint());
    }

    public boolean showDialog() {
        setVisible(true);
        return applied;
    }

    public int getCycles() {
        return cyclesStepper.getIntValue();
    }

    public double getAmplitude() {
        return amplitudeSlider.getValue() / 100.0;
    }

    public double getPhaseDeg() {
        return phaseSlider.getValue();
    }

    /**
     * Live preview canvas showing the sine wave shape.
     */
    private class PreviewCanvas extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Background
            g2.setColor(Theme.BG_INPUT);
            g2.fillRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
            g2.setColor(Theme.BORDER_SUBTLE);
            g2.drawRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);

            // Center line
            g2.setColor(Theme.BORDER_SUBTLE);
            g2.drawLine(0, h / 2, w, h / 2);

            // Sine preview
            int cycles = cyclesStepper.getIntValue();
            double amplitude = amplitudeSlider.getValue() / 100.0;
            double phaseRad = Math.toRadians(phaseSlider.getValue());

            GeneralPath path = new GeneralPath();
            for (int px = 0; px < w; px++) {
                double t = (double) px / (w - 1);
                double val = amplitude * (Math.sin(phaseRad + 2 * Math.PI * cycles * t) + 1) / 2;
                int py = (int) (h - 1 - val * (h - 2));
                if (px == 0) {
                    path.moveTo(px, py);
                } else {
                    path.lineTo(px, py);
                }
            }

            // Fill
            GeneralPath fillPath = new GeneralPath(path);
            fillPath.lineTo(w - 1, h - 1);
            fillPath.lineTo(0, h - 1);
            fillPath.closePath();
            g2.setColor(Theme.ACCENT_FILL);
            g2.fill(fillPath);

            // Line
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);

            g2.dispose();
        }
    }
}
