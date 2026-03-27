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

    private static final int WIDTH = 260;
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
        openFolderBtn.setFont(Theme.FONT_SMALL);
        openFolderBtn.setPreferredSize(new Dimension(85, 24));
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
        tabSwitch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
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
            empty.setFont(Theme.FONT_SMALL);
            empty.setForeground(Theme.FG_DIM);
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
                g2.setColor(Theme.BG_CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS, Theme.RADIUS);
                g2.setColor(Theme.BORDER_SUBTLE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS, Theme.RADIUS);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        // Name + date
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        JLabel nameLabel = new JLabel(entry.displayName);
        nameLabel.setFont(Theme.FONT_BASE);
        nameLabel.setForeground(Theme.FG);
        nameLabel.setAlignmentX(0);
        info.add(nameLabel);

        JLabel dateLabel = new JLabel(DATE_FMT.format(new Date(entry.dateMillis)));
        dateLabel.setFont(Theme.FONT_SMALL);
        dateLabel.setForeground(Theme.FG_DIM);
        dateLabel.setAlignmentX(0);
        info.add(dateLabel);

        card.add(info, BorderLayout.CENTER);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.setOpaque(false);

        if (isRender && entry.wavFile != null) {
            JButton playBtn = Theme.ghostButton("Play");
            playBtn.setFont(Theme.FONT_SMALL);
            playBtn.setPreferredSize(new Dimension(50, 22));
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
            buttons.add(playBtn);
        }

        if (entry.propertiesFile != null && entry.propertiesFile.exists()) {
            JButton loadBtn = Theme.ghostButton("Load");
            loadBtn.setFont(Theme.FONT_SMALL);
            loadBtn.setPreferredSize(new Dimension(50, 22));
            loadBtn.addActionListener(e -> callback.onLoadPreset(entry.propertiesFile));
            buttons.add(loadBtn);
        }

        JButton updateBtn = Theme.ghostButton("Update");
        updateBtn.setFont(Theme.FONT_SMALL);
        updateBtn.setPreferredSize(new Dimension(55, 22));
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
        buttons.add(updateBtn);

        JButton renameBtn = Theme.ghostButton("Rename");
        renameBtn.setFont(Theme.FONT_SMALL);
        renameBtn.setPreferredSize(new Dimension(60, 22));
        renameBtn.addActionListener(e -> {
            String newName = JOptionPane.showInputDialog(this, "New name:", entry.displayName);
            if (newName != null && !newName.trim().isEmpty()) {
                SoundLibrary.renameEntry(entry, newName.trim());
                refresh();
            }
        });
        buttons.add(renameBtn);

        JButton delBtn = Theme.ghostButton("Del");
        delBtn.setFont(Theme.FONT_SMALL);
        delBtn.setForeground(Theme.DESTRUCTIVE);
        delBtn.setPreferredSize(new Dimension(40, 22));
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
        buttons.add(delBtn);

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
