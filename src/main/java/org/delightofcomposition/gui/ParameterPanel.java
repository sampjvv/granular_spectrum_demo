package org.delightofcomposition.gui;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Scrollable;

import org.delightofcomposition.SynthParameters;

/**
 * Phone-style parameter panel with single-column layout,
 * iOS-inspired controls, and generous touch-friendly spacing.
 */
public class ParameterPanel extends JPanel implements Scrollable {

    private final SynthParameters params;

    private SampleDropPanel sourceDropPanel;
    private SampleDropPanel grainDropPanel;
    private SampleDropPanel irDropPanel;
    private StepperControl refFreqStepper;
    private SegmentedControl windowSizeSegmented;
    private StepperControl controlRateStepper;
    private StepperControl grainsPerPeakStepper;
    private LabeledSlider ampThresholdSlider;
    private ToggleSwitch useReverbToggle;
    private LabeledSlider sourceReverbSlider;
    private LabeledSlider synthReverbSlider;
    private LabeledSlider panSmoothSlider;
    private StepperControl crossfadeStepper;
    private LabeledSlider dramaticSlider;
    private ToggleSwitch chordEnableToggle;
    private JTextField chordRatiosField;
    private JTextField chordAttacksField;

    public ParameterPanel(SynthParameters params) {
        this.params = params;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildSamplesSection());
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildSynthesisSection());
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildReverbSection());
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildOutputSection());
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildChordSection());
        add(Box.createVerticalGlue());
    }

    // ── Section builders ──

    private JPanel buildSamplesSection() {
        JPanel card = sectionCard();

        card.add(Theme.sectionHeader("Samples"));
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        sourceDropPanel = new SampleDropPanel("Source Sample", params.sourceFile,
                file -> params.sourceFile = file);
        sourceDropPanel.setToolTipText("The source sample to analyze spectrally");
        sourceDropPanel.setAlignmentX(0);
        card.add(sourceDropPanel);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        grainDropPanel = new SampleDropPanel("Grain Sample", params.grainFile,
                file -> params.grainFile = file);
        grainDropPanel.setToolTipText("The grain sample used for resynthesis");
        grainDropPanel.setAlignmentX(0);
        card.add(grainDropPanel);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        JLabel refLabel = Theme.paramLabel("Reference Frequency");
        refLabel.setAlignmentX(0);
        card.add(refLabel);
        card.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        refFreqStepper = new StepperControl(params.grainReferenceFreq, 20, 20000, 1, "%.0f Hz");
        refFreqStepper.setAlignmentX(0);
        refFreqStepper.addChangeListener(e -> params.grainReferenceFreq = refFreqStepper.getDoubleValue());
        refFreqStepper.setToolTipText("Fundamental frequency of the grain sample in Hz");
        card.add(refFreqStepper);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        irDropPanel = new SampleDropPanel("Impulse Response", params.impulseResponseFile,
                file -> params.impulseResponseFile = file);
        irDropPanel.setToolTipText("Impulse response for convolution reverb");
        irDropPanel.setAlignmentX(0);
        card.add(irDropPanel);

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JPanel buildSynthesisSection() {
        JPanel card = sectionCard();

        card.add(Theme.sectionHeader("Synthesis"));
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        JLabel winLabel = Theme.paramLabel("Window Size");
        winLabel.setAlignmentX(0);
        card.add(winLabel);
        card.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        String[] windowLabels = {"1K", "2K", "4K", "8K", "16K", "32K", "64K"};
        windowSizeSegmented = new SegmentedControl(windowLabels, params.windowSizeExponent - 10);
        windowSizeSegmented.setAlignmentX(0);
        windowSizeSegmented.addChangeListener(e ->
                params.windowSizeExponent = windowSizeSegmented.getSelectedIndex() + 10);
        windowSizeSegmented.setToolTipText("FFT window size in samples");
        card.add(windowSizeSegmented);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        JLabel crLabel = Theme.paramLabel("Control Rate");
        crLabel.setAlignmentX(0);
        card.add(crLabel);
        card.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        controlRateStepper = new StepperControl(params.controlRate, 0.01, 1.0, 0.01, "%.2f s");
        controlRateStepper.setAlignmentX(0);
        controlRateStepper.addChangeListener(e -> params.controlRate = controlRateStepper.getDoubleValue());
        controlRateStepper.setToolTipText("Seconds between overlapping analysis windows");
        card.add(controlRateStepper);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        JLabel gpLabel = Theme.paramLabel("Grains / Peak");
        gpLabel.setAlignmentX(0);
        card.add(gpLabel);
        card.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        grainsPerPeakStepper = new StepperControl(params.grainsPerPeak, 1, 50, 1);
        grainsPerPeakStepper.setAlignmentX(0);
        grainsPerPeakStepper.addChangeListener(e -> params.grainsPerPeak = grainsPerPeakStepper.getIntValue());
        grainsPerPeakStepper.setToolTipText("More grains = denser texture but longer render");
        card.add(grainsPerPeakStepper);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        ampThresholdSlider = new LabeledSlider("Amp Threshold", 1, 500,
                (int) (params.amplitudeThreshold * 1000),
                v -> String.format("%.3f", v / 1000.0));
        ampThresholdSlider.setAlignmentX(0);
        ampThresholdSlider.addChangeListener(e ->
                params.amplitudeThreshold = ampThresholdSlider.getValue() / 1000.0);
        ampThresholdSlider.setToolTipText("Higher = only the loudest spectral peaks produce grains");
        card.add(ampThresholdSlider);

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JPanel buildReverbSection() {
        JPanel card = sectionCard();

        card.add(Theme.sectionHeader("Reverb"));
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        useReverbToggle = new ToggleSwitch(params.useReverb);
        useReverbToggle.addChangeListener(e -> params.useReverb = useReverbToggle.isSelected());
        JPanel toggleRow = Theme.toggleRow("Use Reverb", useReverbToggle);
        toggleRow.setAlignmentX(0);
        toggleRow.setToolTipText("Enable/disable convolution reverb");
        card.add(toggleRow);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        sourceReverbSlider = new LabeledSlider("Source Mix", 0, 100,
                (int) (params.sourceReverbMix * 100),
                v -> v + "%");
        sourceReverbSlider.setAlignmentX(0);
        sourceReverbSlider.addChangeListener(e ->
                params.sourceReverbMix = sourceReverbSlider.getValue() / 100.0);
        card.add(sourceReverbSlider);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        synthReverbSlider = new LabeledSlider("Synth Mix", 0, 100,
                (int) (params.synthReverbMix * 100),
                v -> v + "%");
        synthReverbSlider.setAlignmentX(0);
        synthReverbSlider.addChangeListener(e ->
                params.synthReverbMix = synthReverbSlider.getValue() / 100.0);
        card.add(synthReverbSlider);

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JPanel buildOutputSection() {
        JPanel card = sectionCard();

        card.add(Theme.sectionHeader("Output"));
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        panSmoothSlider = new LabeledSlider("Pan Smoothing", 0, 100,
                (int) (params.panSmoothing * 100),
                v -> String.format("%.2f", v / 100.0));
        panSmoothSlider.setAlignmentX(0);
        panSmoothSlider.addChangeListener(e ->
                params.panSmoothing = panSmoothSlider.getValue() / 100.0);
        card.add(panSmoothSlider);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        JLabel cfLabel = Theme.paramLabel("Crossfade");
        cfLabel.setAlignmentX(0);
        card.add(cfLabel);
        card.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        crossfadeStepper = new StepperControl(params.crossfadeDuration, 0.0, 5.0, 0.1, "%.1f s");
        crossfadeStepper.setAlignmentX(0);
        crossfadeStepper.addChangeListener(e -> params.crossfadeDuration = crossfadeStepper.getDoubleValue());
        card.add(crossfadeStepper);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        dramaticSlider = new LabeledSlider("Dramatic", 0, 200,
                (int) (params.dramaticFactor * 10),
                v -> String.format("%.1f", v / 10.0));
        dramaticSlider.setAlignmentX(0);
        dramaticSlider.addChangeListener(e ->
                params.dramaticFactor = dramaticSlider.getValue() / 10.0);
        card.add(dramaticSlider);

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JPanel buildChordSection() {
        JPanel card = sectionCard();

        card.add(Theme.sectionHeader("Chord Mode"));
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        chordEnableToggle = new ToggleSwitch(params.useChordMode);
        chordEnableToggle.addChangeListener(e -> params.useChordMode = chordEnableToggle.isSelected());
        JPanel toggleRow = Theme.toggleRow("Enable", chordEnableToggle);
        toggleRow.setAlignmentX(0);
        toggleRow.setToolTipText("Build a just-intonation chord from multiple transpositions");
        card.add(toggleRow);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        JLabel ratiosLabel = Theme.paramLabel("Ratios");
        ratiosLabel.setAlignmentX(0);
        card.add(ratiosLabel);
        card.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        chordRatiosField = styledTextField(arrayToString(params.chordRatios));
        chordRatiosField.addActionListener(e -> parseChordRatios());
        chordRatiosField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                parseChordRatios();
            }
        });
        chordRatiosField.setToolTipText("Just intonation ratios, e.g. '1, 3, 5'");
        card.add(chordRatiosField);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        JLabel attacksLabel = Theme.paramLabel("Attack Times");
        attacksLabel.setAlignmentX(0);
        card.add(attacksLabel);
        card.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        chordAttacksField = styledTextField(arrayToString(params.chordAttackTimes));
        chordAttacksField.addActionListener(e -> parseChordAttacks());
        chordAttacksField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                parseChordAttacks();
            }
        });
        chordAttacksField.setToolTipText("Stagger entry times in seconds, e.g. '0, 3, 4'");
        card.add(chordAttacksField);

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    // ── Helpers ──

    private JPanel sectionCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER, Theme.RADIUS_LG, new Insets(0, 0, 0, 0)),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        return card;
    }

    private static JTextField styledTextField(String text) {
        JTextField field = new JTextField(text);
        field.setFont(Theme.FONT_MONO);
        field.setBackground(Theme.BG_INPUT);
        field.setForeground(Theme.FG);
        field.setCaretColor(Theme.FG);
        field.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER, Theme.RADIUS_SM, new Insets(0, 0, 0, 0)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setPreferredSize(new Dimension(200, 40));
        field.setAlignmentX(0);
        return field;
    }

    private void parseChordRatios() {
        try { params.chordRatios = parseDoubleArray(chordRatiosField.getText()); }
        catch (NumberFormatException ignored) {}
    }

    private void parseChordAttacks() {
        try { params.chordAttackTimes = parseDoubleArray(chordAttacksField.getText()); }
        catch (NumberFormatException ignored) {}
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
        sourceDropPanel.setFile(params.sourceFile);
        grainDropPanel.setFile(params.grainFile);
        irDropPanel.setFile(params.impulseResponseFile);
        refFreqStepper.setValue(params.grainReferenceFreq);
        windowSizeSegmented.setSelectedIndex(params.windowSizeExponent - 10);
        controlRateStepper.setValue(params.controlRate);
        grainsPerPeakStepper.setValue(params.grainsPerPeak);
        ampThresholdSlider.setValue((int) (params.amplitudeThreshold * 1000));
        useReverbToggle.setSelected(params.useReverb);
        sourceReverbSlider.setValue((int) (params.sourceReverbMix * 100));
        synthReverbSlider.setValue((int) (params.synthReverbMix * 100));
        panSmoothSlider.setValue((int) (params.panSmoothing * 100));
        crossfadeStepper.setValue(params.crossfadeDuration);
        dramaticSlider.setValue((int) (params.dramaticFactor * 10));
        chordEnableToggle.setSelected(params.useChordMode);
        chordRatiosField.setText(arrayToString(params.chordRatios));
        chordAttacksField.setText(arrayToString(params.chordAttackTimes));
    }
}
