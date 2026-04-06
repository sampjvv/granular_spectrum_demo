package org.delightofcomposition.gui;

import java.awt.BorderLayout;
import java.awt.Color;
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

import com.sptc.uilab.papermin.PmButton;
import com.sptc.uilab.papermin.PmCard;
import com.sptc.uilab.tokens.PaperMinimalistTokens;

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
    private JPanel densityCard, mixCard, dynamicsCard, pitchCard;
    private boolean stacked = false;

    public EnvelopeEditorPanel(SynthParameters params) {
        this.params = params;
        setLayout(new java.awt.GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        densityEnvelope = envelopeToEditable(params.probEnv, false, false);
        mixEnvelope = envelopeToEditable(params.mixEnv, false, false);
        dynamicsEnvelope = envelopeToEditable(params.dynamicsEnv, true, false);
        pitchEnvelope = envelopeToEditable(params.pitchEnv, false, true);

        densityCanvas = new EnvelopeCanvas(densityEnvelope,
                () -> Theme.isPaper() ? new Color(0x5A, 0x7A, 0x9A) : Theme.ACCENT);
        mixCanvas = new EnvelopeCanvas(mixEnvelope,
                () -> Theme.isPaper() ? new Color(0x5A, 0x8A, 0x6A) : Theme.SUCCESS);
        dynamicsCanvas = new EnvelopeCanvas(dynamicsEnvelope,
                () -> Theme.isPaper() ? new Color(0xD4, 0x85, 0x4A) : Theme.AMBER, true);
        pitchCanvas = new EnvelopeCanvas(pitchEnvelope,
                () -> Theme.isSynthwave() ? Theme.SW_GREEN
                    : Theme.isPaper() ? new Color(0x7A, 0x6A, 0x8A)
                    : Theme.DESTRUCTIVE, false, true);

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

        buildLayout();
    }

    private void buildLayout() {
        // Clear accumulated canvas listeners from previous builds
        densityCanvas.clearChangeListeners();
        mixCanvas.clearChangeListeners();
        dynamicsCanvas.clearChangeListeners();
        pitchCanvas.clearChangeListeners();

        // Register help texts
        HelpManager help = HelpManager.getInstance();
        help.register(densityCanvas,
                "Ctrl-click to add/remove nodes. Drag nodes to move them. Drag a line segment to move both endpoints. Shift-drag a segment to curve it. Alt-click for flat lines.");
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

        // Build cards (store references for responsive relayout)
        densityCard = buildEditorCard("Density",
                "Grain density over time. Low = individual notes, High = dense spectrum.",
                densityCanvas, densityEnvelope, null);
        densityCard.setMinimumSize(new Dimension(350, 180));

        mixCard = buildEditorCard("Mix",
                "Mix between granular texture (0) and original sample (1).",
                mixCanvas, mixEnvelope, null);
        mixCard.setMinimumSize(new Dimension(350, 180));

        dynamicsCard = buildEditorCard("Dynamics",
                "Overall output amplitude over time.",
                dynamicsCanvas, dynamicsEnvelope, dynamicsToggle);
        // Add dynamics-specific controls
        JPanel dynOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        dynOptions.setOpaque(false);

        // Curve mode button
        JButton curveBtn = Theme.ghostButton(params.dynamicsExponential ? "Exponential" : "Linear");
        Theme.tagFont(curveBtn, "small");
        curveBtn.setPreferredSize(new Dimension(90, 24));

        // Exponent factor slider (hidden until exponential mode)
        LabeledSlider exponentSlider = new LabeledSlider("Factor", 1, 2000,
                (int) (params.dramaticFactor * 100),
                v -> String.format("%.2f", v / 100.0));
        exponentSlider.setPreferredSize(new Dimension(180, exponentSlider.getPreferredSize().height));
        exponentSlider.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        exponentSlider.addChangeListener(e ->
                params.dramaticFactor = exponentSlider.getValue() / 100.0);
        exponentSlider.setVisible(params.dynamicsExponential);

        // Layout helper: arranges button + slider based on mode
        Runnable relayout = () -> {
            dynOptions.removeAll();
            if (params.dynamicsExponential) {
                dynOptions.add(curveBtn);
                dynOptions.add(exponentSlider);
            } else {
                dynOptions.add(curveBtn);
            }
            dynOptions.revalidate();
            dynOptions.repaint();
        };

        curveBtn.addActionListener(e -> {
            params.dynamicsExponential = !params.dynamicsExponential;
            curveBtn.setText(params.dynamicsExponential ? "Exponential" : "Linear");
            exponentSlider.setVisible(params.dynamicsExponential);
            relayout.run();
        });

        relayout.run(); // initial layout

        help.register(curveBtn, "Linear: direct gain. Exponential: applies an exponential curve for more dramatic shaping.");
        help.register(exponentSlider, "Controls the steepness of exponential shaping. Higher = more aggressive attack/decay contrast.");

        dynamicsCard.add(dynOptions, BorderLayout.SOUTH);
        dynamicsCard.setMinimumSize(new Dimension(350, 180));

        pitchCard = buildEditorCard("Pitch",
                "Pitch shift over time. Center = no change, up = higher, down = lower.",
                pitchCanvas, pitchEnvelope, pitchToggle);
        pitchCard.setMinimumSize(new Dimension(350, 180));

        // Initial grid layout
        layoutCards();

        // Responsive: switch between 2x2 and 1-column on resize
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                boolean shouldStack = getWidth() > 0 && getWidth() < 700;
                if (shouldStack != stacked) {
                    stacked = shouldStack;
                    removeAll();
                    layoutCards();
                    revalidate();
                    repaint();
                }
            }
        });

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

    private void layoutCards() {
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.fill = java.awt.GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.insets = new java.awt.Insets(6, 6, 6, 6);

        if (stacked) {
            gbc.gridx = 0;
            gbc.gridy = 0; add(densityCard, gbc);
            gbc.gridy = 1; add(mixCard, gbc);
            gbc.gridy = 2; add(dynamicsCard, gbc);
            gbc.gridy = 3; add(pitchCard, gbc);
        } else {
            gbc.gridx = 0; gbc.gridy = 0; add(densityCard, gbc);
            gbc.gridx = 1; gbc.gridy = 0; add(mixCard, gbc);
            gbc.gridx = 0; gbc.gridy = 1; add(dynamicsCard, gbc);
            gbc.gridx = 1; gbc.gridy = 1; add(pitchCard, gbc);
        }
    }

    public void rebuild() {
        removeAll();
        buildLayout();
        revalidate();
        repaint();
    }

    private JPanel buildEditorCard(String title, String helpText, EnvelopeCanvas canvas,
                                   Envelope envelope, JComponent titleExtra) {
        JPanel card;
        if (Theme.isPaper()) {
            card = new PmCard(PmCard.Variant.FLAT);
        } else {
            card = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (Theme.isSynthwave()) {
                        SynthwavePainter.fillPanel(g2, 0, 0, getWidth(), getHeight(),
                                Theme.BG_CARD, Theme.BG_CARD);
                        SynthwavePainter.paintBevel(g2, 0, 0, getWidth(), getHeight(), true);
                    } else {
                        g2.setColor(Theme.BG_CARD);
                        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                                Theme.RADIUS_LG, Theme.RADIUS_LG);
                    }
                    g2.dispose();
                }
            };
            card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        }
        card.setLayout(new BorderLayout(0, 8));
        card.setOpaque(false);

        // Header with title + buttons
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleArea = new JPanel(new BorderLayout());
        titleArea.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);

        if (Theme.isPaper()) {
            JLabel titleLabel = new JLabel(title.toUpperCase());
            Theme.tagFont(titleLabel, "title");
            titleLabel.setForeground(PaperMinimalistTokens.INK);
            titleRow.add(titleLabel);
        } else {
            titleRow.add(Theme.sectionLabel(title));
        }
        if (titleExtra != null) {
            titleRow.add(titleExtra);
        }
        titleArea.add(titleRow, BorderLayout.NORTH);

        JLabel helpLabel = new JLabel(helpText);
        if (Theme.isPaper()) {
            helpLabel.setFont(PaperMinimalistTokens.FONT_BODY_XS);
            helpLabel.setForeground(PaperMinimalistTokens.INK_GHOST);
        } else {
            Theme.tagFont(helpLabel, "small");
            Theme.tagFg(helpLabel, "fgDim");
        }
        helpLabel.setBorder(BorderFactory.createEmptyBorder(3, 5, 0, 0));
        titleArea.add(helpLabel, BorderLayout.SOUTH);
        header.add(titleArea, BorderLayout.CENTER);

        // Button bar
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonBar.setOpaque(false);

        if (Theme.isPaper()) {
            PmButton resetBtn = new PmButton("Reset", PmButton.Variant.GHOST).small();
            resetBtn.addActionListener(e -> canvas.reset());
            resetBtn.setPreferredSize(new Dimension(58, 26));

            PmButton undoBtn = new PmButton("Undo", PmButton.Variant.GHOST).small();
            undoBtn.setComponentEnabled(false);
            undoBtn.addActionListener(e -> {
                canvas.undo();
                undoBtn.setComponentEnabled(canvas.canUndo());
            });
            canvas.addChangeListener(e -> undoBtn.setComponentEnabled(canvas.canUndo()));
            undoBtn.setPreferredSize(new Dimension(52, 26));

            if (canvas.pitchMode) {
                PmButton snapBtn = new PmButton("Snap", PmButton.Variant.GHOST).small();
                snapBtn.setPreferredSize(new Dimension(52, 26));
                snapBtn.addActionListener(e -> {
                    canvas.setSnapToGrid(!canvas.isSnapToGrid());
                    snapBtn.setText(canvas.isSnapToGrid() ? "Snap \u2713" : "Snap");
                });
                buttonBar.add(snapBtn);
            }

            buttonBar.add(resetBtn);
            buttonBar.add(undoBtn);
        } else {
            JButton resetBtn = Theme.ghostButton("Reset");
            Theme.tagFont(resetBtn, "small");
            resetBtn.setPreferredSize(new Dimension(58, 26));
            resetBtn.addActionListener(e -> canvas.reset());

            JButton undoBtn = Theme.ghostButton("Undo");
            Theme.tagFont(undoBtn, "small");
            undoBtn.setPreferredSize(new Dimension(52, 26));
            undoBtn.setEnabled(false);
            undoBtn.addActionListener(e -> {
                canvas.undo();
                undoBtn.setEnabled(canvas.canUndo());
            });
            canvas.addChangeListener(e -> undoBtn.setEnabled(canvas.canUndo()));

            HelpManager help = HelpManager.getInstance();
            help.register(resetBtn, "Reset this envelope to its default shape.");
            help.register(undoBtn, "Undo the last edit to this envelope.");

            // Snap button (pitch envelope only)
            if (canvas.pitchMode) {
                JButton snapBtn = Theme.ghostButton("Snap");
                Theme.tagFont(snapBtn, "small");
                snapBtn.setPreferredSize(new Dimension(52, 26));
                snapBtn.addActionListener(e -> {
                    canvas.setSnapToGrid(!canvas.isSnapToGrid());
                    snapBtn.setText(canvas.isSnapToGrid() ? "Snap \u2713" : "Snap");
                });
                help.register(snapBtn, "Snap nodes to exact semitone pitches.");
                buttonBar.add(snapBtn);
            }

            buttonBar.add(resetBtn);
            buttonBar.add(undoBtn);
        }

        header.add(buttonBar, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        // Canvas wrapper
        JPanel editorWrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (Theme.isPaper()) {
                    g2.setColor(PaperMinimalistTokens.PAPER_WARM);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                            PaperMinimalistTokens.RADIUS_SM, PaperMinimalistTokens.RADIUS_SM);
                    g2.setColor(PaperMinimalistTokens.BORDER_FAINT);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                            PaperMinimalistTokens.RADIUS_SM, PaperMinimalistTokens.RADIUS_SM);
                } else if (Theme.isSynthwave()) {
                    SynthwavePainter.fillPanel(g2, 0, 0, getWidth(), getHeight(),
                            Theme.BG_INPUT, Theme.BORDER_SUBTLE);
                } else {
                    g2.setColor(Theme.BG_INPUT);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                            Theme.RADIUS, Theme.RADIUS);
                    g2.setColor(Theme.BORDER_SUBTLE);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                            Theme.RADIUS, Theme.RADIUS);
                }
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
        env.segmentCurves = new ArrayList<>();

        if (src.times != null && src.times.length > 0) {
            for (int i = 0; i < src.times.length; i++) {
                int x = (int) (src.times[i] * EnvelopeCanvas.COORD_X_MAX);
                int y = convertValueToCoord(src.values[i], dbMode, pitchMode);
                env.coords.add(new int[]{x, y});
                double curve = (src.curves != null && i < src.curves.length) ? src.curves[i] : 0.0;
                env.segmentCurves.add(curve);
            }
        } else {
            int defaultY = pitchMode ? 250 : 500;
            env.coords.add(new int[]{0, defaultY});
            env.coords.add(new int[]{EnvelopeCanvas.COORD_X_MAX, defaultY});
            env.segmentCurves.add(0.0);
            env.segmentCurves.add(0.0);
        }

        int n = env.coords.size();
        env.times = new double[n];
        env.values = new double[n];
        env.curves = new double[n];
        for (int i = 0; i < n; i++) {
            int[] coord = env.coords.get(i);
            env.times[i] = coord[0] / (double) EnvelopeCanvas.COORD_X_MAX;
            env.values[i] = convertCoordToValue(coord[1], dbMode, pitchMode);
            env.curves[i] = env.segmentCurves.get(i);
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
        int n = src.coords.size();
        double[] times = new double[n];
        double[] values = new double[n];
        double[] curves = new double[n];
        for (int i = 0; i < n; i++) {
            int[] coord = src.coords.get(i);
            times[i] = coord[0] / (double) EnvelopeCanvas.COORD_X_MAX;
            values[i] = convertCoordToValue(coord[1], dbMode, pitchMode);
            curves[i] = (i < src.segmentCurves.size()) ? src.segmentCurves.get(i) : 0.0;
        }
        return new Envelope(times, values, curves);
    }

    private static void copyEnvelopeData(Envelope src, Envelope dst, boolean dbMode, boolean pitchMode) {
        dst.coords = new ArrayList<>();
        dst.segmentCurves = new ArrayList<>();
        if (src.times != null) {
            for (int i = 0; i < src.times.length; i++) {
                int x = (int) (src.times[i] * EnvelopeCanvas.COORD_X_MAX);
                int y = convertValueToCoord(src.values[i], dbMode, pitchMode);
                dst.coords.add(new int[]{x, y});
                double curve = (src.curves != null && i < src.curves.length) ? src.curves[i] : 0.0;
                dst.segmentCurves.add(curve);
            }
        }
        int n = dst.coords.size();
        dst.times = new double[n];
        dst.values = new double[n];
        dst.curves = new double[n];
        for (int i = 0; i < n; i++) {
            int[] coord = dst.coords.get(i);
            dst.times[i] = coord[0] / (double) EnvelopeCanvas.COORD_X_MAX;
            dst.values[i] = convertCoordToValue(coord[1], dbMode, pitchMode);
            dst.curves[i] = dst.segmentCurves.get(i);
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
