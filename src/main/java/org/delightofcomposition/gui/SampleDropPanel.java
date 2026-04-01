package org.delightofcomposition.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;


/**
 * Drag-and-drop panel for loading WAV samples, styled with dark theme.
 */
public class SampleDropPanel extends JPanel {

    private final JLabel titleLabel;
    private final JLabel fileLabel;
    private final Consumer<File> onFileSelected;
    private File currentFile;
    private boolean hovering = false;

    public SampleDropPanel(String title, File initialFile, Consumer<File> onFileSelected) {
        this.onFileSelected = onFileSelected;
        this.currentFile = initialFile;
        setOpaque(false);
        setLayout(new BorderLayout(6, 0));
        setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        JPanel inner = new JPanel(new BorderLayout(6, 2)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Background
                Color bg = hovering ? Theme.ACCENT_MUTED : Theme.BG_INPUT;
                Color border = hovering ? Theme.ACCENT : Theme.BORDER;
                if (Theme.isSynthwave()) {
                    SynthwavePainter.fillPanel(g2, 0, 0, getWidth(), getHeight(), bg, border);
                } else {
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                    g2.setColor(border);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                }

                g2.dispose();
            }
        };
        inner.setOpaque(false);
        inner.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JPanel labels = new JPanel(new BorderLayout());
        labels.setOpaque(false);

        titleLabel = new JLabel(title);
        Theme.tagFont(titleLabel, "small");
        Theme.tagFg(titleLabel, "fgDim");
        labels.add(titleLabel, BorderLayout.NORTH);

        fileLabel = new JLabel(initialFile != null ? initialFile.getName() : "Drop WAV here");
        Theme.tagFont(fileLabel, "base");
        Theme.tagFg(fileLabel, "fg");
        fileLabel.setHorizontalAlignment(SwingConstants.LEFT);
        labels.add(fileLabel, BorderLayout.CENTER);

        inner.add(labels, BorderLayout.CENTER);

        JButton browseBtn = Theme.ghostButton("...");
        browseBtn.setPreferredSize(new Dimension(36, 28));
        browseBtn.setToolTipText("Browse for WAV file");
        browseBtn.addActionListener(e -> browse());

        inner.add(browseBtn, BorderLayout.EAST);

        add(inner, BorderLayout.CENTER);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        setPreferredSize(new Dimension(280, 52));

        // Drag-and-drop
        new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    hovering = true;
                    repaint();
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                hovering = false;
                repaint();
            }

            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                hovering = false;
                repaint();
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        File file = files.get(0);
                        if (validateWav(file)) {
                            setFile(file);
                            dtde.dropComplete(true);
                        } else {
                            dtde.dropComplete(false);
                        }
                    }
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                }
            }
        }, true);
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser(
                currentFile != null ? currentFile.getParentFile() : new File(System.getProperty("user.dir")));
        chooser.setFileFilter(new FileNameExtensionFilter("WAV audio files", "wav"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (validateWav(file)) setFile(file);
        }
    }

    private boolean validateWav(File file) {
        if (file == null || !file.exists()) {
            JOptionPane.showMessageDialog(this, "File does not exist.", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (!file.getName().toLowerCase().endsWith(".wav")) {
            JOptionPane.showMessageDialog(this,
                    "Only .wav files are supported.\nReadSound supports mono 16-bit or 24-bit PCM WAV.",
                    "Unsupported Format", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    public void setFile(File file) {
        this.currentFile = file;
        fileLabel.setText(file.getName());
        fileLabel.setToolTipText(file.getAbsolutePath());
        onFileSelected.accept(file);
    }

    public File getFile() {
        return currentFile;
    }
}
