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
 * Left panel for Live mode — shows only the parameters relevant
 * to real-time MIDI playback plus live density/mix controls.
 */
public class LiveParameterPanel extends JPanel implements Scrollable {

    private final SynthParameters params;

    private SampleDropPanel sourceDropPanel;
    private SampleDropPanel grainDropPanel;
    private StepperControl refFreqStepper;
    private SegmentedControl windowSizeSegmented;
    private StepperControl controlRateStepper;
    private LabeledSlider densitySlider;
    private LabeledSlider mixSlider;

    private LiveMidiController liveController;
    private Timer syncTimer;
    private boolean syncing;

    public LiveParameterPanel(SynthParameters params) {
        this.params = params;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildSamplesSection());
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildSynthesisSection());
        add(Box.createVerticalStrut(Theme.SECTION_GAP));
        add(buildLiveControlsSection());
        add(Box.createVerticalGlue());
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

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    public void setLiveController(LiveMidiController controller) {
        this.liveController = controller;
    }

    /** Start polling ControlState to sync sliders when MIDI CC changes them externally. */
    public void startSync() {
        if (syncTimer != null) syncTimer.stop();
        syncTimer = new Timer(100, e -> {
            if (liveController == null || liveController.getControlState() == null) return;
            ControlState cs = liveController.getControlState();

            int densityVal = (int) (cs.getDensity() * 100);
            int mixVal = (int) (cs.getMix() * 100);

            syncing = true;
            try {
                if (densitySlider.getValue() != densityVal) {
                    densitySlider.setValue(densityVal);
                }
                if (mixSlider.getValue() != mixVal) {
                    mixSlider.setValue(mixVal);
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
                new Theme.RoundedBorder(Theme.BORDER, Theme.RADIUS_LG, new Insets(0, 0, 0, 0)),
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
