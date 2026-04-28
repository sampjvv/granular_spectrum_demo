package org.delightofcomposition.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import com.sptc.uilab.papermin.PmCard;
import com.sptc.uilab.tokens.PaperMinimalistTokens;

import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.sound.AudioPlayer;
import org.delightofcomposition.sound.FFT2;
import org.delightofcomposition.sound.ReadSound;
import org.delightofcomposition.sound.WaveWriter;

/**
 * Shared "Samples" section: Source / Grain / IR drop panels, reference
 * frequency stepper, and per-row preview buttons. Used by both
 * ParameterPanel (WAV mode) and LiveParameterPanel (Live mode) so they
 * share state and behavior — preferences save, source-change listener,
 * pitch detection.
 *
 * Construct one and call {@link #build()} to get the section card to
 * add into a parent layout.
 */
public class SamplesSection {

    private static final AudioPlayer previewPlayer = new AudioPlayer();
    private static JButton currentPreviewBtn;

    private final SynthParameters params;
    private final boolean usePaperCard;
    private final List<Runnable> syncActions = new ArrayList<>();

    private SampleDropPanel sourceDropPanel;
    private SampleDropPanel grainDropPanel;
    private SampleDropPanel irDropPanel;
    private StepperControl refFreqStepper;
    private Consumer<File> sourceFileChangeListener;

    /**
     * @param params  shared synth params
     * @param usePaperCard  if true and current theme is paper, render with PmCard;
     *                      pass false to always use the standard painted sectionCard
     *                      (matches LiveParameterPanel's other sections).
     */
    public SamplesSection(SynthParameters params, boolean usePaperCard) {
        this.params = params;
        this.usePaperCard = usePaperCard;
    }

    /** Build and return the section card (PmCard for paper, painted JPanel otherwise). */
    public JPanel build() {
        JPanel card;
        JPanel content;

        if (usePaperCard && Theme.isPaper()) {
            PmCard pmCard = new PmCard(PmCard.Variant.DEFAULT);
            content = new JPanel();
            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.add(paperSectionHeader("Samples"));
            content.add(Box.createVerticalStrut(8));
            pmCard.add(content, BorderLayout.CENTER);
            card = pmCard;
        } else {
            card = sectionCard();
            content = card;
            content.add(Theme.sectionHeader("Samples"));
            content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        }

        sourceDropPanel = new SampleDropPanel("Source Sample", params.sourceFile,
                file -> {
                    params.sourceFile = file;
                    SamplePreferences.saveSourceFile(file);
                    if (sourceFileChangeListener != null) sourceFileChangeListener.accept(file);
                });
        content.add(sampleRow(sourceDropPanel));
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        syncActions.add(() -> sourceDropPanel.setFile(params.sourceFile));

        grainDropPanel = new SampleDropPanel("Grain Sample", params.grainFile,
                file -> {
                    params.grainFile = file;
                    SamplePreferences.saveGrainFile(file);
                    detectGrainPitch(file);
                });
        content.add(sampleRow(grainDropPanel));
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        syncActions.add(() -> grainDropPanel.setFile(params.grainFile));

        JLabel refLabel = (usePaperCard && Theme.isPaper()) ? paperLabel("Reference Frequency") : Theme.paramLabel("Reference Frequency");
        refLabel.setAlignmentX(0);
        content.add(refLabel);
        content.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        refFreqStepper = new StepperControl(params.grainReferenceFreq, 20, 20000, 0.1, "%.1f Hz");
        refFreqStepper.setAlignmentX(0);
        refFreqStepper.addChangeListener(e -> params.grainReferenceFreq = refFreqStepper.getDoubleValue());
        content.add(refFreqStepper);
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        syncActions.add(() -> refFreqStepper.setValue(params.grainReferenceFreq));

        irDropPanel = new SampleDropPanel("Impulse Response", params.impulseResponseFile,
                file -> {
                    params.impulseResponseFile = file;
                    SamplePreferences.saveImpulseResponseFile(file);
                });
        content.add(sampleRow(irDropPanel));
        syncActions.add(() -> irDropPanel.setFile(params.impulseResponseFile));

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));

        if (params.grainFile != null && params.grainFile.exists()) {
            detectGrainPitch(params.grainFile);
        }

        return card;
    }

    public void setSourceFileChangeListener(Consumer<File> listener) {
        this.sourceFileChangeListener = listener;
    }

    public void syncFromParams() {
        for (Runnable action : syncActions) action.run();
    }

    public SampleDropPanel getSourceDropPanel() { return sourceDropPanel; }
    public SampleDropPanel getGrainDropPanel() { return grainDropPanel; }
    public SampleDropPanel getIrDropPanel() { return irDropPanel; }
    public StepperControl getRefFreqStepper() { return refFreqStepper; }

    // ── Sample row + preview ──

    private JPanel sampleRow(SampleDropPanel panel) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.add(panel, BorderLayout.CENTER);

        JButton playBtn = createPlayButton();
        playBtn.addActionListener(e -> toggleSamplePreview(playBtn, panel));
        Box btnBox = Box.createVerticalBox();
        btnBox.add(Box.createVerticalGlue());
        btnBox.add(playBtn);
        btnBox.add(Box.createVerticalGlue());
        row.add(btnBox, BorderLayout.EAST);

        row.setAlignmentX(0);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        return row;
    }

    private JButton createPlayButton() {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (Theme.isSynthwave()) {
                    SynthwavePainter.paintGhostButton(g2, 0, 0, getWidth(), getHeight(),
                            getModel().isPressed(), getModel().isRollover(), isEnabled());
                } else if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(getModel().isPressed() ? Theme.ZINC_700 : Theme.BG_MUTED);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.RADIUS, Theme.RADIUS);
                }

                g2.setColor(isEnabled() ? Theme.FG : Theme.ZINC_600);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int s = 6;

                if ("stop".equals(getName())) {
                    g2.fillRect(cx - s, cy - s, s * 2, s * 2);
                } else {
                    int[] xs = {cx - s, cx - s, cx + s};
                    int[] ys = {cy - s, cy + s, cy};
                    g2.fillPolygon(xs, ys, 3);
                }
                g2.dispose();
            }
        };
        btn.setName("play");
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(36, 36));
        btn.setMinimumSize(new Dimension(36, 36));
        btn.setMaximumSize(new Dimension(36, 36));
        HelpManager.getInstance().register(btn, "Preview sample");
        return btn;
    }

    private void setPlayIcon(JButton btn, boolean playing) {
        btn.setName(playing ? "stop" : "play");
        btn.repaint();
    }

    private void toggleSamplePreview(JButton btn, SampleDropPanel panel) {
        if (currentPreviewBtn == btn) {
            previewPlayer.stop();
            setPlayIcon(btn, false);
            currentPreviewBtn = null;
            return;
        }
        if (currentPreviewBtn != null) {
            previewPlayer.stop();
            setPlayIcon(currentPreviewBtn, false);
            currentPreviewBtn = null;
        }
        if (panel.getFile() == null || !panel.getFile().exists()) return;
        try {
            float[] mono = ReadSound.readSound(panel.getFile().getPath());
            if (mono == null || mono.length == 0) return;
            float[][] stereo = {mono, mono};
            setPlayIcon(btn, true);
            currentPreviewBtn = btn;
            previewPlayer.play(stereo, WaveWriter.SAMPLE_RATE, () -> {
                setPlayIcon(btn, false);
                currentPreviewBtn = null;
            });
        } catch (Exception ex) {
            // silently ignore preview errors
        }
    }

    // ── Pitch detection ──

    private void detectGrainPitch(File grainFile) {
        if (grainFile == null || !grainFile.exists()) return;
        System.out.println("[PitchDetect] Analyzing: " + grainFile.getName());
        new SwingWorker<Double, Void>() {
            @Override
            protected Double doInBackground() {
                double[] samples = ReadSound.readSoundDoubles(grainFile.getPath());
                if (samples == null || samples.length == 0) {
                    System.err.println("[PitchDetect] Failed to read grain sample");
                    return null;
                }
                System.out.println("[PitchDetect] Sample length: " + samples.length
                        + " (" + String.format("%.2f", samples.length / (double) WaveWriter.SAMPLE_RATE) + "s)");
                double freq = FFT2.getPitch(samples, WaveWriter.SAMPLE_RATE);
                System.out.println("[PitchDetect] Raw detected: " + String.format("%.2f", freq) + " Hz");
                return freq;
            }

            @Override
            protected void done() {
                try {
                    Double freq = get();
                    if (freq != null && freq >= 20 && freq <= 20000) {
                        params.grainReferenceFreq = freq;
                        refFreqStepper.setValue(freq);
                        System.out.println("[PitchDetect] Applied: " + String.format("%.1f", freq) + " Hz");
                    } else {
                        System.err.println("[PitchDetect] Frequency out of range: " + freq);
                    }
                } catch (Exception e) {
                    System.err.println("[PitchDetect] Failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    // ── Card helpers (mirrored from ParameterPanel/LiveParameterPanel) ──

    private JPanel sectionCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_LG, Theme.RADIUS_LG);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(null, -1, new Insets(0, 0, 0, 0)),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        return card;
    }

    private static JLabel paperLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        label.setForeground(PaperMinimalistTokens.INK_LIGHT);
        return label;
    }

    private static JLabel paperSectionHeader(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        Theme.tagFont(label, "section");
        label.setForeground(PaperMinimalistTokens.INK);
        label.setAlignmentX(0);
        return label;
    }
}
