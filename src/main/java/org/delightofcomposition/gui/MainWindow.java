package org.delightofcomposition.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.delightofcomposition.RenderController;
import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.sound.AudioPlayer;
import org.delightofcomposition.sound.WaveWriter;

/**
 * Top-level JFrame for the Granular Spectrum Synthesizer GUI.
 */
public class MainWindow extends JFrame {

    private final SynthParameters params = new SynthParameters();
    private final JProgressBar progressBar;
    private final AudioPlayer player = new AudioPlayer();
    private final RenderController renderController;

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

    public MainWindow() {
        super("Granular Spectrum Synthesizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);

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

        buildMenuBar();
        buildToolbar();
        buildMainContent();
        setupKeyboardShortcuts();

        playBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        exportBtn.setEnabled(false);
        progressBar.setString("Ready");
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
        exitItem.addActionListener(e -> System.exit(0));
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
        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBackground(Theme.BG_CARD);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));

        renderBtn = Theme.primaryButton("Render");
        renderBtn.setPreferredSize(new Dimension(90, 34));
        renderBtn.setToolTipText("Render the granular spectrum (Ctrl+R)");
        renderBtn.addActionListener(e -> doRender());
        toolbar.add(renderBtn);

        playBtn = Theme.secondaryButton("Play");
        playBtn.setPreferredSize(new Dimension(80, 34));
        playBtn.setToolTipText("Play rendered audio (Space)");
        playBtn.addActionListener(e -> doPlay());
        toolbar.add(playBtn);

        stopBtn = Theme.ghostButton("Stop");
        stopBtn.setPreferredSize(new Dimension(70, 34));
        stopBtn.setToolTipText("Stop playback or rendering (Escape)");
        stopBtn.addActionListener(e -> doStop());
        toolbar.add(stopBtn);

        exportBtn = Theme.secondaryButton("Export WAV");
        exportBtn.setPreferredSize(new Dimension(110, 34));
        exportBtn.setToolTipText("Export rendered audio to WAV file");
        exportBtn.addActionListener(e -> doExport());
        toolbar.add(exportBtn);

        helpBtn = Theme.ghostButton("?");
        helpBtn.setPreferredSize(new Dimension(34, 34));
        helpBtn.setToolTipText("Toggle help tooltips");
        helpBtn.addActionListener(e -> {
            HelpManager hm = HelpManager.getInstance();
            hm.setHelpMode(!hm.isHelpMode());
            helpBtn.setForeground(hm.isHelpMode() ? Theme.ACCENT : Theme.FG);
        });
        toolbar.add(helpBtn);

        toolbar.add(Box.createHorizontalStrut(12));

        progressBar.setPreferredSize(new Dimension(300, 20));
        toolbar.add(progressBar);

        add(toolbar, BorderLayout.NORTH);
    }

    private void buildMainContent() {
        // Left panel: parameter controls (scrollable)
        parameterPanel = new ParameterPanel(params);
        JScrollPane leftScroll = new JScrollPane(parameterPanel);
        leftScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);
        Theme.styleScrollPane(leftScroll);

        // Right panel: envelope editors + waveform display in vertical split
        envelopePanel = new EnvelopeEditorPanel(params);
        waveformDisplay = new WaveformDisplay();

        JScrollPane envelopeScroll = new JScrollPane(envelopePanel);
        envelopeScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        envelopeScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        envelopeScroll.getVerticalScrollBar().setUnitIncrement(16);
        Theme.styleScrollPane(envelopeScroll);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                envelopeScroll, waveformDisplay);
        rightSplit.setResizeWeight(0.7);
        rightSplit.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightSplit);
        splitPane.setDividerLocation(340);
        splitPane.setResizeWeight(0.0);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        add(splitPane, BorderLayout.CENTER);
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
                doStop();
            }
        });
    }

    private void doRender() {
        if (renderController.isRendering()) return;
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

    public static void main(String[] args) {
        // Install theme BEFORE any Swing components are created
        Theme.applyTheme(ThemePreferences.load());

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
