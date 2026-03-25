package org.delightofcomposition.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.Timer;

import org.delightofcomposition.realtime.LiveMidiController;
import org.delightofcomposition.realtime.Voice;

/**
 * Right panel for Live mode — MIDI status, piano keyboard, and activity monitor.
 */
public class LiveMonitorPanel extends JPanel implements Scrollable {

    private final JLabel midiDeviceLabel;
    private final JLabel midiStatusDot;
    private final JLabel voiceCountLabel;
    private final PianoKeyboard keyboard;
    private final JLabel octaveLabel;
    private final VoiceActivityPanel activityPanel;

    private LiveMidiController liveController;
    private Timer pollTimer;

    public LiveMonitorPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // ── MIDI Status Card ──
        JPanel midiCard = sectionCard();
        midiCard.add(Theme.sectionHeader("MIDI"));
        midiCard.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        JPanel midiRow = new JPanel(new BorderLayout(8, 0));
        midiRow.setOpaque(false);
        midiRow.setAlignmentX(0);

        midiStatusDot = new JLabel("\u25CF"); // filled circle
        midiStatusDot.setFont(Theme.FONT_BASE);
        midiStatusDot.setForeground(Theme.AMBER);
        midiRow.add(midiStatusDot, BorderLayout.WEST);

        midiDeviceLabel = Theme.valueLabel("No device");
        midiRow.add(midiDeviceLabel, BorderLayout.CENTER);

        midiRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        midiCard.add(midiRow);
        midiCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, midiCard.getPreferredSize().height));
        add(midiCard);
        add(Box.createVerticalStrut(Theme.SECTION_GAP));

        // ── Piano Keyboard ──
        JPanel keyboardCard = sectionCard();
        keyboardCard.add(Theme.sectionHeader("Keyboard"));
        keyboardCard.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        keyboard = new PianoKeyboard();
        keyboard.setAlignmentX(0);
        keyboard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        keyboardCard.add(keyboard);
        keyboardCard.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        // Octave shifter row
        JPanel octaveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        octaveRow.setOpaque(false);
        octaveRow.setAlignmentX(0);

        JButton octDownBtn = createOctaveButton(true);
        octDownBtn.setToolTipText("Octave down (Z)");
        octDownBtn.addActionListener(e -> keyboard.setOctaveShift(keyboard.getOctaveShift() - 1));
        octaveRow.add(octDownBtn);

        octaveLabel = Theme.valueLabel("Octave: 0");
        octaveRow.add(octaveLabel);

        JButton octUpBtn = createOctaveButton(false);
        octUpBtn.setToolTipText("Octave up (X)");
        octUpBtn.addActionListener(e -> keyboard.setOctaveShift(keyboard.getOctaveShift() + 1));
        octaveRow.add(octUpBtn);

        octaveRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        keyboardCard.add(octaveRow);

        // Listen for octave changes from keyboard shortcuts (Z/X keys)
        keyboard.addPropertyChangeListener("octaveShift", evt ->
                octaveLabel.setText("Octave: " + keyboard.getOctaveShift()));

        keyboardCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, keyboardCard.getPreferredSize().height));
        add(keyboardCard);
        add(Box.createVerticalStrut(Theme.SECTION_GAP));

        // ── Activity Monitor ──
        JPanel activityCard = sectionCard();
        activityCard.add(Theme.sectionHeader("Activity"));
        activityCard.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        voiceCountLabel = Theme.valueLabel("Voices: 0/16");
        voiceCountLabel.setAlignmentX(0);
        activityCard.add(voiceCountLabel);
        activityCard.add(Box.createVerticalStrut(Theme.CONTROL_GAP));

        activityPanel = new VoiceActivityPanel();
        activityPanel.setAlignmentX(0);
        activityPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        activityCard.add(activityPanel);

        activityCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, activityCard.getPreferredSize().height));
        add(activityCard);
        add(Box.createVerticalGlue());

        registerHelpTexts();
    }

    private void registerHelpTexts() {
        HelpManager help = HelpManager.getInstance();
        help.register(keyboard,
                "Play notes with your computer keyboard: A-; for white keys, W E T Y U O P for black keys. "
                + "Or click directly on the keys.");
        help.register(octaveLabel,
                "Current octave offset for the computer keyboard. Press Z to shift down, X to shift up.");
        help.register(voiceCountLabel,
                "Number of active synthesis voices out of the 16 available. Each held note uses one voice.");
        help.register(activityPanel,
                "Visual indicator of which of the 16 polyphonic voices are currently active.");
        help.register(midiDeviceLabel,
                "The connected MIDI input device. If no hardware controller is found, use the computer keyboard instead.");
    }

    public void setLiveController(LiveMidiController controller) {
        this.liveController = controller;
        keyboard.setVoices(controller.getVoices());
        keyboard.setSourceFundamentalHz(controller.getSourceFundamentalHz());

        // Update MIDI status
        String deviceName = controller.getMidiDeviceName();
        midiDeviceLabel.setText(deviceName);
        boolean connected = !"No MIDI device".equals(deviceName);
        midiStatusDot.setForeground(connected ? Theme.SUCCESS : Theme.AMBER);
    }

    /** Start 100ms polling timer for activity display. */
    public void startPolling() {
        if (pollTimer != null) pollTimer.stop();
        pollTimer = new Timer(100, e -> {
            if (liveController == null || !liveController.isRunning()) return;

            int voices = liveController.getActiveVoiceCount();
            int maxV = liveController.getMaxVoices();

            voiceCountLabel.setText("Voices: " + voices + "/" + maxV);

            activityPanel.repaint();
            keyboard.repaint();
        });
        pollTimer.start();
    }

    /** Stop polling timer. */
    public void stopPolling() {
        if (pollTimer != null) {
            pollTimer.stop();
            pollTimer = null;
        }
    }

    /** Reset display to idle state. */
    public void reset() {
        stopPolling();
        midiDeviceLabel.setText("No device");
        midiStatusDot.setForeground(Theme.AMBER);
        voiceCountLabel.setText("Voices: 0/16");
        keyboard.setVoices(null);
        activityPanel.repaint();
        keyboard.repaint();
    }

    /**
     * Creates a painted icon button for octave shifting, matching the
     * preview-sample play button style. Left = triangle pointing left,
     * Right = triangle pointing right.
     */
    private JButton createOctaveButton(boolean left) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(getModel().isPressed() ? Theme.ZINC_700 : Theme.BG_MUTED);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.RADIUS, Theme.RADIUS);
                }

                g2.setColor(isEnabled() ? Theme.FG : Theme.ZINC_600);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int s = 5;

                if (left) {
                    int[] xs = {cx + s, cx + s, cx - s};
                    int[] ys = {cy - s, cy + s, cy};
                    g2.fillPolygon(xs, ys, 3);
                } else {
                    int[] xs = {cx - s, cx - s, cx + s};
                    int[] ys = {cy - s, cy + s, cy};
                    g2.fillPolygon(xs, ys, 3);
                }
                g2.dispose();
            }
        };
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(32, 28));
        btn.setMinimumSize(new Dimension(32, 28));
        btn.setMaximumSize(new Dimension(32, 28));
        return btn;
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

    /**
     * Row of 16 small bars showing per-voice activity.
     */
    private class VoiceActivityPanel extends JPanel {
        VoiceActivityPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(200, 40));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int maxVoices = 16;
            int gap = 3;
            int barWidth = (getWidth() - gap * (maxVoices - 1)) / maxVoices;
            if (barWidth < 2) barWidth = 2;
            int barHeight = getHeight();

            Voice[] voices = (liveController != null) ? liveController.getVoices() : null;

            for (int i = 0; i < maxVoices; i++) {
                int x = i * (barWidth + gap);
                boolean active = (voices != null && i < voices.length && voices[i].isActive());

                // Background bar
                g2.setColor(Theme.BG_MUTED);
                g2.fillRoundRect(x, 0, barWidth, barHeight, 4, 4);

                // Active fill
                if (active) {
                    g2.setColor(Theme.ACCENT);
                    g2.fillRoundRect(x, 0, barWidth, barHeight, 4, 4);
                }
            }

            g2.dispose();
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
