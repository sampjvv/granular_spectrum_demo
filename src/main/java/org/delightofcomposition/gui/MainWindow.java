package org.delightofcomposition.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.delightofcomposition.RenderController;
import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.realtime.LiveMidiController;
import org.delightofcomposition.sound.AudioPlayer;
import org.delightofcomposition.sound.ReadSound;
import org.delightofcomposition.sound.WaveWriter;

/**
 * Top-level JFrame for the Granular Spectrum Synthesizer GUI.
 */
public class MainWindow extends JFrame {

    private static final String WAV_MODE = "WAV";
    private static final String LIVE_MODE = "Live";

    private final SynthParameters params = new SynthParameters();
    private final JProgressBar progressBar;
    private final AudioPlayer player = new AudioPlayer();
    private final RenderController renderController;

    // WAV mode controls
    private JButton renderBtn;
    private JButton playBtn;
    private JButton stopBtn;
    private JButton exportBtn;

    private float[][] renderedBuffer = null;

    private ParameterPanel parameterPanel;
    private EnvelopeEditorPanel envelopePanel;
    private WaveformDisplay waveformDisplay;
    private JButton helpBtn;
    private JPanel toolbar;

    // Live mode controls
    private LiveMidiController liveController;
    private SegmentedControl modeSwitch;
    private JPanel toolbarCards;
    private JPanel contentCards;
    private CardLayout toolbarCardLayout;
    private CardLayout contentCardLayout;
    private LiveParameterPanel liveParamPanel;
    private LiveMonitorPanel liveMonitorPanel;
    private JButton liveStartBtn;
    private JButton liveStopBtn;
    private JLabel midiStatusLabel;
    private JLabel voiceCountLabel;
    private JProgressBar liveProgressBar;
    private Timer liveStatusTimer;

    public MainWindow() {
        super("Granular Spectrum Synthesizer");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);

        liveController = new LiveMidiController();

        progressBar = Theme.styledProgressBar();

        renderController = new RenderController(progressBar, new RenderController.RenderCallback() {
            @Override
            public void onComplete(float[][] stereoBuffer) {
                renderedBuffer = stereoBuffer;
                renderBtn.setEnabled(true);
                playBtn.setEnabled(true);
                exportBtn.setEnabled(true);
                progressBar.setValue(100);
                progressBar.setString("Done");
                if (waveformDisplay != null) {
                    waveformDisplay.setBuffer(stereoBuffer);
                }
            }

            @Override
            public void onError(String message) {
                renderBtn.setEnabled(true);
                progressBar.setString("Error");
                JOptionPane.showMessageDialog(MainWindow.this,
                        "Render error: " + message, "Error", JOptionPane.ERROR_MESSAGE);
            }

            @Override
            public void onCancelled() {
                renderBtn.setEnabled(true);
                progressBar.setValue(0);
                progressBar.setString("Cancelled");
            }
        });

        // Restore last session's sample files
        params.sourceFile = SamplePreferences.loadSourceFile(params.sourceFile);
        params.grainFile = SamplePreferences.loadGrainFile(params.grainFile);
        params.impulseResponseFile = SamplePreferences.loadImpulseResponseFile(params.impulseResponseFile);

        buildMenuBar();
        buildToolbar();
        buildMainContent();
        setupKeyboardShortcuts();

        playBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        exportBtn.setEnabled(false);
        progressBar.setString("Ready");

        // Window close: stop live engine if running
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopLiveMode();
                dispose();
                System.exit(0);
            }
        });
    }

    private void buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem exportItem = new JMenuItem("Export WAV...");
        exportItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK));
        exportItem.addActionListener(e -> doExport());
        fileMenu.add(exportItem);

        fileMenu.addSeparator();

        JMenuItem savePreset = new JMenuItem("Save Preset...");
        savePreset.addActionListener(e -> doSavePreset());
        fileMenu.add(savePreset);

        JMenuItem loadPreset = new JMenuItem("Load Preset...");
        loadPreset.addActionListener(e -> doLoadPreset());
        fileMenu.add(loadPreset);

        fileMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            stopLiveMode();
            System.exit(0);
        });
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);

        JMenu themeMenu = new JMenu("Theme");
        ButtonGroup themeGroup = new ButtonGroup();
        for (ThemePreset preset : ThemePreset.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(preset.displayName);
            item.setSelected(preset == Theme.getPreset());
            item.addActionListener(e -> {
                Theme.applyTheme(preset);
                SwingUtilities.updateComponentTreeUI(this);
                getContentPane().setBackground(Theme.BG);
                toolbar.setBackground(Theme.BG_CARD);
                repaint();
                ThemePreferences.save(preset);
            });
            themeGroup.add(item);
            themeMenu.add(item);
        }
        viewMenu.add(themeMenu);

        menuBar.add(viewMenu);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Granular Spectrum Synthesizer\n" +
                        "Transforms source samples into spectral textures\n" +
                        "using granular synthesis with configurable envelopes.",
                "About", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private void buildToolbar() {
        toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setBackground(Theme.BG_CARD);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        // Mode switch — fixed width on the left
        modeSwitch = new SegmentedControl(new String[]{WAV_MODE, LIVE_MODE}, 0);
        modeSwitch.setPreferredSize(new Dimension(120, 34));
        modeSwitch.addChangeListener(e -> onModeSwitch());
        toolbar.add(modeSwitch, BorderLayout.WEST);

        // CardLayout for toolbar buttons
        toolbarCardLayout = new CardLayout();
        toolbarCards = new JPanel(toolbarCardLayout);
        toolbarCards.setOpaque(false);

        // WAV toolbar
        JPanel wavToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        wavToolbar.setOpaque(false);

        renderBtn = Theme.primaryButton("Render");
        renderBtn.setPreferredSize(new Dimension(90, 34));
        renderBtn.setToolTipText("Render the granular spectrum (Ctrl+R)");
        renderBtn.addActionListener(e -> doRender());
        wavToolbar.add(renderBtn);

        playBtn = Theme.secondaryButton("Play");
        playBtn.setPreferredSize(new Dimension(80, 34));
        playBtn.setToolTipText("Play rendered audio (Space)");
        playBtn.addActionListener(e -> doPlay());
        wavToolbar.add(playBtn);

        stopBtn = Theme.ghostButton("Stop");
        stopBtn.setPreferredSize(new Dimension(70, 34));
        stopBtn.setToolTipText("Stop playback or rendering (Escape)");
        stopBtn.addActionListener(e -> doStop());
        wavToolbar.add(stopBtn);

        exportBtn = Theme.secondaryButton("Export WAV");
        exportBtn.setPreferredSize(new Dimension(110, 34));
        exportBtn.setToolTipText("Export rendered audio to WAV file");
        exportBtn.addActionListener(e -> doExport());
        wavToolbar.add(exportBtn);

        helpBtn = Theme.ghostButton("?");
        helpBtn.setPreferredSize(new Dimension(34, 34));
        helpBtn.setToolTipText("Toggle help tooltips");
        helpBtn.addActionListener(e -> {
            HelpManager hm = HelpManager.getInstance();
            hm.setHelpMode(!hm.isHelpMode());
            helpBtn.setForeground(hm.isHelpMode() ? Theme.ACCENT : Theme.FG);
        });
        wavToolbar.add(helpBtn);

        wavToolbar.add(Box.createHorizontalStrut(12));
        progressBar.setPreferredSize(new Dimension(300, 20));
        wavToolbar.add(progressBar);

        toolbarCards.add(wavToolbar, WAV_MODE);

        // Live toolbar
        JPanel liveToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        liveToolbar.setOpaque(false);

        liveStartBtn = Theme.primaryButton("Start");
        liveStartBtn.setPreferredSize(new Dimension(80, 34));
        liveStartBtn.setToolTipText("Start live MIDI playback");
        liveStartBtn.addActionListener(e -> startLiveMode());
        liveToolbar.add(liveStartBtn);

        liveStopBtn = Theme.ghostButton("Stop");
        liveStopBtn.setPreferredSize(new Dimension(70, 34));
        liveStopBtn.setToolTipText("Stop live MIDI playback");
        liveStopBtn.setEnabled(false);
        liveStopBtn.addActionListener(e -> stopLiveMode());
        liveToolbar.add(liveStopBtn);

        JButton liveHelpBtn = Theme.ghostButton("?");
        liveHelpBtn.setPreferredSize(new Dimension(34, 34));
        liveHelpBtn.setToolTipText("Toggle help tooltips");
        liveHelpBtn.addActionListener(e -> {
            HelpManager hm = HelpManager.getInstance();
            hm.setHelpMode(!hm.isHelpMode());
            liveHelpBtn.setForeground(hm.isHelpMode() ? Theme.ACCENT : Theme.FG);
        });
        liveToolbar.add(liveHelpBtn);

        liveToolbar.add(Box.createHorizontalStrut(12));

        midiStatusLabel = Theme.valueLabel("MIDI: —");
        liveToolbar.add(midiStatusLabel);

        liveToolbar.add(Box.createHorizontalStrut(12));

        voiceCountLabel = Theme.valueLabel("");
        liveToolbar.add(voiceCountLabel);

        liveToolbar.add(Box.createHorizontalStrut(12));
        liveProgressBar = Theme.styledProgressBar();
        liveProgressBar.setPreferredSize(new Dimension(200, 20));
        liveProgressBar.setVisible(false);
        liveToolbar.add(liveProgressBar);

        toolbarCards.add(liveToolbar, LIVE_MODE);

        toolbar.add(toolbarCards, BorderLayout.CENTER);

        add(toolbar, BorderLayout.NORTH);
    }

    private void buildMainContent() {
        contentCardLayout = new CardLayout();
        contentCards = new JPanel(contentCardLayout);

        // ── WAV content (existing layout) ──
        parameterPanel = new ParameterPanel(params);
        JScrollPane leftScroll = new JScrollPane(parameterPanel);
        leftScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);
        Theme.styleScrollPane(leftScroll);

        envelopePanel = new EnvelopeEditorPanel(params);
        waveformDisplay = new WaveformDisplay();

        JScrollPane envelopeScroll = new JScrollPane(envelopePanel);
        envelopeScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        envelopeScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        envelopeScroll.getVerticalScrollBar().setUnitIncrement(16);
        Theme.styleScrollPane(envelopeScroll);

        waveformDisplay.setTimbralPreview(envelopePanel.getTimbralPreview());

        // Load source waveform into envelope backgrounds
        loadSourceWaveform();
        parameterPanel.setSourceFileChangeListener(file -> loadSourceWaveform());

        envelopeScroll.setMinimumSize(new Dimension(0, 200));
        waveformDisplay.setMinimumSize(new Dimension(0, 150));

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                envelopeScroll, waveformDisplay);
        rightSplit.setResizeWeight(0.6);
        rightSplit.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane wavSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightSplit);
        wavSplit.setDividerLocation(340);
        wavSplit.setResizeWeight(0.0);
        wavSplit.setBorder(BorderFactory.createEmptyBorder());

        contentCards.add(wavSplit, WAV_MODE);

        // ── Live content ──
        liveParamPanel = new LiveParameterPanel(params);
        JScrollPane liveLeftScroll = new JScrollPane(liveParamPanel);
        liveLeftScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        liveLeftScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        liveLeftScroll.getVerticalScrollBar().setUnitIncrement(16);
        liveLeftScroll.getViewport().setBackground(Theme.BG);
        Theme.styleScrollPane(liveLeftScroll);

        liveMonitorPanel = new LiveMonitorPanel();
        JScrollPane liveRightScroll = new JScrollPane(liveMonitorPanel);
        liveRightScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        liveRightScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        liveRightScroll.getVerticalScrollBar().setUnitIncrement(16);
        liveRightScroll.getViewport().setBackground(Theme.BG);
        Theme.styleScrollPane(liveRightScroll);

        JSplitPane liveSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                liveLeftScroll, liveRightScroll);
        liveSplit.setDividerLocation(340);
        liveSplit.setResizeWeight(0.0);
        liveSplit.setBorder(BorderFactory.createEmptyBorder());

        contentCards.add(liveSplit, LIVE_MODE);

        add(contentCards, BorderLayout.CENTER);
    }

    private void onModeSwitch() {
        boolean isLive = modeSwitch.getSelectedIndex() == 1;
        if (isLive) {
            // Stop WAV playback if running
            if (player.isPlaying()) {
                player.stop();
                playBtn.setText("Play");
            }
            if (renderController.isRendering()) {
                renderController.cancel();
            }
            toolbarCardLayout.show(toolbarCards, LIVE_MODE);
            contentCardLayout.show(contentCards, LIVE_MODE);
        } else {
            // Switching back to WAV: stop live engine if running
            stopLiveMode();
            toolbarCardLayout.show(toolbarCards, WAV_MODE);
            contentCardLayout.show(contentCards, WAV_MODE);
        }
        toolbarCards.revalidate();
        toolbarCards.repaint();
        contentCards.revalidate();
        contentCards.repaint();
    }

    private void startLiveMode() {
        // Validate samples
        if (params.sourceFile == null || !params.sourceFile.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Source sample file not found. Set it in the Samples section.",
                    "Missing Sample", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (params.grainFile == null || !params.grainFile.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Grain sample file not found. Set it in the Samples section.",
                    "Missing Sample", JOptionPane.WARNING_MESSAGE);
            return;
        }

        liveStartBtn.setEnabled(false);
        midiStatusLabel.setText("Preparing layers...");
        liveProgressBar.setValue(0);
        liveProgressBar.setVisible(true);

        new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() {
                liveController.start(params, pct -> publish(pct));
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (!chunks.isEmpty()) {
                    liveProgressBar.setValue(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                liveProgressBar.setVisible(false);
                try {
                    get(); // check for exceptions
                    liveStopBtn.setEnabled(true);
                    midiStatusLabel.setText("MIDI: " + liveController.getMidiDeviceName());

                    // Wire up panels
                    liveParamPanel.setLiveController(liveController);
                    liveMonitorPanel.setLiveController(liveController);

                    // Start polling
                    liveParamPanel.startSync();
                    liveMonitorPanel.startPolling();

                    // Start toolbar status timer
                    if (liveStatusTimer != null) liveStatusTimer.stop();
                    liveStatusTimer = new Timer(100, ev -> {
                        if (liveController.isRunning()) {
                            voiceCountLabel.setText("Voices: " + liveController.getActiveVoiceCount()
                                + "/" + liveController.getMaxVoices());
                        }
                    });
                    liveStatusTimer.start();
                } catch (Exception ex) {
                    liveStartBtn.setEnabled(true);
                    midiStatusLabel.setText("Error");
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "Failed to start live mode: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void stopLiveMode() {
        if (liveStatusTimer != null) {
            liveStatusTimer.stop();
            liveStatusTimer = null;
        }

        // Cancel in-flight rendering or stop running engine
        liveController.cancel();
        liveController.stop();

        liveProgressBar.setVisible(false);
        liveParamPanel.stopSync();
        liveMonitorPanel.stopPolling();

        liveStartBtn.setEnabled(true);
        liveStopBtn.setEnabled(false);
        midiStatusLabel.setText("MIDI: —");
        voiceCountLabel.setText("");
        liveMonitorPanel.reset();
    }

    private void setupKeyboardShortcuts() {
        JPanel root = (JPanel) getContentPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), "render");
        root.getActionMap().put("render", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doRender();
            }
        });

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "playPause");
        root.getActionMap().put("playPause", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doPlay();
            }
        });

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "stop");
        root.getActionMap().put("stop", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Escape stops live mode if active, otherwise stops WAV playback/render
                if (liveController.isRunning()) {
                    stopLiveMode();
                } else {
                    doStop();
                }
            }
        });

        // Arrow keys: mix (left/right) and density (up/down) in live mode.
        // Uses KeyEventDispatcher to intercept BEFORE focused sliders consume them.
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ke -> {
            if (ke.getID() != KeyEvent.KEY_PRESSED) return false;
            if (!liveController.isRunning()) return false;
            switch (ke.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                    liveController.getControlState().setMix(
                            liveController.getControlState().getMix() - 0.05);
                    return true;
                case KeyEvent.VK_RIGHT:
                    liveController.getControlState().setMix(
                            liveController.getControlState().getMix() + 0.05);
                    return true;
                case KeyEvent.VK_UP:
                    liveController.getControlState().setDensity(
                            liveController.getControlState().getDensity() + 0.05);
                    return true;
                case KeyEvent.VK_DOWN:
                    liveController.getControlState().setDensity(
                            liveController.getControlState().getDensity() - 0.05);
                    return true;
                default:
                    return false;
            }
        });
    }

    private void doRender() {
        if (renderController.isRendering()) return;
        if (modeSwitch.getSelectedIndex() == 1) return; // don't render in live mode
        if (player.isPlaying()) {
            player.stop();
        }
        playBtn.setText("Play");
        envelopePanel.syncToParams();
        renderBtn.setEnabled(false);
        playBtn.setEnabled(false);
        exportBtn.setEnabled(false);
        progressBar.setString("Rendering...");
        renderController.render(params);
    }

    private void doPlay() {
        if (renderedBuffer == null) return;
        if (modeSwitch.getSelectedIndex() == 1) return; // don't play WAV in live mode
        if (player.isPlaying()) {
            player.stop();
            playBtn.setText("Play");
        } else {
            player.play(renderedBuffer, WaveWriter.SAMPLE_RATE, () -> playBtn.setText("Play"));
            playBtn.setText("Pause");
        }
    }

    private void doStop() {
        if (player.isPlaying()) {
            player.stop();
            playBtn.setText("Play");
        }
        if (renderController.isRendering()) {
            renderController.cancel();
        }
    }

    private void doExport() {
        if (renderedBuffer == null) {
            JOptionPane.showMessageDialog(this, "Nothing to export. Render first.");
            return;
        }
        JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
        chooser.setFileFilter(new FileNameExtensionFilter("WAV files", "wav"));
        chooser.setSelectedFile(new File("granular_spectrum_output.wav"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String path = file.getPath();
            if (!path.toLowerCase().endsWith(".wav")) {
                path += ".wav";
            }
            WaveWriter ww = new WaveWriter("_export", renderedBuffer[0].length);
            ww.df = renderedBuffer;
            ww.renderToFile(path);
            JOptionPane.showMessageDialog(this, "Exported to " + path);
        }
    }

    private void doSavePreset() {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
        chooser.setFileFilter(new FileNameExtensionFilter("Properties files", "properties"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String path = file.getPath();
            if (!path.toLowerCase().endsWith(".properties")) {
                path += ".properties";
            }
            PresetManager.save(params, path);
        }
    }

    private void doLoadPreset() {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
        chooser.setFileFilter(new FileNameExtensionFilter("Properties files", "properties"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            PresetManager.load(params, chooser.getSelectedFile().getPath());
            parameterPanel.syncFromParams();
            envelopePanel.syncFromParams();
        }
    }

    private void loadSourceWaveform() {
        File src = params.sourceFile;
        if (src == null) return;
        new SwingWorker<float[], Void>() {
            @Override
            protected float[] doInBackground() {
                try {
                    return ReadSound.readSound(src.getPath());
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
            @Override
            protected void done() {
                try {
                    float[] samples = get();
                    if (samples != null && envelopePanel != null) {
                        envelopePanel.setWaveformData(samples);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    public static void main(String[] args) {
        // Install theme BEFORE any Swing components are created
        Theme.applyTheme(ThemePreferences.load());

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
