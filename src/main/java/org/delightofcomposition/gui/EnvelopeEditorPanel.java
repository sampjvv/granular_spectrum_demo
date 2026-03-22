package org.delightofcomposition.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.envelopes.Envelope;

/**
 * Three stacked envelope editors for density, mix, and dynamics, with button bars,
 * timbral preview, and styled dark theme.
 */
public class EnvelopeEditorPanel extends JPanel {

    private final SynthParameters params;
    private final Envelope densityEnvelope;
    private final Envelope mixEnvelope;
    private final Envelope dynamicsEnvelope;
    private final EnvelopeCanvas densityCanvas;
    private final EnvelopeCanvas mixCanvas;
    private final EnvelopeCanvas dynamicsCanvas;
    private TimbralPreview timbralPreview;
    private final ToggleSwitch dynamicsToggle;

    public EnvelopeEditorPanel(SynthParameters params) {
        this.params = params;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        densityEnvelope = envelopeToEditable(params.probEnv, false);
        mixEnvelope = envelopeToEditable(params.mixEnv, false);
        dynamicsEnvelope = envelopeToEditable(params.dynamicsEnv, true);

        densityCanvas = new EnvelopeCanvas(densityEnvelope, () -> Theme.ACCENT);
        mixCanvas = new EnvelopeCanvas(mixEnvelope, () -> Theme.SUCCESS);
        dynamicsCanvas = new EnvelopeCanvas(dynamicsEnvelope, () -> Theme.AMBER, true);

        // Dynamics toggle
        dynamicsToggle = new ToggleSwitch(params.useDynamicsEnv);
        dynamicsToggle.addChangeListener(e -> {
            params.useDynamicsEnv = dynamicsToggle.isSelected();
            if (timbralPreview != null) timbralPreview.repaint();
        });

        // Register help texts
        HelpManager help = HelpManager.getInstance();
        help.register(densityCanvas,
                "Controls grain density over time. Click to add nodes, Ctrl-click to delete, Alt-click for flat lines. Drag nodes to reshape.");
        help.register(mixCanvas,
                "Controls the mix between granular texture (low) and original sample (high). Same editing controls as density envelope.");
        help.register(dynamicsCanvas,
                "Controls overall output volume over time. Shape this to create fade-ins, fade-outs, and dynamic swells.");
        help.register(dynamicsToggle,
                "Enable or disable the dynamics envelope. When off, output plays at full volume.");

        add(buildEditorCard("Density Envelope",
                "Controls grain density over time. Low = individual notes, High = dense spectrum.",
                densityCanvas, densityEnvelope, null));
        add(Box.createVerticalStrut(8));
        add(buildEditorCard("Mix Envelope",
                "Controls mix between granular texture (0) and original sample (1).",
                mixCanvas, mixEnvelope, null));
        add(Box.createVerticalStrut(8));
        add(buildEditorCard("Dynamics Envelope",
                "Controls overall output amplitude over time.",
                dynamicsCanvas, dynamicsEnvelope, dynamicsToggle));
        add(Box.createVerticalStrut(8));

        // Timbral preview
        timbralPreview = new TimbralPreview(densityEnvelope, mixEnvelope,
                dynamicsEnvelope, () -> dynamicsToggle.isSelected());
        help.register(timbralPreview,
                "Shows how source and synth audio blend over time. Blue = synthesized granular texture, Green = original source audio. Dot density = dynamics, height = density.");

        JPanel previewCard = new JPanel(new BorderLayout(0, 4)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_LG, Theme.RADIUS_LG);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_LG, Theme.RADIUS_LG);
                g2.dispose();
            }
        };
        previewCard.setOpaque(false);
        previewCard.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        previewCard.add(Theme.sectionLabel("Timbral Blend"), BorderLayout.NORTH);
        previewCard.add(timbralPreview, BorderLayout.CENTER);
        add(previewCard);

        add(Box.createVerticalGlue());

        // Wire change listeners to update timbral preview
        densityCanvas.addChangeListener(e -> timbralPreview.repaint());
        mixCanvas.addChangeListener(e -> timbralPreview.repaint());
        dynamicsCanvas.addChangeListener(e -> timbralPreview.repaint());
    }

    private JPanel buildEditorCard(String title, String helpText, EnvelopeCanvas canvas,
                                   Envelope envelope, JComponent titleExtra) {
        JPanel card = new JPanel(new BorderLayout(0, 4)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_LG, Theme.RADIUS_LG);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_LG, Theme.RADIUS_LG);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // Header with title + buttons
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleArea = new JPanel(new BorderLayout());
        titleArea.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        titleRow.add(Theme.sectionLabel(title));
        if (titleExtra != null) {
            titleRow.add(titleExtra);
        }
        titleArea.add(titleRow, BorderLayout.NORTH);

        JLabel helpLabel = new JLabel(helpText);
        helpLabel.setFont(Theme.FONT_SMALL);
        helpLabel.setForeground(Theme.FG_DIM);
        titleArea.add(helpLabel, BorderLayout.SOUTH);
        header.add(titleArea, BorderLayout.CENTER);

        // Button bar
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonBar.setOpaque(false);

        JButton resetBtn = Theme.ghostButton("Reset");
        resetBtn.setFont(Theme.FONT_SMALL);
        resetBtn.setPreferredSize(new Dimension(60, 26));
        resetBtn.addActionListener(e -> canvas.reset());

        JButton undoBtn = Theme.ghostButton("Undo");
        undoBtn.setFont(Theme.FONT_SMALL);
        undoBtn.setPreferredSize(new Dimension(60, 26));
        undoBtn.addActionListener(e -> canvas.undo());

        HelpManager help = HelpManager.getInstance();
        help.register(resetBtn, "Reset this envelope to its default shape.");
        help.register(undoBtn, "Undo the last edit to this envelope.");

        buttonBar.add(resetBtn);
        buttonBar.add(undoBtn);
        header.add(buttonBar, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        // Canvas wrapper
        JPanel editorWrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_INPUT);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS, Theme.RADIUS);
                g2.setColor(Theme.BORDER_SUBTLE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS, Theme.RADIUS);
                g2.dispose();
            }
        };
        editorWrapper.setOpaque(false);
        editorWrapper.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        canvas.setPreferredSize(new Dimension(600, 200));
        editorWrapper.add(canvas, BorderLayout.CENTER);
        card.add(editorWrapper, BorderLayout.CENTER);

        return card;
    }

    private static Envelope envelopeToEditable(Envelope src, boolean dbMode) {
        Envelope env = new Envelope();
        env.type = 1;
        env.coords = new ArrayList<>();

        if (src.times != null && src.times.length > 0) {
            for (int i = 0; i < src.times.length; i++) {
                int x = (int) (src.times[i] * 100);
                int y = dbMode ? gainToCoord(src.values[i]) : (int) (500 - src.values[i] * 500);
                env.coords.add(new int[]{x, y});
            }
        } else {
            env.coords.add(new int[]{0, 500});
            env.coords.add(new int[]{100, 500});
        }

        env.times = new double[env.coords.size()];
        env.values = new double[env.coords.size()];
        for (int i = 0; i < env.coords.size(); i++) {
            int[] coord = env.coords.get(i);
            env.times[i] = coord[0] / 100.0;
            env.values[i] = dbMode ? coordToGain(coord[1]) : (500 - coord[1]) / 500.0;
        }
        return env;
    }

    // dB conversion helpers (mirror EnvelopeCanvas logic)
    private static double coordToGain(int coordY) {
        double dB = EnvelopeCanvas.DB_MAX - (coordY / 500.0) * EnvelopeCanvas.DB_RANGE;
        return Math.pow(10, dB / 20.0);
    }

    private static int gainToCoord(double gain) {
        if (gain <= 0) return 500;
        double dB = 20 * Math.log10(gain);
        dB = Math.max(EnvelopeCanvas.DB_MAX - EnvelopeCanvas.DB_RANGE,
                Math.min(EnvelopeCanvas.DB_MAX, dB));
        return (int) ((EnvelopeCanvas.DB_MAX - dB) / EnvelopeCanvas.DB_RANGE * 500);
    }

    public void syncToParams() {
        params.probEnv = cloneWithData(densityEnvelope, false);
        params.mixEnv = cloneWithData(mixEnvelope, false);
        params.dynamicsEnv = cloneWithData(dynamicsEnvelope, true);
        params.useDynamicsEnv = dynamicsToggle.isSelected();
    }

    public void syncFromParams() {
        copyEnvelopeData(params.probEnv, densityEnvelope, false);
        copyEnvelopeData(params.mixEnv, mixEnvelope, false);
        copyEnvelopeData(params.dynamicsEnv, dynamicsEnvelope, true);
        dynamicsToggle.setSelected(params.useDynamicsEnv);
        timbralPreview.repaint();
    }

    private static Envelope cloneWithData(Envelope src, boolean dbMode) {
        double[] times = new double[src.coords.size()];
        double[] values = new double[src.coords.size()];
        for (int i = 0; i < src.coords.size(); i++) {
            int[] coord = src.coords.get(i);
            times[i] = coord[0] / 100.0;
            values[i] = dbMode ? coordToGain(coord[1]) : (500 - coord[1]) / 500.0;
        }
        return new Envelope(times, values);
    }

    private static void copyEnvelopeData(Envelope src, Envelope dst, boolean dbMode) {
        dst.coords = new ArrayList<>();
        if (src.times != null) {
            for (int i = 0; i < src.times.length; i++) {
                int x = (int) (src.times[i] * 100);
                int y = dbMode ? gainToCoord(src.values[i]) : (int) (500 - src.values[i] * 500);
                dst.coords.add(new int[]{x, y});
            }
        }
        dst.times = new double[dst.coords.size()];
        dst.values = new double[dst.coords.size()];
        for (int i = 0; i < dst.coords.size(); i++) {
            int[] coord = dst.coords.get(i);
            dst.times[i] = coord[0] / 100.0;
            dst.values[i] = dbMode ? coordToGain(coord[1]) : (500 - coord[1]) / 500.0;
        }
    }
}
