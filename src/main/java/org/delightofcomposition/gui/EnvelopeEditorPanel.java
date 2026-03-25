package org.delightofcomposition.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;

import java.awt.Rectangle;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;

import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.envelopes.Envelope;

/**
 * Four stacked envelope editors for density, mix, dynamics, and pitch,
 * with button bars, timbral preview, resize handles, and styled dark theme.
 */
public class EnvelopeEditorPanel extends JPanel implements Scrollable {

    private final SynthParameters params;
    private final Envelope densityEnvelope;
    private final Envelope mixEnvelope;
    private final Envelope dynamicsEnvelope;
    private final Envelope pitchEnvelope;
    private final EnvelopeCanvas densityCanvas;
    private final EnvelopeCanvas mixCanvas;
    private final EnvelopeCanvas dynamicsCanvas;
    private final EnvelopeCanvas pitchCanvas;
    private TimbralPreview timbralPreview;
    private final ToggleSwitch dynamicsToggle;
    private final ToggleSwitch pitchToggle;

    public EnvelopeEditorPanel(SynthParameters params) {
        this.params = params;
        setLayout(new java.awt.GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        densityEnvelope = envelopeToEditable(params.probEnv, false, false);
        mixEnvelope = envelopeToEditable(params.mixEnv, false, false);
        dynamicsEnvelope = envelopeToEditable(params.dynamicsEnv, true, false);
        pitchEnvelope = envelopeToEditable(params.pitchEnv, false, true);

        densityCanvas = new EnvelopeCanvas(densityEnvelope, () -> Theme.ACCENT);
        mixCanvas = new EnvelopeCanvas(mixEnvelope, () -> Theme.SUCCESS);
        dynamicsCanvas = new EnvelopeCanvas(dynamicsEnvelope, () -> Theme.AMBER, true);
        pitchCanvas = new EnvelopeCanvas(pitchEnvelope, () -> Theme.DESTRUCTIVE, false, true);

        // Dynamics toggle
        dynamicsToggle = new ToggleSwitch(params.useDynamicsEnv);
        dynamicsToggle.addChangeListener(e -> {
            params.useDynamicsEnv = dynamicsToggle.isSelected();
            if (timbralPreview != null) timbralPreview.repaint();
        });

        // Pitch toggle
        pitchToggle = new ToggleSwitch(params.usePitchEnv);
        pitchToggle.addChangeListener(e -> {
            params.usePitchEnv = pitchToggle.isSelected();
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
        help.register(pitchCanvas,
                "Controls pitch shift over time. Center line = no change. Up = higher pitch, down = lower. Range: 2 octaves each way.");
        help.register(pitchToggle,
                "Enable or disable the pitch envelope. When off, grains play at their natural spectral frequency.");

        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.fill = java.awt.GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.insets = new java.awt.Insets(4, 4, 4, 4);

        // Row 0: Density (left), Mix (right)
        gbc.gridx = 0; gbc.gridy = 0;
        add(buildEditorCard("Density",
                "Grain density over time. Low = individual notes, High = dense spectrum.",
                densityCanvas, densityEnvelope, null), gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        add(buildEditorCard("Mix",
                "Mix between granular texture (0) and original sample (1).",
                mixCanvas, mixEnvelope, null), gbc);

        // Row 1: Dynamics (left), Pitch (right)
        gbc.gridx = 0; gbc.gridy = 1;
        add(buildEditorCard("Dynamics",
                "Overall output amplitude over time.",
                dynamicsCanvas, dynamicsEnvelope, dynamicsToggle), gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        add(buildEditorCard("Pitch",
                "Pitch shift over time. Center = no change, up = higher, down = lower.",
                pitchCanvas, pitchEnvelope, pitchToggle), gbc);

        // Create timbral preview (displayed in WaveformDisplay, not here)
        timbralPreview = new TimbralPreview(densityEnvelope, mixEnvelope,
                dynamicsEnvelope, () -> dynamicsToggle.isSelected(),
                pitchEnvelope, () -> pitchToggle.isSelected());
        help.register(timbralPreview,
                "Shows how source and synth audio blend over time. Blue = synthesized granular texture, Green = original source audio.");

        // Wire change listeners to update timbral preview
        densityCanvas.addChangeListener(e -> timbralPreview.repaint());
        mixCanvas.addChangeListener(e -> timbralPreview.repaint());
        dynamicsCanvas.addChangeListener(e -> timbralPreview.repaint());
        pitchCanvas.addChangeListener(e -> timbralPreview.repaint());
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

        canvas.setPreferredSize(new Dimension(300, 200));
        editorWrapper.add(canvas, BorderLayout.CENTER);
        card.add(editorWrapper, BorderLayout.CENTER);

        return card;
    }

    // ── Coordinate conversion helpers ──

    private static Envelope envelopeToEditable(Envelope src, boolean dbMode, boolean pitchMode) {
        Envelope env = new Envelope();
        env.type = 1;
        env.coords = new ArrayList<>();

        if (src.times != null && src.times.length > 0) {
            for (int i = 0; i < src.times.length; i++) {
                int x = (int) (src.times[i] * EnvelopeCanvas.COORD_X_MAX);
                int y = convertValueToCoord(src.values[i], dbMode, pitchMode);
                env.coords.add(new int[]{x, y});
            }
        } else {
            int defaultY = pitchMode ? 250 : 500; // pitch center = no change, others = zero
            env.coords.add(new int[]{0, defaultY});
            env.coords.add(new int[]{EnvelopeCanvas.COORD_X_MAX, defaultY});
        }

        env.times = new double[env.coords.size()];
        env.values = new double[env.coords.size()];
        for (int i = 0; i < env.coords.size(); i++) {
            int[] coord = env.coords.get(i);
            env.times[i] = coord[0] / (double) EnvelopeCanvas.COORD_X_MAX;
            env.values[i] = convertCoordToValue(coord[1], dbMode, pitchMode);
        }
        return env;
    }

    private static int convertValueToCoord(double value, boolean dbMode, boolean pitchMode) {
        if (pitchMode) {
            return ratioToCoord(value);
        } else if (dbMode) {
            return gainToCoord(value);
        } else {
            return (int) (500 - value * 500);
        }
    }

    private static double convertCoordToValue(int coordY, boolean dbMode, boolean pitchMode) {
        if (pitchMode) {
            return coordToRatio(coordY);
        } else if (dbMode) {
            return coordToGain(coordY);
        } else {
            return (500 - coordY) / 500.0;
        }
    }

    // dB conversion helpers
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

    // Pitch conversion helpers (ratio ↔ coordY via semitones)
    private static double coordToRatio(int coordY) {
        double semi = EnvelopeCanvas.PITCH_SEMI_MAX - (coordY / 500.0) * EnvelopeCanvas.PITCH_SEMI_RANGE;
        return Math.pow(2, semi / 12.0);
    }

    private static int ratioToCoord(double ratio) {
        if (ratio <= 0) return 500;
        double semi = 12.0 * Math.log(ratio) / Math.log(2);
        semi = Math.max(-EnvelopeCanvas.PITCH_SEMI_MAX, Math.min(EnvelopeCanvas.PITCH_SEMI_MAX, semi));
        return (int) ((EnvelopeCanvas.PITCH_SEMI_MAX - semi) / EnvelopeCanvas.PITCH_SEMI_RANGE * 500);
    }

    // ── Sync with SynthParameters ──

    public TimbralPreview getTimbralPreview() {
        return timbralPreview;
    }

    public void setWaveformData(float[] samples) {
        densityCanvas.setWaveformData(samples);
        mixCanvas.setWaveformData(samples);
        dynamicsCanvas.setWaveformData(samples);
        pitchCanvas.setWaveformData(samples);
    }

    public void syncToParams() {
        params.probEnv = cloneWithData(densityEnvelope, false, false);
        params.mixEnv = cloneWithData(mixEnvelope, false, false);
        params.dynamicsEnv = cloneWithData(dynamicsEnvelope, true, false);
        params.useDynamicsEnv = dynamicsToggle.isSelected();
        params.pitchEnv = cloneWithData(pitchEnvelope, false, true);
        params.usePitchEnv = pitchToggle.isSelected();
    }

    public void syncFromParams() {
        copyEnvelopeData(params.probEnv, densityEnvelope, false, false);
        copyEnvelopeData(params.mixEnv, mixEnvelope, false, false);
        copyEnvelopeData(params.dynamicsEnv, dynamicsEnvelope, true, false);
        dynamicsToggle.setSelected(params.useDynamicsEnv);
        copyEnvelopeData(params.pitchEnv, pitchEnvelope, false, true);
        pitchToggle.setSelected(params.usePitchEnv);
        timbralPreview.repaint();
    }

    private static Envelope cloneWithData(Envelope src, boolean dbMode, boolean pitchMode) {
        double[] times = new double[src.coords.size()];
        double[] values = new double[src.coords.size()];
        for (int i = 0; i < src.coords.size(); i++) {
            int[] coord = src.coords.get(i);
            times[i] = coord[0] / (double) EnvelopeCanvas.COORD_X_MAX;
            values[i] = convertCoordToValue(coord[1], dbMode, pitchMode);
        }
        return new Envelope(times, values);
    }

    private static void copyEnvelopeData(Envelope src, Envelope dst, boolean dbMode, boolean pitchMode) {
        dst.coords = new ArrayList<>();
        if (src.times != null) {
            for (int i = 0; i < src.times.length; i++) {
                int x = (int) (src.times[i] * EnvelopeCanvas.COORD_X_MAX);
                int y = convertValueToCoord(src.values[i], dbMode, pitchMode);
                dst.coords.add(new int[]{x, y});
            }
        }
        dst.times = new double[dst.coords.size()];
        dst.values = new double[dst.coords.size()];
        for (int i = 0; i < dst.coords.size(); i++) {
            int[] coord = dst.coords.get(i);
            dst.times[i] = coord[0] / (double) EnvelopeCanvas.COORD_X_MAX;
            dst.values[i] = convertCoordToValue(coord[1], dbMode, pitchMode);
        }
    }

    // ── Scrollable ──

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return visibleRect.height - 16;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
