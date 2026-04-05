package org.delightofcomposition.gui;

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
import javax.swing.Scrollable;
import javax.swing.Timer;

import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.realtime.ControlState;
import org.delightofcomposition.realtime.LiveMidiController;

/**
 * Left panel for Live mode — synthesis parameters (pre-render)
 * plus real-time density/mix/pan controls.
 */
public class LiveParameterPanel extends JPanel implements Scrollable {

    private final SynthParameters params;

    // Samples
    private SampleDropPanel sourceDropPanel;
    private SampleDropPanel grainDropPanel;
    private StepperControl refFreqStepper;
    private SampleDropPanel irDropPanel;

    // Synthesis
    private SegmentedControl windowSizeSegmented;
    private StepperControl controlRateStepper;
    private StepperControl grainsPerPeakStepper;
    private LabeledSlider ampThresholdSlider;

    // Reverb
    private ToggleSwitch useReverbToggle;
    private LabeledSlider sourceReverbSlider;
    private LabeledSlider synthReverbSlider;

    // Live controls
    private LabeledSlider densitySlider;
    private LabeledSlider mixSlider;
    private LabeledSlider panSlider;
    private LabeledSlider dramaticSlider;

    private LiveMidiController liveController;
    private Timer syncTimer;
    private boolean syncing;

    public LiveParameterPanel(SynthParameters params) {
        this.params = params;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        Theme.tagBg(this, "bg");
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildSamplesSection());
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildSynthesisSection());
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildReverbSection());
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildLiveControlsSection());
        add(Box.createVerticalGlue());

        registerHelpTexts();
    }

    private void registerHelpTexts() {
        HelpManager help = HelpManager.getInstance();
        help.register(sourceDropPanel,
                "The audio file whose spectral content will be analyzed and resynthesized as granular texture.");
        help.register(grainDropPanel,
                "The short audio sample used as the building block for granular synthesis. Its timbre colors the output.");
        help.register(refFreqStepper,
                "The fundamental frequency (Hz) of the grain sample. Used to tune grains to match spectral peaks.");
        help.register(irDropPanel,
                "An impulse response recording used for convolution reverb, placing the sound in a virtual space.");
        help.register(windowSizeSegmented,
                "FFT window size: larger = better frequency resolution but smears timing. Smaller = better timing but coarser frequency.");
        help.register(controlRateStepper,
                "Time between analysis windows. Lower = more detail but longer render. Higher = faster but less smooth.");
        help.register(grainsPerPeakStepper,
                "How many grain copies per spectral peak. More = denser/richer texture, fewer = sparser/clearer.");
        help.register(ampThresholdSlider,
                "Minimum amplitude for a spectral peak to trigger grains. Higher = only loud peaks, lower = more detail.");
        help.register(useReverbToggle,
                "Apply convolution reverb using the impulse response sample to add spatial depth.");
        help.register(sourceReverbSlider,
                "How much reverb to apply to the original source audio portion of the mix.");
        help.register(synthReverbSlider,
                "How much reverb to apply to the granular synthesized portion of the mix.");
        help.register(densitySlider,
                "Controls how many granular layers are audible. Use the up/down arrow keys or MIDI CC#74 as shortcuts.");
        help.register(mixSlider,
                "Blends between the granular texture (0%) and the original source sample (100%). "
                + "Use the left/right arrow keys or MIDI mod wheel as shortcuts.");
        help.register(panSlider,
                "Stereo position of the output. Left = 0%, Center = 50%, Right = 100%. Also controllable via MIDI CC#10.");
        help.register(dramaticSlider,
                "Exaggerates amplitude dynamics across the playback position. Higher = more contrast and drama.");
    }

    private JPanel buildSamplesSection() {
        JPanel card = sectionCard();

        card.add(Theme.sectionHeader("Samples"));
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        sourceDropPanel = new SampleDropPanel("Source Sample", params.sourceFile,
                file -> params.sourceFile = file);
        sourceDropPanel.setAlignmentX(0);
        card.add(sourceDropPanel);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        grainDropPanel = new SampleDropPanel("Grain Sample", params.grainFile,
                file -> params.grainFile = file);
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
        card.add(refFreqStepper);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        irDropPanel = new SampleDropPanel("Impulse Response", params.impulseResponseFile,
                file -> params.impulseResponseFile = file);
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
        card.add(windowSizeSegmented);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        JLabel crLabel = Theme.paramLabel("Control Rate");
        crLabel.setAlignmentX(0);
        card.add(crLabel);
        card.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        controlRateStepper = new StepperControl(params.controlRate, 0.01, 1.0, 0.01, "%.2f s");
        controlRateStepper.setAlignmentX(0);
        controlRateStepper.addChangeListener(e -> params.controlRate = controlRateStepper.getDoubleValue());
        card.add(controlRateStepper);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        JLabel gpLabel = Theme.paramLabel("Grains / Peak");
        gpLabel.setAlignmentX(0);
        card.add(gpLabel);
        card.add(Box.createVerticalStrut(Theme.LABEL_GAP));
        grainsPerPeakStepper = new StepperControl(params.grainsPerPeak, 1, 50, 1);
        grainsPerPeakStepper.setAlignmentX(0);
        grainsPerPeakStepper.addChangeListener(e -> params.grainsPerPeak = grainsPerPeakStepper.getIntValue());
        card.add(grainsPerPeakStepper);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        ampThresholdSlider = new LabeledSlider("Amp Threshold", 1, 500,
                (int) (params.amplitudeThreshold * 1000),
                v -> String.format("%.3f", v / 1000.0));
        ampThresholdSlider.setAlignmentX(0);
        ampThresholdSlider.addChangeListener(e ->
                params.amplitudeThreshold = ampThresholdSlider.getValue() / 1000.0);
        card.add(ampThresholdSlider);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        dramaticSlider = new LabeledSlider("Dramatic", 1, 2000,
                (int) (params.dramaticFactor * 100),
                v -> String.format("%.2f", v / 100.0));
        dramaticSlider.setAlignmentX(0);
        dramaticSlider.addChangeListener(e ->
                params.dramaticFactor = dramaticSlider.getValue() / 100.0);
        card.add(dramaticSlider);

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
        card.add(toggleRow);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        sourceReverbSlider = new LabeledSlider("Source Mix", 0, 100,
                (int) (params.sourceReverbMix * 100), v -> v + "%");
        sourceReverbSlider.setAlignmentX(0);
        sourceReverbSlider.addChangeListener(e ->
                params.sourceReverbMix = sourceReverbSlider.getValue() / 100.0);
        card.add(sourceReverbSlider);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        synthReverbSlider = new LabeledSlider("Synth Mix", 0, 100,
                (int) (params.synthReverbMix * 100), v -> v + "%");
        synthReverbSlider.setAlignmentX(0);
        synthReverbSlider.addChangeListener(e ->
                params.synthReverbMix = synthReverbSlider.getValue() / 100.0);
        card.add(synthReverbSlider);

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JPanel buildLiveControlsSection() {
        JPanel card = sectionCard();

        card.add(Theme.sectionHeader("Live Controls"));
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        densitySlider = new LabeledSlider("Density", 0, 100, 50, v -> v + "%");
        densitySlider.setAlignmentX(0);
        densitySlider.addChangeListener(e -> {
            if (syncing) return;
            if (liveController != null && liveController.getControlState() != null) {
                liveController.getControlState().setDensity(densitySlider.getValue() / 100.0);
            }
        });
        card.add(densitySlider);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        mixSlider = new LabeledSlider("Mix", 0, 100, 0, v -> v + "%");
        mixSlider.setAlignmentX(0);
        mixSlider.addChangeListener(e -> {
            if (syncing) return;
            if (liveController != null && liveController.getControlState() != null) {
                liveController.getControlState().setMix(mixSlider.getValue() / 100.0);
            }
        });
        card.add(mixSlider);
        card.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        panSlider = new LabeledSlider("Pan", 0, 100, 50,
                v -> v < 45 ? "L " + (50 - v) : v > 55 ? "R " + (v - 50) : "C");
        panSlider.setAlignmentX(0);
        panSlider.addChangeListener(e -> {
            if (syncing) return;
            if (liveController != null && liveController.getControlState() != null) {
                liveController.getControlState().setPan(panSlider.getValue() / 100.0);
            }
        });
        card.add(panSlider);

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    public void setLiveController(LiveMidiController controller) {
        this.liveController = controller;
    }

    /** Start polling ControlState to sync sliders when MIDI CC changes them externally. */
    public void startSync() {
        if (syncTimer != null) syncTimer.stop();
        syncTimer = new Timer(250, e -> {
            if (liveController == null || liveController.getControlState() == null) return;
            ControlState cs = liveController.getControlState();

            int densityVal = (int) (cs.getDensity() * 100);
            int mixVal = (int) (cs.getMix() * 100);
            int panVal = (int) (cs.getPan() * 100);

            syncing = true;
            try {
                if (densitySlider.getValue() != densityVal) {
                    densitySlider.setValue(densityVal);
                }
                if (mixSlider.getValue() != mixVal) {
                    mixSlider.setValue(mixVal);
                }
                if (panSlider.getValue() != panVal) {
                    panSlider.setValue(panVal);
                }
            } finally {
                syncing = false;
            }
        });
        syncTimer.start();
    }

    /** Stop polling. */
    public void stopSync() {
        if (syncTimer != null) {
            syncTimer.stop();
            syncTimer = null;
        }
    }

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
