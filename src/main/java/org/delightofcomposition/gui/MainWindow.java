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
    private final JLabel progressLabel;
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
    private JScrollPane envelopeScroll;
    private JButton helpBtn;
    private JButton saveToLibBtn;
    private JButton liveSavePresetBtn;
    private LibraryPanel libraryPanel;
    private SourceRegionSelector regionSelector;
    private float[] fullSourceWaveform; // stored for re-slicing on region change
    private JPanel toolbar;
    private JPanel mainWithLibrary;
    private JSplitPane wavSplit;
    private JSplitPane rightSplit;
    private JSplitPane liveSplit;

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

        // Solid themed background behind ALL content — avoids Windows L&F white bleed
        JPanel bg = new JPanel(new java.awt.BorderLayout()) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                g.setColor(Theme.BG);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        bg.setBackground(Theme.BG);
        setContentPane(bg);

        liveController = new LiveMidiController();

        progressBar = Theme.styledProgressBar();
        progressLabel = Theme.valueLabel("");
        Theme.tagFont(progressLabel, "small");

        // Show percentage next to bar whenever value changes
        progressBar.addChangeListener(e -> {
            int val = progressBar.getValue();
            if (val > 0 && val < 100) {
                progressLabel.setText(val + "%");
            } else {
                progressLabel.setText("");
            }
        });

        renderController = new RenderController(progressBar, new RenderController.RenderCallback() {
            @Override
            public void onComplete(float[][] stereoBuffer) {
                renderedBuffer = stereoBuffer;
                renderBtn.setEnabled(true);
                playBtn.setEnabled(true);
                exportBtn.setEnabled(true);
                saveToLibBtn.setEnabled(true);
                progressBar.setValue(100);
                if (waveformDisplay != null) {
                    waveformDisplay.setBuffer(stereoBuffer);
                }
            }

            @Override
            public void onError(String message) {
                renderBtn.setEnabled(true);
                JOptionPane.showMessageDialog(MainWindow.this,
                        "Render error: " + message, "Error", JOptionPane.ERROR_MESSAGE);
            }

            @Override
            public void onCancelled() {
                renderBtn.setEnabled(true);
                progressBar.setValue(0);
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
        saveToLibBtn.setEnabled(false);
        progressLabel.setText("");

        // Window close: stop live engine if running
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopLiveMode();
                dispose();
                System.exit(0);
            }
        });

        // Set up CRT scanline overlay if synthwave theme is active at startup
        if (Theme.isSynthwave()) {
            setGlassPane(new SynthwaveScanlinePane());
            getGlassPane().setVisible(true);
        }
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
                Theme.resetExplicitProperties(getContentPane());
                if (getJMenuBar() != null) {
                    Theme.resetExplicitProperties(getJMenuBar());
                    // Also reset popup menus (not part of the JMenuBar component tree)
                    for (int mi = 0; mi < getJMenuBar().getMenuCount(); mi++) {
                        javax.swing.JMenu menu = getJMenuBar().getMenu(mi);
                        if (menu != null) {
                            Theme.resetExplicitProperties(menu.getPopupMenu());
                        }
                    }
                }
                // Rebuild panels that swap PmXxx components for Paper theme
                parameterPanel.rebuild();
                envelopePanel.rebuild();
                waveformDisplay.setTimbralPreview(envelopePanel.getTimbralPreview());
                SwingUtilities.updateComponentTreeUI(this);
                Theme.refreshTaggedProperties(getContentPane());
                refreshMenuBar();
                refreshBackgrounds();
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

    /** Force-refresh the menu bar after a theme switch. */
    private void refreshMenuBar() {
        javax.swing.JMenuBar mb = getJMenuBar();
        if (mb == null) return;
        mb.setBackground(Theme.BG_CARD);
        mb.setForeground(Theme.FG);
        mb.setFont(Theme.FONT_BASE);
        mb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        for (int i = 0; i < mb.getMenuCount(); i++) {
            javax.swing.JMenu menu = mb.getMenu(i);
            if (menu == null) continue;
            menu.setBackground(Theme.BG_CARD);
            menu.setForeground(Theme.FG);
            menu.setFont(Theme.FONT_BASE);
            // Also refresh popup menu items
            javax.swing.JPopupMenu popup = menu.getPopupMenu();
            popup.setBackground(Theme.BG_CARD);
            popup.setForeground(Theme.FG);
            for (java.awt.Component mc : popup.getComponents()) {
                if (mc instanceof javax.swing.JComponent) {
                    ((javax.swing.JComponent) mc).setBackground(Theme.BG_CARD);
                    ((javax.swing.JComponent) mc).setForeground(Theme.FG);
                    ((javax.swing.JComponent) mc).setFont(Theme.FONT_BASE);
                }
                // Recurse into submenus (like the Theme submenu)
                if (mc instanceof javax.swing.JMenu) {
                    javax.swing.JMenu sub = (javax.swing.JMenu) mc;
                    javax.swing.JPopupMenu subPop = sub.getPopupMenu();
                    subPop.setBackground(Theme.BG_CARD);
                    subPop.setForeground(Theme.FG);
                    for (java.awt.Component sc : subPop.getComponents()) {
                        if (sc instanceof javax.swing.JComponent) {
                            ((javax.swing.JComponent) sc).setBackground(Theme.BG_CARD);
                            ((javax.swing.JComponent) sc).setForeground(Theme.FG);
                            ((javax.swing.JComponent) sc).setFont(Theme.FONT_BASE);
                        }
                    }
                }
            }
        }
    }

    /** Re-apply themed backgrounds after a theme switch. */
    private void refreshBackgrounds() {
        getContentPane().setBackground(Theme.BG);
        toolbar.setBackground(Theme.BG_CARD);
        // Re-clear split pane internals that Windows L&F may recreate on updateUI
        clearSplitPaneOpacity(wavSplit);
        clearSplitPaneOpacity(rightSplit);
        clearSplitPaneOpacity(liveSplit);
        // Keep envelope viewport opaque so toggle repaints don't bleed
        if (envelopeScroll != null) {
            envelopeScroll.getViewport().setOpaque(true);
            envelopeScroll.getViewport().setBackground(Theme.BG);
        }

        // Toggle CRT scanline overlay
        if (Theme.isSynthwave()) {
            if (!(getGlassPane() instanceof SynthwaveScanlinePane)) {
                setGlassPane(new SynthwaveScanlinePane());
            }
            getGlassPane().setVisible(true);
        } else {
            if (getGlassPane() instanceof SynthwaveScanlinePane) {
                getGlassPane().setVisible(false);
            }
        }

        // Revalidate for font metric changes
        getContentPane().revalidate();
    }

    /**
     * Make a JSplitPane and ALL its internal components (divider, wrapper panels)
     * non-opaque so the root background shows through. Windows L&F creates opaque
     * internal containers that ignore UIManager SplitPane.background.
     */
    private static void clearSplitPaneOpacity(JSplitPane sp) {
        if (sp == null) return;
        sp.setOpaque(false);
        sp.setBorder(BorderFactory.createEmptyBorder());
        for (java.awt.Component c : sp.getComponents()) {
            if (c instanceof JComponent) {
                ((JComponent) c).setOpaque(false);
            }
        }
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

        saveToLibBtn = Theme.secondaryButton("Save to Library");
        saveToLibBtn.setPreferredSize(new Dimension(130, 34));
        saveToLibBtn.setToolTipText("Save render + settings to library");
        saveToLibBtn.addActionListener(e -> doSaveToLibrary());
        wavToolbar.add(saveToLibBtn);

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
        progressBar.setPreferredSize(new Dimension(150, 20));
        progressBar.setMinimumSize(new Dimension(80, 20));
        wavToolbar.add(progressBar);
        wavToolbar.add(Box.createHorizontalStrut(6));
        wavToolbar.add(progressLabel);

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

        liveSavePresetBtn = Theme.secondaryButton("Save to Library");
        liveSavePresetBtn.setPreferredSize(new Dimension(130, 34));
        liveSavePresetBtn.setToolTipText("Save current settings as a live preset");
        liveSavePresetBtn.addActionListener(e -> doSaveLivePreset());
        liveToolbar.add(liveSavePresetBtn);

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

        JButton libraryToggle = Theme.ghostButton("Library");
        libraryToggle.setPreferredSize(new Dimension(80, 34));
        libraryToggle.setToolTipText("Toggle library sidebar (Ctrl+L)");
        libraryToggle.addActionListener(e -> toggleLibrary());
        toolbar.add(libraryToggle, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);
    }

    private void buildMainContent() {
        contentCardLayout = new CardLayout();
        contentCards = new JPanel(contentCardLayout);
        contentCards.setOpaque(false);

        // ── WAV content (existing layout) ──
        parameterPanel = new ParameterPanel(params);
        JScrollPane leftScroll = new JScrollPane(parameterPanel);
        leftScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);
        Theme.styleScrollPane(leftScroll);

        envelopePanel = new EnvelopeEditorPanel(params);
        waveformDisplay = new WaveformDisplay();

        envelopeScroll = new JScrollPane(envelopePanel);
        envelopeScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        envelopeScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        envelopeScroll.getVerticalScrollBar().setUnitIncrement(16);
        Theme.styleScrollPane(envelopeScroll);
        envelopeScroll.getViewport().setOpaque(true);
        envelopeScroll.getViewport().setBackground(Theme.BG);
        envelopeScroll.getViewport().setScrollMode(javax.swing.JViewport.SIMPLE_SCROLL_MODE);

        waveformDisplay.setTimbralPreview(envelopePanel.getTimbralPreview());

        // Region selector above envelopes
        regionSelector = new SourceRegionSelector(params);
        regionSelector.addChangeListener(e -> onRegionChanged());

        // Load source waveform into envelope backgrounds + region selector
        loadSourceWaveform();
        parameterPanel.setSourceFileChangeListener(file -> {
            params.sourceStartFraction = 0.0;
            params.sourceEndFraction = 1.0;
            loadSourceWaveform();
        });

        JPanel envelopeWrapper = new JPanel(new BorderLayout(0, 4));
        envelopeWrapper.setOpaque(false);
        envelopeWrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        envelopeWrapper.add(regionSelector, BorderLayout.NORTH);
        envelopeWrapper.add(envelopeScroll, BorderLayout.CENTER);

        envelopeScroll.setMinimumSize(new Dimension(0, 280));
        waveformDisplay.setPreferredSize(new Dimension(0, 280));
        waveformDisplay.setMinimumSize(new Dimension(0, 280));
        waveformDisplay.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));

        rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                envelopeWrapper, waveformDisplay);
        rightSplit.setResizeWeight(1.0);
        clearSplitPaneOpacity(rightSplit);
        // Defer divider location — pixel values are ignored before layout
        SwingUtilities.invokeLater(() -> rightSplit.setDividerLocation(0.7));

        wavSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightSplit);
        wavSplit.setDividerLocation(340);
        wavSplit.setResizeWeight(0.0);
        clearSplitPaneOpacity(wavSplit);

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

        liveSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                liveLeftScroll, liveRightScroll);
        liveSplit.setDividerLocation(340);
        liveSplit.setResizeWeight(0.0);
        clearSplitPaneOpacity(liveSplit);

        contentCards.add(liveSplit, LIVE_MODE);

        // Library sidebar
        libraryPanel = new LibraryPanel(new LibraryPanel.LibraryCallback() {
            @Override
            public void onLoadPreset(java.io.File propsFile) {
                PresetManager.load(params, propsFile.getPath());
                parameterPanel.syncFromParams();
                envelopePanel.syncFromParams();
                loadSourceWaveform();
            }

            @Override
            public void onUpdateEntry(SoundLibrary.LibraryEntry entry, boolean isRender) {
                envelopePanel.syncToParams();
                if (isRender && renderedBuffer != null) {
                    SoundLibrary.updateRender(entry, renderedBuffer, params);
                } else {
                    SoundLibrary.updateLivePreset(entry, params);
                }
            }
        });
        libraryPanel.setVisible(false);

        mainWithLibrary = new JPanel(new BorderLayout());
        mainWithLibrary.setOpaque(false);
        mainWithLibrary.add(contentCards, BorderLayout.CENTER);
        mainWithLibrary.add(libraryPanel, BorderLayout.EAST);

        add(mainWithLibrary, BorderLayout.CENTER);
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
        liveStopBtn.setEnabled(true);
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
                    liveStatusTimer = new Timer(250, ev -> {
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

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), "toggleLibrary");
        root.getActionMap().put("toggleLibrary", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleLibrary();
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
        saveToLibBtn.setEnabled(false);
        progressLabel.setText("");
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

    private void doSaveToLibrary() {
        if (renderedBuffer == null) return;
        envelopePanel.syncToParams();
        SoundLibrary.saveRender(renderedBuffer, params);
        if (libraryPanel != null) {
            libraryPanel.refresh();
            if (!libraryPanel.isVisible()) toggleLibrary();
        }
    }

    private void doSaveLivePreset() {
        envelopePanel.syncToParams();
        SoundLibrary.saveLivePreset(params);
        if (libraryPanel != null) {
            libraryPanel.refresh();
            if (!libraryPanel.isVisible()) toggleLibrary();
        }
    }

    private void toggleLibrary() {
        if (libraryPanel != null) {
            boolean show = !libraryPanel.isVisible();
            if (show) libraryPanel.refresh();
            libraryPanel.setVisible(show);
            libraryPanel.getParent().revalidate();
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
                    if (samples != null) {
                        fullSourceWaveform = samples;
                        if (regionSelector != null) {
                            regionSelector.setWaveformData(samples);
                        }
                        updateEnvelopeWaveform();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    private void onRegionChanged() {
        updateEnvelopeWaveform();
        if (regionSelector != null) regionSelector.repaint();
    }

    private void updateEnvelopeWaveform() {
        if (fullSourceWaveform == null || envelopePanel == null) return;
        int start = (int) (params.sourceStartFraction * fullSourceWaveform.length);
        int end = (int) (params.sourceEndFraction * fullSourceWaveform.length);
        start = Math.max(0, start);
        end = Math.min(fullSourceWaveform.length, end);
        if (end <= start) return;
        float[] region = java.util.Arrays.copyOfRange(fullSourceWaveform, start, end);
        envelopePanel.setWaveformData(region);
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
