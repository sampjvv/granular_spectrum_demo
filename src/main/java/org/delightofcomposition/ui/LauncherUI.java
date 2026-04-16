package org.delightofcomposition.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;

import org.delightofcomposition.Main;
import org.delightofcomposition.RealtimeMain;
import org.delightofcomposition.realtime.AudioEngine;

public class LauncherUI {

    private JFrame frame;
    private AudioEngine activeEngine;

    public void show() {
        SwingUtilities.invokeLater(this::createAndShowGUI);
    }

    private void createAndShowGUI() {
        frame = new JFrame("Granular Spectrum Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Title
        JLabel title = new JLabel("Granular Spectrum Demo");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setHorizontalAlignment(JLabel.CENTER);
        mainPanel.add(title, BorderLayout.NORTH);

        // Mode selection
        JPanel modePanel = new JPanel(new GridLayout(2, 1, 5, 5));
        modePanel.setBorder(BorderFactory.createTitledBorder("Select Mode"));

        JRadioButton wavMode = new JRadioButton("WAV Render Mode (offline processing)");
        JRadioButton midiMode = new JRadioButton("Real-Time MIDI Mode (live playback)", true);

        ButtonGroup group = new ButtonGroup();
        group.add(wavMode);
        group.add(midiMode);

        modePanel.add(wavMode);
        modePanel.add(midiMode);
        mainPanel.add(modePanel, BorderLayout.CENTER);

        // Launch button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton launchButton = new JButton("Launch");
        launchButton.setFont(new Font("SansSerif", Font.BOLD, 14));

        launchButton.addActionListener(e -> {
            frame.dispose();
            if (wavMode.isSelected()) {
                launchWavMode();
            } else {
                launchMidiMode();
            }
        });

        buttonPanel.add(launchButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        frame.setContentPane(mainPanel);
        frame.setVisible(true);
    }

    private void launchWavMode() {
        System.out.println("Launching WAV Render Mode...");
        new Thread(() -> {
            Main.main(new String[]{});
            System.out.println("WAV rendering complete.");
        }, "WavRenderer").start();
    }

    private void launchMidiMode() {
        System.out.println("Launching Real-Time MIDI Mode...");
        new Thread(() -> {
            activeEngine = RealtimeMain.start(
                    "../samples/Cello/bowedCello1.wav",
                    "../samples/bell.wav",
                    1287);
        }, "MidiLauncher").start();
    }

    public void stopEngine() {
        if (activeEngine != null) {
            activeEngine.stop();
        }
    }
}
