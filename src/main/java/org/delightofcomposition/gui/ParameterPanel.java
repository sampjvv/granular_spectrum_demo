package org.delightofcomposition.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Scrollable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

import com.sptc.uilab.papermin.PmCard;
import com.sptc.uilab.papermin.PmTabs;
import com.sptc.uilab.tokens.PaperMinimalistTokens;

import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.sound.AudioPlayer;
import org.delightofcomposition.sound.FFT2;
import org.delightofcomposition.sound.ReadSound;
import org.delightofcomposition.sound.WaveWriter;

/**
 * Phone-style parameter panel with single-column layout,
 * iOS-inspired controls, and generous touch-friendly spacing.
 */
public class ParameterPanel extends JPanel implements Scrollable {

    private static final AudioPlayer previewPlayer = new AudioPlayer();

    private final SynthParameters params;
    private final List<Runnable> syncActions = new ArrayList<>();
    private JButton currentPreviewBtn;

    private SampleDropPanel sourceDropPanel;
    private SampleDropPanel grainDropPanel;
    private SampleDropPanel irDropPanel;
    private StepperControl refFreqStepper;
    private Consumer<File> sourceFileChangeListener;

    public ParameterPanel(SynthParameters params) {
        this.params = params;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        buildControls();
        updateSourceDuration();

        // Auto-detect grain pitch on startup if a grain sample is already loaded
        if (params.grainFile != null && params.grainFile.exists()) {
            detectGrainPitch(params.grainFile);
        }
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
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildChordSection());
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
        help.register(sourceDropPanel, "The audio file whose spectral content will be analyzed and resynthesized as granular texture.");
        help.register(grainDropPanel, "The short audio sample used as the building block for granular synthesis. Its timbre colors the output.");
        help.register(irDropPanel, "An impulse response recording used for convolution reverb, placing the sound in a virtual space.");
        help.register(refFreqStepper, "The fundamental frequency (Hz) of the grain sample. Used to tune grains to match spectral peaks.");
    }

    // ── Section builders ──

    private JPanel buildSamplesSection() {
        JPanel card;
        JPanel content;

        if (Theme.isPaper()) {
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
                    updateSourceDuration();
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

        JLabel refLabel = Theme.isPaper() ? paperLabel("Reference Frequency") : Theme.paramLabel("Reference Frequency");
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
        return card;
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

    // Interval names indexed by semitone (0-11)
    private static final String[] INTERVAL_NAMES = {
            "P1", "m2", "M2", "m3", "M3", "P4", "TT", "P5", "m6", "M6", "m7", "M7"};

    // Chord preset definitions
    private static final String[] CHORD_PRESET_NAMES = {"Overtone", "Major", "Minor", "Octaves", "Custom"};
    private static final boolean[] CHORD_PRESET_OVERTONE = {true, false, false, false, false};
    // Overtone preset uses harmonics; others use intervals+octaves
    private static final int[][] CHORD_PRESET_HARMONICS = {
            {1, 3, 5}, null, null, null, null};
    private static final int[][] CHORD_PRESET_INTERVALS = {
            null, {0, 4, 7}, {0, 3, 7}, {0, 0, 0}, null};
    private static final int[][] CHORD_PRESET_OCTAVES = {
            null, {0, 0, 0}, {0, 0, 0}, {0, 1, 2}, null};
    private static final double[][] CHORD_PRESET_ATTACKS = {
            {0, 3, 4}, {0, 1, 2}, {0, 1, 2}, {0, 2, 3}, null};
    private static final double[][] CHORD_PRESET_GAINS = {
            {1.0, 0.7, 0.5}, {1.0, 0.8, 0.7}, {1.0, 0.8, 0.7}, {1.0, 0.8, 0.6}, null};
    private static final double[][] CHORD_PRESET_PANS = {
            {0.0, -0.6, 0.6}, {0.0, -0.4, 0.4}, {0.0, -0.4, 0.4}, {0.0, -0.5, 0.5}, null};

    private JPanel voiceListPanel;
    private double cachedSourceRegionSec = 0;
    private int chordPresetIndex = 0;
    private boolean suppressPresetAutoSwitch = false;
    private Runnable chordModeChangeListener;

    public void setChordModeChangeListener(Runnable listener) {
        this.chordModeChangeListener = listener;
    }

    /** Update the cached source region duration (call when source file or region changes). */
    public void updateSourceDuration() {
        try {
            if (params.sourceFile != null && params.sourceFile.exists()) {
                javax.sound.sampled.AudioFileFormat fmt =
                        javax.sound.sampled.AudioSystem.getAudioFileFormat(params.sourceFile);
                long frames = fmt.getFrameLength();
                double sr = fmt.getFormat().getSampleRate();
                if (frames > 0 && sr > 0) {
                    double totalSec = frames / sr;
                    double regionFrac = params.sourceEndFraction - params.sourceStartFraction;
                    cachedSourceRegionSec = totalSec * Math.max(0.01, regionFrac);
                    return;
                }
            }
        } catch (Exception ignored) {}
        cachedSourceRegionSec = 0;
    }

    private JPanel buildChordSection() {
        JPanel card;
        JPanel content;

        if (Theme.isPaper()) {
            PmCard pmCard = new PmCard(PmCard.Variant.DEFAULT);
            content = new JPanel();
            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.add(paperSectionHeader("Chord Mode"));
            content.add(Box.createVerticalStrut(8));
            pmCard.add(content, BorderLayout.CENTER);
            card = pmCard;
        } else {
            card = sectionCard();
            content = card;
            content.add(Theme.sectionHeader("Chord Mode"));
            content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        }

        // Enable toggle
        ToggleSwitch chordEnableToggle = new ToggleSwitch(params.useChordMode);
        chordEnableToggle.addChangeListener(e -> {
            params.useChordMode = chordEnableToggle.isSelected();
            if (chordModeChangeListener != null) chordModeChangeListener.run();
        });
        JPanel toggleRow = Theme.toggleRow("Enable", chordEnableToggle);
        toggleRow.setAlignmentX(0);
        content.add(toggleRow);
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        syncActions.add(() -> chordEnableToggle.setSelected(params.useChordMode));

        // Preset selector
        SegmentedControl presetControl = new SegmentedControl(CHORD_PRESET_NAMES, chordPresetIndex);
        presetControl.setAlignmentX(0);
        presetControl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        content.add(presetControl);
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        // Voice list container
        voiceListPanel = new JPanel();
        voiceListPanel.setLayout(new BoxLayout(voiceListPanel, BoxLayout.Y_AXIS));
        voiceListPanel.setOpaque(false);
        voiceListPanel.setAlignmentX(0);
        content.add(voiceListPanel);

        // Rebuild voice rows from current params
        Runnable rebuildVoices = () -> rebuildVoiceList(voiceListPanel, presetControl);
        rebuildVoices.run();

        // Preset selection handler
        presetControl.addChangeListener(e -> {
            int idx = presetControl.getSelectedIndex();
            chordPresetIndex = idx;
            if (idx < CHORD_PRESET_NAMES.length - 1) { // not "Custom"
                suppressPresetAutoSwitch = true;
                params.chordOvertoneMode = CHORD_PRESET_OVERTONE[idx];
                if (params.chordOvertoneMode && CHORD_PRESET_HARMONICS[idx] != null) {
                    params.chordHarmonics = CHORD_PRESET_HARMONICS[idx].clone();
                } else if (CHORD_PRESET_INTERVALS[idx] != null) {
                    params.chordIntervals = CHORD_PRESET_INTERVALS[idx].clone();
                    params.chordOctaves = CHORD_PRESET_OCTAVES[idx].clone();
                }
                params.chordAttackTimes = CHORD_PRESET_ATTACKS[idx].clone();
                params.chordGains = CHORD_PRESET_GAINS[idx].clone();
                params.chordPans = CHORD_PRESET_PANS[idx].clone();
                rebuildVoices.run();
                suppressPresetAutoSwitch = false;
            }
        });

        // Add Voice button
        JButton addVoiceBtn = Theme.ghostButton("+ Add Voice");
        Theme.tagFont(addVoiceBtn, "small");
        addVoiceBtn.setAlignmentX(0);
        addVoiceBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        addVoiceBtn.addActionListener(e -> {
            if (params.chordOvertoneMode) {
                params.chordHarmonics = appendInt(params.chordHarmonics, 1);
            } else {
                params.chordIntervals = appendInt(params.chordIntervals, 0);
                params.chordOctaves = appendInt(params.chordOctaves, 0);
            }
            params.chordAttackTimes = appendDouble(params.chordAttackTimes, 0.0);
            params.chordGains = appendDouble(params.chordGains, 1.0);
            params.chordPans = appendDouble(params.chordPans, 0.0);
            switchToCustomPreset(presetControl);
            rebuildVoices.run();
        });
        content.add(addVoiceBtn);
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        // Options section
        JLabel optLabel = Theme.isPaper() ? paperLabel("Options") : Theme.paramLabel("Options");
        optLabel.setAlignmentX(0);
        content.add(optLabel);
        content.add(Box.createVerticalStrut(Theme.LABEL_GAP));

        ToggleSwitch palindromeToggle = new ToggleSwitch(params.chordHarmonicsPalindrome);
        palindromeToggle.addChangeListener(e -> params.chordHarmonicsPalindrome = palindromeToggle.isSelected());
        JPanel palindromeRow = Theme.toggleRow("Harmonics Palindrome", palindromeToggle);
        palindromeRow.setAlignmentX(0);
        content.add(palindromeRow);
        content.add(Box.createVerticalStrut(4));
        syncActions.add(() -> palindromeToggle.setSelected(params.chordHarmonicsPalindrome));

        ToggleSwitch softAttackToggle = new ToggleSwitch(params.chordSoftAttackFill);
        softAttackToggle.addChangeListener(e -> params.chordSoftAttackFill = softAttackToggle.isSelected());
        JPanel softAttackRow = Theme.toggleRow("Soft Attack Fill", softAttackToggle);
        softAttackRow.setAlignmentX(0);
        content.add(softAttackRow);
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        syncActions.add(() -> softAttackToggle.setSelected(params.chordSoftAttackFill));

        // Tuning mode
        String[] tuningModes = {"Just", "Equal"};
        int tuningIdx = "equal".equals(params.chordTuning) ? 1 : 0;
        SegmentedControl tuningControl = new SegmentedControl(tuningModes, tuningIdx);
        tuningControl.setAlignmentX(0);
        tuningControl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        tuningControl.addChangeListener(e ->
                params.chordTuning = tuningControl.getSelectedIndex() == 0 ? "just" : "equal");
        JLabel tuningLabel = Theme.isPaper() ? paperLabel("Tuning") : Theme.paramLabel("Tuning");
        tuningLabel.setAlignmentX(0);
        content.add(tuningLabel);
        content.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        content.add(tuningControl);
        content.add(Box.createVerticalStrut(Theme.CONTROL_GAP));
        syncActions.add(() -> tuningControl.setSelectedIndex("equal".equals(params.chordTuning) ? 1 : 0));

        // Envelope mode
        String[] envModes = {"Crisp", "Shared"};
        int envIdx = "shared".equals(params.chordEnvelopeMode) ? 1 : 0;
        SegmentedControl envModeControl = new SegmentedControl(envModes, envIdx);
        envModeControl.setAlignmentX(0);
        envModeControl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        envModeControl.addChangeListener(e ->
                params.chordEnvelopeMode = envModeControl.getSelectedIndex() == 0 ? "crisp" : "shared");
        JLabel envLabel = Theme.isPaper() ? paperLabel("Envelope Mode") : Theme.paramLabel("Envelope Mode");
        envLabel.setAlignmentX(0);
        content.add(envLabel);
        content.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        content.add(envModeControl);
        syncActions.add(() -> envModeControl.setSelectedIndex("shared".equals(params.chordEnvelopeMode) ? 1 : 0));

        // Sync action for full rebuild on preset load
        syncActions.add(() -> {
            suppressPresetAutoSwitch = true;
            rebuildVoices.run();
            suppressPresetAutoSwitch = false;
        });

        return card;
    }

    private void rebuildVoiceList(JPanel container, SegmentedControl presetControl) {
        container.removeAll();
        boolean overtone = params.chordOvertoneMode;
        int numVoices = overtone ? params.chordHarmonics.length : params.chordIntervals.length;
        // Pad arrays to match
        if (!overtone) params.chordOctaves = padIntArray(params.chordOctaves, numVoices, 0);
        params.chordAttackTimes = padArray(params.chordAttackTimes, numVoices, 0.0);
        params.chordGains = padArray(params.chordGains, numVoices, 1.0);
        params.chordPans = padArray(params.chordPans, numVoices, 0.0);

        for (int v = 0; v < numVoices; v++) {
            final int voiceIdx = v;

            JPanel voiceBlock = new JPanel();
            voiceBlock.setLayout(new BoxLayout(voiceBlock, BoxLayout.Y_AXIS));
            voiceBlock.setOpaque(false);
            voiceBlock.setAlignmentX(0);

            // Header: "Voice 1  (2.3s)" with remove button
            JPanel header = new JPanel(new BorderLayout(4, 0));
            header.setOpaque(false);
            header.setAlignmentX(0);
            header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

            // Compute duration for this voice
            double voiceRatio;
            if (overtone) {
                voiceRatio = Math.max(1, params.chordHarmonics[voiceIdx]);
            } else {
                int semi = params.chordIntervals[voiceIdx];
                int oct = (voiceIdx < params.chordOctaves.length) ? params.chordOctaves[voiceIdx] : 0;
                if ("equal".equals(params.chordTuning)) {
                    voiceRatio = Math.pow(2, semi / 12.0) * Math.pow(2, oct);
                } else {
                    voiceRatio = SynthParameters.JI_RATIOS[Math.max(0, Math.min(11, semi))] * Math.pow(2, oct);
                }
            }
            String durStr = "";
            if (cachedSourceRegionSec > 0 && voiceRatio > 0) {
                double voiceSec = cachedSourceRegionSec / voiceRatio;
                durStr = "  " + formatDur(voiceSec);
            }

            JPanel headerLeft = new JPanel();
            headerLeft.setLayout(new BoxLayout(headerLeft, BoxLayout.X_AXIS));
            headerLeft.setOpaque(false);

            JLabel voiceLabel = new JLabel("Voice " + (v + 1));
            Theme.tagFont(voiceLabel, "small");
            Theme.tagFg(voiceLabel, "fgMuted");
            headerLeft.add(voiceLabel);

            if (durStr.length() > 0) {
                JLabel durLabel = new JLabel(durStr);
                Theme.tagFont(durLabel, "small");
                Theme.tagFg(durLabel, "fgDim");
                headerLeft.add(durLabel);
            }
            header.add(headerLeft, BorderLayout.WEST);

            JButton removeBtn = Theme.ghostButton("\u00D7");
            removeBtn.setPreferredSize(new Dimension(20, 20));
            removeBtn.setMargin(new Insets(0, 0, 0, 0));
            removeBtn.setEnabled(numVoices > 1);
            removeBtn.setFocusable(false);
            removeBtn.addActionListener(e -> {
                if (params.chordOvertoneMode) {
                    params.chordHarmonics = removeIntAt(params.chordHarmonics, voiceIdx);
                } else {
                    params.chordIntervals = removeIntAt(params.chordIntervals, voiceIdx);
                    params.chordOctaves = removeIntAt(params.chordOctaves, voiceIdx);
                }
                params.chordAttackTimes = removeAt(params.chordAttackTimes, voiceIdx);
                params.chordGains = removeAt(params.chordGains, voiceIdx);
                params.chordPans = removeAt(params.chordPans, voiceIdx);
                switchToCustomPreset(presetControl);
                rebuildVoiceList(container, presetControl);
            });
            header.add(removeBtn, BorderLayout.EAST);
            voiceBlock.add(header);
            voiceBlock.add(Box.createVerticalStrut(2));

            if (overtone) {
                // Overtone mode: single Harmonic stepper (1-16)
                StepperControl harmonicStepper = new StepperControl(params.chordHarmonics[voiceIdx], 1, 16, 1);
                harmonicStepper.setAlignmentX(0);
                voiceBlock.add(labeledRow("Harmonic", harmonicStepper));
                voiceBlock.add(Box.createVerticalStrut(2));

                harmonicStepper.addChangeListener(e -> {
                    params.chordHarmonics[voiceIdx] = harmonicStepper.getIntValue();
                    switchToCustomPreset(presetControl);
                });
            } else {
                // Interval mode: Interval + Octave steppers
                StepperControl intervalStepper = new StepperControl(params.chordIntervals[voiceIdx], 0, 11, 1);
                intervalStepper.setLabels(INTERVAL_NAMES);
                intervalStepper.setAlignmentX(0);
                voiceBlock.add(labeledRow("Interval", intervalStepper));
                voiceBlock.add(Box.createVerticalStrut(2));

                StepperControl octaveStepper = new StepperControl(params.chordOctaves[voiceIdx], 0, 3, 1);
                octaveStepper.setAlignmentX(0);
                voiceBlock.add(labeledRow("Octave", octaveStepper));
                voiceBlock.add(Box.createVerticalStrut(2));

                intervalStepper.addChangeListener(e -> {
                    params.chordIntervals[voiceIdx] = intervalStepper.getIntValue();
                    switchToCustomPreset(presetControl);
                });
                octaveStepper.addChangeListener(e -> {
                    params.chordOctaves[voiceIdx] = octaveStepper.getIntValue();
                    switchToCustomPreset(presetControl);
                });
            }

            // Attack
            StepperControl attackStepper = new StepperControl(params.chordAttackTimes[voiceIdx], 0, 120, 0.1, "%.1f s");
            attackStepper.setAlignmentX(0);
            voiceBlock.add(labeledRow("Attack", attackStepper));
            voiceBlock.add(Box.createVerticalStrut(2));

            // Gain
            StepperControl gainStepper = new StepperControl(params.chordGains[voiceIdx], 0, 1.0, 0.05, "%.2f");
            gainStepper.setAlignmentX(0);
            voiceBlock.add(labeledRow("Gain", gainStepper));
            voiceBlock.add(Box.createVerticalStrut(2));

            // Pan
            StepperControl panStepper = new StepperControl(params.chordPans[voiceIdx], -1.0, 1.0, 0.1, "%+.1f");
            panStepper.setAlignmentX(0);
            voiceBlock.add(labeledRow("Pan", panStepper));

            container.add(voiceBlock);
            if (v < numVoices - 1) {
                container.add(Box.createVerticalStrut(8));
            }

            // Wire shared change listeners
            attackStepper.addChangeListener(e -> {
                params.chordAttackTimes[voiceIdx] = attackStepper.getDoubleValue();
                switchToCustomPreset(presetControl);
            });
            gainStepper.addChangeListener(e -> {
                params.chordGains[voiceIdx] = gainStepper.getDoubleValue();
                switchToCustomPreset(presetControl);
            });
            panStepper.addChangeListener(e -> {
                params.chordPans[voiceIdx] = panStepper.getDoubleValue();
                switchToCustomPreset(presetControl);
            });
        }

        container.revalidate();
        container.repaint();
        // Propagate revalidation up through the card and scroll pane
        java.awt.Container parent = container.getParent();
        while (parent != null) {
            parent.revalidate();
            if (parent instanceof javax.swing.JScrollPane) break;
            parent = parent.getParent();
        }
    }

    private void switchToCustomPreset(SegmentedControl presetControl) {
        if (!suppressPresetAutoSwitch) {
            chordPresetIndex = CHORD_PRESET_NAMES.length - 1; // "Custom"
            presetControl.setSelectedIndex(chordPresetIndex);
        }
    }

    private static int[] appendInt(int[] arr, int val) {
        int[] result = new int[arr.length + 1];
        System.arraycopy(arr, 0, result, 0, arr.length);
        result[arr.length] = val;
        return result;
    }

    private static int[] removeIntAt(int[] arr, int index) {
        if (arr.length <= 1) return arr;
        int[] result = new int[arr.length - 1];
        System.arraycopy(arr, 0, result, 0, index);
        System.arraycopy(arr, index + 1, result, index, arr.length - index - 1);
        return result;
    }

    private static int[] padIntArray(int[] arr, int targetLen, int defaultVal) {
        if (arr.length >= targetLen) return arr;
        int[] result = new int[targetLen];
        System.arraycopy(arr, 0, result, 0, arr.length);
        for (int i = arr.length; i < targetLen; i++) result[i] = defaultVal;
        return result;
    }

    private static double[] appendDouble(double[] arr, double val) {
        double[] result = new double[arr.length + 1];
        System.arraycopy(arr, 0, result, 0, arr.length);
        result[arr.length] = val;
        return result;
    }

    private static double[] removeAt(double[] arr, int index) {
        if (arr.length <= 1) return arr;
        double[] result = new double[arr.length - 1];
        System.arraycopy(arr, 0, result, 0, index);
        System.arraycopy(arr, index + 1, result, index, arr.length - index - 1);
        return result;
    }

    /** Compact label + control row for voice parameters. */
    private static JPanel labeledRow(String text, javax.swing.JComponent control) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setAlignmentX(0);
        JLabel label = new JLabel(text);
        Theme.tagFont(label, "small");
        Theme.tagFg(label, "fgDim");
        label.setPreferredSize(new Dimension(38, 36));
        row.add(label, BorderLayout.WEST);
        row.add(control, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return row;
    }

    private static String formatDur(double seconds) {
        if (seconds < 60) return String.format("%.1fs", seconds);
        int min = (int) (seconds / 60);
        double sec = seconds - min * 60;
        return String.format("%d:%04.1f", min, sec);
    }

    private static JLabel smallDimLabel(String text) {
        JLabel label = new JLabel(text);
        Theme.tagFont(label, "small");
        Theme.tagFg(label, "fgDim");
        return label;
    }

    private static double[] padArray(double[] arr, int targetLen, double defaultVal) {
        if (arr.length >= targetLen) return arr;
        double[] result = new double[targetLen];
        System.arraycopy(arr, 0, result, 0, arr.length);
        for (int i = arr.length; i < targetLen; i++) result[i] = defaultVal;
        return result;
    }

    // ── Sample preview ──

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
                int s = 6; // half-size of icon

                if ("stop".equals(getName())) {
                    // filled square
                    g2.fillRect(cx - s, cy - s, s * 2, s * 2);
                } else {
                    // filled triangle pointing right
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

    private JPanel sampleRow(SampleDropPanel panel) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.add(panel, BorderLayout.CENTER);

        JButton playBtn = createPlayButton();
        playBtn.addActionListener(e -> toggleSamplePreview(playBtn, panel));
        // Wrap in a box so BorderLayout.EAST doesn't stretch the button vertically
        Box btnBox = Box.createVerticalBox();
        btnBox.add(Box.createVerticalGlue());
        btnBox.add(playBtn);
        btnBox.add(Box.createVerticalGlue());
        row.add(btnBox, BorderLayout.EAST);

        row.setAlignmentX(0);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        return row;
    }

    private void setPlayIcon(JButton btn, boolean playing) {
        btn.setName(playing ? "stop" : "play");
        btn.repaint();
    }

    private void toggleSamplePreview(JButton btn, SampleDropPanel panel) {
        // If this button is currently playing, stop it
        if (currentPreviewBtn == btn) {
            previewPlayer.stop();
            setPlayIcon(btn, false);
            currentPreviewBtn = null;
            return;
        }
        // If another button is playing, stop it first
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
        this.sourceFileChangeListener = listener;
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

    public void syncFromParams() {
        for (Runnable action : syncActions) action.run();
    }
}
