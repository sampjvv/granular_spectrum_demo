package org.delightofcomposition.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Scrollable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.sptc.uilab.papermin.PmCard;
import com.sptc.uilab.papermin.PmTabs;
import com.sptc.uilab.tokens.PaperMinimalistTokens;

import org.delightofcomposition.SynthParameters;

/**
 * Phone-style parameter panel with single-column layout,
 * iOS-inspired controls, and generous touch-friendly spacing.
 */
public class ParameterPanel extends JPanel implements Scrollable {

    private final SynthParameters params;
    private final List<Runnable> syncActions = new ArrayList<>();

    private SamplesSection samplesSection;

    public ParameterPanel(SynthParameters params) {
        this.params = params;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        buildControls();
    }

    private void buildControls() {
        syncActions.clear();

        add(buildSamplesSection());
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildSynthesisSection());
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildReverbSection());
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildOutputSection());
        add(Box.createVerticalGlue());

        registerHelpTexts();
    }

    public void rebuild() {
        removeAll();
        buildControls();
        syncFromParams();
        revalidate();
        repaint();
    }

    private void registerHelpTexts() {
        HelpManager help = HelpManager.getInstance();
        help.register(samplesSection.getSourceDropPanel(), "The audio file whose spectral content will be analyzed and resynthesized as granular texture.");
        help.register(samplesSection.getGrainDropPanel(), "The short audio sample used as the building block for granular synthesis. Its timbre colors the output.");
        help.register(samplesSection.getIrDropPanel(), "An impulse response recording used for convolution reverb, placing the sound in a virtual space.");
        help.register(samplesSection.getRefFreqStepper(), "The fundamental frequency (Hz) of the grain sample. Used to tune grains to match spectral peaks.");
    }

    // ── Section builders ──

    private JPanel buildSamplesSection() {
        samplesSection = new SamplesSection(params, true);
        return samplesSection.build();
    }

    private JPanel buildSynthesisSection() {
        JPanel card;
        JPanel content;

        if (Theme.isPaper()) {
            PmCard pmCard = new PmCard(PmCard.Variant.DEFAULT);
            content = new JPanel();
            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.add(paperSectionHeader("Synthesis"));
            content.add(Box.createVerticalStrut(8));
            pmCard.add(content, BorderLayout.CENTER);
            card = pmCard;
        } else {
            card = sectionCard();
            content = card;
            content.add(Theme.sectionHeader("Synthesis"));
            content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        }

        JLabel winLabel = Theme.isPaper() ? paperLabel("Window Size") : Theme.paramLabel("Window Size");
        winLabel.setAlignmentX(0);
        content.add(winLabel);
        content.add(Box.createVerticalStrut(Theme.LABEL_GAP));

        String[] windowLabels = {"1K", "2K", "4K", "8K", "16K", "32K", "64K"};

        if (Theme.isPaper()) {
            PmTabs pmTabs = new PmTabs(windowLabels);
            pmTabs.setSelectedIndex(params.windowSizeExponent - 10);
            pmTabs.setOnChange(idx -> params.windowSizeExponent = idx + 10);
            pmTabs.setAlignmentX(0);
            content.add(pmTabs);
            syncActions.add(() -> pmTabs.setSelectedIndex(params.windowSizeExponent - 10));
        } else {
            SegmentedControl windowSizeSegmented = new SegmentedControl(windowLabels, params.windowSizeExponent - 10);
            windowSizeSegmented.setAlignmentX(0);
            windowSizeSegmented.addChangeListener(e ->
                    params.windowSizeExponent = windowSizeSegmented.getSelectedIndex() + 10);
            content.add(windowSizeSegmented);
            syncActions.add(() -> windowSizeSegmented.setSelectedIndex(params.windowSizeExponent - 10));
        }
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        JLabel crLabel = Theme.isPaper() ? paperLabel("Control Rate") : Theme.paramLabel("Control Rate");
        crLabel.setAlignmentX(0);
        content.add(crLabel);
        content.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        StepperControl controlRateStepper = new StepperControl(params.controlRate, 0.01, 1.0, 0.01, "%.2f s");
        controlRateStepper.setAlignmentX(0);
        controlRateStepper.addChangeListener(e -> params.controlRate = controlRateStepper.getDoubleValue());
        content.add(controlRateStepper);
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        syncActions.add(() -> controlRateStepper.setValue(params.controlRate));

        JLabel gpLabel = Theme.isPaper() ? paperLabel("Grains / Peak") : Theme.paramLabel("Grains / Peak");
        gpLabel.setAlignmentX(0);
        content.add(gpLabel);
        content.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        StepperControl grainsPerPeakStepper = new StepperControl(params.grainsPerPeak, 1, 50, 1);
        grainsPerPeakStepper.setAlignmentX(0);
        grainsPerPeakStepper.addChangeListener(e -> params.grainsPerPeak = grainsPerPeakStepper.getIntValue());
        content.add(grainsPerPeakStepper);
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        syncActions.add(() -> grainsPerPeakStepper.setValue(params.grainsPerPeak));

        LabeledSlider ampThresholdSlider = new LabeledSlider("Amp Threshold", 1, 500,
                (int) (params.amplitudeThreshold * 1000),
                v -> String.format("%.3f", v / 1000.0));
        ampThresholdSlider.setAlignmentX(0);
        ampThresholdSlider.addChangeListener(e ->
                params.amplitudeThreshold = ampThresholdSlider.getValue() / 1000.0);
        content.add(ampThresholdSlider);
        syncActions.add(() -> ampThresholdSlider.setValue((int) (params.amplitudeThreshold * 1000)));

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JPanel buildReverbSection() {
        JPanel card;
        JPanel content;

        if (Theme.isPaper()) {
            PmCard pmCard = new PmCard(PmCard.Variant.DEFAULT);
            content = new JPanel();
            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.add(paperSectionHeader("Reverb"));
            content.add(Box.createVerticalStrut(8));
            pmCard.add(content, BorderLayout.CENTER);
            card = pmCard;
        } else {
            card = sectionCard();
            content = card;
            content.add(Theme.sectionHeader("Reverb"));
            content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        }

        ToggleSwitch useReverbToggle = new ToggleSwitch(params.useReverb);
        useReverbToggle.addChangeListener(e -> params.useReverb = useReverbToggle.isSelected());
        JPanel toggleRow = Theme.toggleRow("Use Reverb", useReverbToggle);
        toggleRow.setAlignmentX(0);
        content.add(toggleRow);
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        syncActions.add(() -> useReverbToggle.setSelected(params.useReverb));

        LabeledSlider sourceReverbSlider = new LabeledSlider("Source Mix", 0, 100,
                (int) (params.sourceReverbMix * 100),
                v -> v + "%");
        sourceReverbSlider.setAlignmentX(0);
        sourceReverbSlider.addChangeListener(e ->
                params.sourceReverbMix = sourceReverbSlider.getValue() / 100.0);
        content.add(sourceReverbSlider);
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        syncActions.add(() -> sourceReverbSlider.setValue((int) (params.sourceReverbMix * 100)));

        LabeledSlider synthReverbSlider = new LabeledSlider("Synth Mix", 0, 100,
                (int) (params.synthReverbMix * 100),
                v -> v + "%");
        synthReverbSlider.setAlignmentX(0);
        synthReverbSlider.addChangeListener(e ->
                params.synthReverbMix = synthReverbSlider.getValue() / 100.0);
        content.add(synthReverbSlider);
        syncActions.add(() -> synthReverbSlider.setValue((int) (params.synthReverbMix * 100)));

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JPanel buildOutputSection() {
        JPanel card;
        JPanel content;

        if (Theme.isPaper()) {
            PmCard pmCard = new PmCard(PmCard.Variant.DEFAULT);
            content = new JPanel();
            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.add(paperSectionHeader("Output"));
            content.add(Box.createVerticalStrut(8));
            pmCard.add(content, BorderLayout.CENTER);
            card = pmCard;
        } else {
            card = sectionCard();
            content = card;
            content.add(Theme.sectionHeader("Output"));
            content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        }

        LabeledSlider panSmoothSlider = new LabeledSlider("Pan Smoothing", 0, 100,
                (int) (params.panSmoothing * 100),
                v -> String.format("%.2f", v / 100.0));
        panSmoothSlider.setAlignmentX(0);
        panSmoothSlider.addChangeListener(e ->
                params.panSmoothing = panSmoothSlider.getValue() / 100.0);
        content.add(panSmoothSlider);
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        syncActions.add(() -> panSmoothSlider.setValue((int) (params.panSmoothing * 100)));


        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    // ── Helpers ──

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

    private static JTextField styledTextField(String text) {
        JTextField field = new JTextField(text);
        Theme.tagFont(field, "mono");
        Theme.tagFg(field, "fg");
        field.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(null, -1, new Insets(0, 0, 0, 0)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setPreferredSize(new Dimension(200, 40));
        field.setAlignmentX(0);
        return field;
    }

    private static double[] parseDoubleArraySafe(String text, double[] fallback) {
        try {
            return parseDoubleArray(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double[] parseDoubleArray(String text) {
        String[] parts = text.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++)
            result[i] = Double.parseDouble(parts[i].trim());
        return result;
    }

    private static String arrayToString(double[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(arr[i] == (int) arr[i] ? String.valueOf((int) arr[i]) : String.valueOf(arr[i]));
        }
        return sb.toString();
    }

    public void setSourceFileChangeListener(Consumer<File> listener) {
        if (samplesSection != null) samplesSection.setSourceFileChangeListener(listener);
    }

    // ── Scrollable — force panel width to match viewport ──

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

    public void syncFromParams() {
        if (samplesSection != null) samplesSection.syncFromParams();
        for (Runnable action : syncActions) action.run();
    }
}
