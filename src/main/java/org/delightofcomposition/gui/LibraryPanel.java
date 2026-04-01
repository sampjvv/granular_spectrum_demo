package org.delightofcomposition.gui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.sound.AudioPlayer;

/**
 * Library sidebar showing saved renders and live presets.
 */
public class LibraryPanel extends JPanel {

    private static final int WIDTH = 290;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("MMM d, yyyy  h:mm a");

    private final AudioPlayer libraryPlayer = new AudioPlayer();
    private final LibraryCallback callback;
    private final JPanel listPanel;
    private final SegmentedControl tabSwitch;
    private JButton currentPlayBtn;

    public interface LibraryCallback {
        void onLoadPreset(File propertiesFile);
        void onUpdateEntry(SoundLibrary.LibraryEntry entry, boolean isRender);
    }

    public LibraryPanel(LibraryCallback callback) {
        this.callback = callback;
        setLayout(new BorderLayout(0, 8));
        setPreferredSize(new Dimension(WIDTH, 0));
        setMinimumSize(new Dimension(WIDTH, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(Theme.sectionLabel("Library"), BorderLayout.WEST);

        JButton openFolderBtn = Theme.ghostButton("Open Folder");
        Theme.tagFont(openFolderBtn, "small");
        openFolderBtn.setPreferredSize(new Dimension(95, 26));
        openFolderBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(SoundLibrary.getLibraryRoot());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        header.add(openFolderBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Entry list
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        Theme.styleScrollPane(scroll);
        add(scroll, BorderLayout.CENTER);

        // Tab switch
        tabSwitch = new SegmentedControl(new String[]{"Renders", "Live Presets"}, 0);
        tabSwitch.addChangeListener(e -> refresh());
        tabSwitch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        add(tabSwitch, BorderLayout.SOUTH);

        refresh();
    }

    public void refresh() {
        listPanel.removeAll();
        boolean showRenders = tabSwitch.getSelectedIndex() == 0;

        List<SoundLibrary.LibraryEntry> entries = showRenders
                ? SoundLibrary.listRenders()
                : SoundLibrary.listLivePresets();

        if (entries.isEmpty()) {
            JLabel empty = new JLabel(showRenders ? "No saved renders" : "No saved presets");
            Theme.tagFont(empty, "small");
            Theme.tagFg(empty, "fgDim");
            empty.setAlignmentX(0.5f);
            empty.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
            listPanel.add(empty);
        } else {
            for (SoundLibrary.LibraryEntry entry : entries) {
                listPanel.add(buildEntryCard(entry, showRenders));
                listPanel.add(Box.createVerticalStrut(6));
            }
        }

        listPanel.add(Box.createVerticalGlue());
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel buildEntryCard(SoundLibrary.LibraryEntry entry, boolean isRender) {
        JPanel card = new JPanel(new BorderLayout(0, 4)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (Theme.isSynthwave()) {
                    SynthwavePainter.fillPanel(g2, 0, 0, getWidth(), getHeight(),
                            Theme.BG_CARD, Theme.BORDER_SUBTLE);
                } else {
                    g2.setColor(Theme.BG_CARD);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                            Theme.RADIUS, Theme.RADIUS);
                    g2.setColor(Theme.BORDER_SUBTLE);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                            Theme.RADIUS, Theme.RADIUS);
                }
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        // Name + date
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        JLabel nameLabel = new JLabel(entry.displayName);
        Theme.tagFont(nameLabel, "small");
        Theme.tagFg(nameLabel, "fg");
        nameLabel.setAlignmentX(0);
        nameLabel.setToolTipText(entry.displayName);
        // Constrain width so text truncates instead of overflowing
        nameLabel.setMaximumSize(new Dimension(WIDTH - 40, Short.MAX_VALUE));
        info.add(nameLabel);

        JLabel dateLabel = new JLabel(DATE_FMT.format(new Date(entry.dateMillis)));
        Theme.tagFont(dateLabel, "small");
        Theme.tagFg(dateLabel, "fgDim");
        dateLabel.setAlignmentX(0);
        info.add(dateLabel);

        card.add(info, BorderLayout.CENTER);

        // Buttons — split into two rows to fit within sidebar width
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.setOpaque(false);

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        topRow.setOpaque(false);

        if (isRender && entry.wavFile != null) {
            JButton playBtn = Theme.ghostButton("Play");
            Theme.tagFont(playBtn, "small");
            playBtn.setPreferredSize(new Dimension(50, 24));
            playBtn.addActionListener(e -> {
                if (libraryPlayer.isPlaying() && currentPlayBtn == playBtn) {
                    libraryPlayer.stop();
                    playBtn.setText("Play");
                    currentPlayBtn = null;
                } else {
                    libraryPlayer.stop();
                    if (currentPlayBtn != null) currentPlayBtn.setText("Play");
                    float[][] buf = SoundLibrary.readStereoWav(entry.wavFile);
                    if (buf != null) {
                        currentPlayBtn = playBtn;
                        playBtn.setText("Stop");
                        libraryPlayer.play(buf, 48000, () -> {
                            playBtn.setText("Play");
                            currentPlayBtn = null;
                        });
                    }
                }
            });
            topRow.add(playBtn);
        }

        if (entry.propertiesFile != null && entry.propertiesFile.exists()) {
            JButton loadBtn = Theme.ghostButton("Load");
            Theme.tagFont(loadBtn, "small");
            loadBtn.setPreferredSize(new Dimension(50, 24));
            loadBtn.addActionListener(e -> callback.onLoadPreset(entry.propertiesFile));
            topRow.add(loadBtn);
        }

        JButton updateBtn = Theme.ghostButton("Update");
        Theme.tagFont(updateBtn, "small");
        updateBtn.setPreferredSize(new Dimension(60, 24));
        updateBtn.setToolTipText("Overwrite with current settings" + (isRender ? " and render" : ""));
        updateBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Overwrite \"" + entry.displayName + "\" with current settings?",
                    "Update", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                callback.onUpdateEntry(entry, isRender);
                refresh();
            }
        });
        topRow.add(updateBtn);

        buttons.add(topRow);

        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bottomRow.setOpaque(false);

        JButton renameBtn = Theme.ghostButton("Rename");
        Theme.tagFont(renameBtn, "small");
        renameBtn.setPreferredSize(new Dimension(65, 24));
        renameBtn.addActionListener(e -> {
            String newName = JOptionPane.showInputDialog(this, "New name:", entry.displayName);
            if (newName != null && !newName.trim().isEmpty()) {
                SoundLibrary.renameEntry(entry, newName.trim());
                refresh();
            }
        });
        bottomRow.add(renameBtn);

        JButton delBtn = Theme.ghostButton("Del");
        Theme.tagFont(delBtn, "small");
        Theme.tagFg(delBtn, "destructive");
        delBtn.setPreferredSize(new Dimension(55, 24));
        delBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete \"" + entry.displayName + "\"?",
                    "Delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                if (libraryPlayer.isPlaying() && currentPlayBtn != null) {
                    libraryPlayer.stop();
                    currentPlayBtn = null;
                }
                SoundLibrary.deleteEntry(entry);
                refresh();
            }
        });
        bottomRow.add(delBtn);

        buttons.add(bottomRow);

        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    public void stopPlayback() {
        libraryPlayer.stop();
        if (currentPlayBtn != null) {
            currentPlayBtn.setText("Play");
            currentPlayBtn = null;
        }
    }
}
