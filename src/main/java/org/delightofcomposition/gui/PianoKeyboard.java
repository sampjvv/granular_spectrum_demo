package org.delightofcomposition.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

import org.delightofcomposition.realtime.Voice;

/**
 * Custom-painted piano keyboard spanning C3 (MIDI 48) to C5 (MIDI 72).
 * Clickable keys send noteOn/noteOff to the Voice array.
 */
public class PianoKeyboard extends JComponent {

    private static final int FIRST_NOTE = 48; // C3
    private static final int LAST_NOTE = 72;  // C5
    private static final int NOTE_COUNT = LAST_NOTE - FIRST_NOTE + 1; // 25 notes

    // Which notes in an octave are black keys (relative to C)
    private static final boolean[] IS_BLACK = {
        false, true, false, true, false, false, true, false, true, false, true, false
    };

    private static final int WHITE_KEY_COUNT;
    static {
        int count = 0;
        for (int n = FIRST_NOTE; n <= LAST_NOTE; n++) {
            if (!IS_BLACK[n % 12]) count++;
        }
        WHITE_KEY_COUNT = count;
    }

    private Voice[] voices;
    private double sourceFundamentalHz = 440.0;
    private int pressedNote = -1;

    public PianoKeyboard() {
        setPreferredSize(new Dimension(400, 100));
        setMinimumSize(new Dimension(200, 60));

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int note = noteFromPoint(e.getX(), e.getY());
                if (note >= 0) {
                    pressedNote = note;
                    sendNoteOn(note);
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (pressedNote >= 0) {
                    sendNoteOff(pressedNote);
                    pressedNote = -1;
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int note = noteFromPoint(e.getX(), e.getY());
                if (note != pressedNote) {
                    if (pressedNote >= 0) sendNoteOff(pressedNote);
                    pressedNote = note;
                    if (note >= 0) sendNoteOn(note);
                    repaint();
                }
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    public void setVoices(Voice[] voices) {
        this.voices = voices;
    }

    public void setSourceFundamentalHz(double hz) {
        this.sourceFundamentalHz = hz;
    }

    private void sendNoteOn(int note) {
        if (voices == null) return;
        for (Voice v : voices) {
            if (!v.isActive()) {
                v.noteOn(note, 100, sourceFundamentalHz);
                return;
            }
        }
    }

    private void sendNoteOff(int note) {
        if (voices == null) return;
        for (Voice v : voices) {
            if (v.isActive() && v.getMidiNote() == note) {
                v.noteOff();
                return;
            }
        }
    }

    private boolean isNoteActive(int note) {
        if (voices == null) return false;
        for (Voice v : voices) {
            if (v.isActive() && v.getMidiNote() == note) return true;
        }
        return false;
    }

    /**
     * Determine which MIDI note was clicked. Black keys are checked first
     * since they overlay the white keys.
     */
    private int noteFromPoint(int x, int y) {
        int w = getWidth();
        int h = getHeight();
        double whiteWidth = (double) w / WHITE_KEY_COUNT;
        int blackHeight = (int) (h * 0.6);

        // Check black keys first (they're on top)
        if (y < blackHeight) {
            int whiteIndex = 0;
            for (int n = FIRST_NOTE; n <= LAST_NOTE; n++) {
                if (IS_BLACK[n % 12]) {
                    // Black key sits between the two adjacent white keys
                    double bx = whiteIndex * whiteWidth - whiteWidth * 0.3;
                    double bw = whiteWidth * 0.6;
                    if (x >= bx && x < bx + bw) {
                        return n;
                    }
                } else {
                    whiteIndex++;
                }
            }
        }

        // Check white keys
        int whiteIndex = (int) (x / whiteWidth);
        if (whiteIndex < 0) whiteIndex = 0;
        if (whiteIndex >= WHITE_KEY_COUNT) whiteIndex = WHITE_KEY_COUNT - 1;

        // Map white key index back to MIDI note
        int count = 0;
        for (int n = FIRST_NOTE; n <= LAST_NOTE; n++) {
            if (!IS_BLACK[n % 12]) {
                if (count == whiteIndex) return n;
                count++;
            }
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        double whiteWidth = (double) w / WHITE_KEY_COUNT;
        int blackHeight = (int) (h * 0.6);

        // Draw white keys
        int whiteIndex = 0;
        for (int n = FIRST_NOTE; n <= LAST_NOTE; n++) {
            if (!IS_BLACK[n % 12]) {
                int kx = (int) (whiteIndex * whiteWidth);
                int kw = (int) ((whiteIndex + 1) * whiteWidth) - kx;

                if (isNoteActive(n) || pressedNote == n) {
                    g2.setColor(Theme.ACCENT);
                } else {
                    g2.setColor(Theme.ZINC_200);
                }
                g2.fillRect(kx, 0, kw - 1, h - 1);

                // Border
                g2.setColor(Theme.BORDER);
                g2.drawRect(kx, 0, kw - 1, h - 1);

                whiteIndex++;
            }
        }

        // Draw black keys
        whiteIndex = 0;
        for (int n = FIRST_NOTE; n <= LAST_NOTE; n++) {
            if (IS_BLACK[n % 12]) {
                double bx = whiteIndex * whiteWidth - whiteWidth * 0.3;
                double bw = whiteWidth * 0.6;

                if (isNoteActive(n) || pressedNote == n) {
                    g2.setColor(Theme.ACCENT);
                } else {
                    g2.setColor(Theme.ZINC_900);
                }
                g2.fillRoundRect((int) bx, 0, (int) bw, blackHeight, 4, 4);

                g2.setColor(Theme.BORDER);
                g2.drawRoundRect((int) bx, 0, (int) bw, blackHeight, 4, 4);
            } else {
                whiteIndex++;
            }
        }

        g2.dispose();
    }
}
