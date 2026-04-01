package org.delightofcomposition.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import org.delightofcomposition.realtime.Voice;

/**
 * Custom-painted piano keyboard with dynamic octave range.
 * Visual display and click detection shift with octaveShift.
 * Computer keyboard keys play notes relative to the shifted range.
 */
public class PianoKeyboard extends JComponent {

    private static final int BASE_FIRST_NOTE = 48; // C3
    private static final int BASE_LAST_NOTE = 72;  // C5

    // Which notes in an octave are black keys (relative to C)
    private static final boolean[] IS_BLACK = {
        false, true, false, true, false, false, true, false, true, false, true, false
    };

    private static final String[] NOTE_NAMES = {
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    // Computer keyboard → base MIDI note mapping (standard DAW layout)
    private static final Map<Integer, Integer> KEY_TO_NOTE = new LinkedHashMap<>();
    static {
        // White keys: a s d f g h j k l ;
        KEY_TO_NOTE.put(KeyEvent.VK_A, 48);           // C3
        KEY_TO_NOTE.put(KeyEvent.VK_S, 50);           // D3
        KEY_TO_NOTE.put(KeyEvent.VK_D, 52);           // E3
        KEY_TO_NOTE.put(KeyEvent.VK_F, 53);           // F3
        KEY_TO_NOTE.put(KeyEvent.VK_G, 55);           // G3
        KEY_TO_NOTE.put(KeyEvent.VK_H, 57);           // A3
        KEY_TO_NOTE.put(KeyEvent.VK_J, 59);           // B3
        KEY_TO_NOTE.put(KeyEvent.VK_K, 60);           // C4
        KEY_TO_NOTE.put(KeyEvent.VK_L, 62);           // D4
        KEY_TO_NOTE.put(KeyEvent.VK_SEMICOLON, 64);   // E4
        // Black keys: w e t y u o p
        KEY_TO_NOTE.put(KeyEvent.VK_W, 49);           // C#3
        KEY_TO_NOTE.put(KeyEvent.VK_E, 51);           // D#3
        KEY_TO_NOTE.put(KeyEvent.VK_T, 54);           // F#3
        KEY_TO_NOTE.put(KeyEvent.VK_Y, 56);           // G#3
        KEY_TO_NOTE.put(KeyEvent.VK_U, 58);           // A#3
        KEY_TO_NOTE.put(KeyEvent.VK_O, 61);           // C#4
        KEY_TO_NOTE.put(KeyEvent.VK_P, 63);           // D#4
    }

    private Voice[] voices;
    private double sourceFundamentalHz = 440.0;
    private int pressedNote = -1;
    private final Set<Integer> heldKeys = new HashSet<>();
    private int octaveShift = 0; // -4 to +4 octaves

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

        setupKeyboardBindings();
    }

    // ── Dynamic range based on octave shift ──

    private int getFirstNote() {
        return Math.max(0, Math.min(127, BASE_FIRST_NOTE + octaveShift * 12));
    }

    private int getLastNote() {
        return Math.max(0, Math.min(127, BASE_LAST_NOTE + octaveShift * 12));
    }

    private int getWhiteKeyCount() {
        int count = 0;
        for (int n = getFirstNote(); n <= getLastNote(); n++) {
            if (!IS_BLACK[n % 12]) count++;
        }
        return count;
    }

    private static String noteName(int midiNote) {
        return NOTE_NAMES[midiNote % 12] + (midiNote / 12 - 1);
    }

    // ── Keyboard bindings ──

    private void setupKeyboardBindings() {
        for (Map.Entry<Integer, Integer> entry : KEY_TO_NOTE.entrySet()) {
            int vk = entry.getKey();
            int baseNote = entry.getValue();
            String pressName = "pianoPress_" + vk;
            String releaseName = "pianoRelease_" + vk;

            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(vk, 0, false), pressName);
            getActionMap().put(pressName, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (voices == null || heldKeys.contains(vk)) return;
                    int note = baseNote + octaveShift * 12;
                    if (note < 0 || note > 127) return;
                    heldKeys.add(vk);
                    sendNoteOn(note);
                    repaint();
                }
            });

            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(vk, 0, true), releaseName);
            getActionMap().put(releaseName, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!heldKeys.remove(vk)) return;
                    int note = baseNote + octaveShift * 12;
                    if (note < 0 || note > 127) return;
                    sendNoteOff(note);
                    repaint();
                }
            });
        }

        // Z = octave down, X = octave up
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0, false), "octaveDown");
        getActionMap().put("octaveDown", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (octaveShift > -4) {
                    octaveShift--;
                    firePropertyChange("octaveShift", octaveShift + 1, octaveShift);
                    repaint();
                }
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_X, 0, false), "octaveUp");
        getActionMap().put("octaveUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (octaveShift < 4) {
                    octaveShift++;
                    firePropertyChange("octaveShift", octaveShift - 1, octaveShift);
                    repaint();
                }
            }
        });
    }

    // ── Public API ──

    public int getOctaveShift() {
        return octaveShift;
    }

    public void setOctaveShift(int shift) {
        int old = this.octaveShift;
        this.octaveShift = Math.max(-4, Math.min(4, shift));
        firePropertyChange("octaveShift", old, this.octaveShift);
        repaint();
    }

    public void setVoices(Voice[] voices) {
        this.voices = voices;
    }

    public void setSourceFundamentalHz(double hz) {
        this.sourceFundamentalHz = hz;
    }

    // ── Voice interaction ──

    private void sendNoteOn(int note) {
        if (voices == null) return;
        for (Voice v : voices) {
            if (!v.isActive()) {
                v.noteOn(note, 100);
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

    // ── Click detection (uses shifted range) ──

    private int noteFromPoint(int x, int y) {
        int w = getWidth();
        int h = getHeight();
        int firstNote = getFirstNote();
        int lastNote = getLastNote();
        int whiteCount = getWhiteKeyCount();
        if (whiteCount == 0) return -1;
        double whiteWidth = (double) w / whiteCount;
        int blackHeight = (int) (h * 0.6);

        // Check black keys first (they're on top)
        if (y < blackHeight) {
            int whiteIndex = 0;
            for (int n = firstNote; n <= lastNote; n++) {
                if (IS_BLACK[n % 12]) {
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
        if (whiteIndex >= whiteCount) whiteIndex = whiteCount - 1;

        int count = 0;
        for (int n = firstNote; n <= lastNote; n++) {
            if (!IS_BLACK[n % 12]) {
                if (count == whiteIndex) return n;
                count++;
            }
        }
        return -1;
    }

    // ── Painting (uses shifted range + note labels) ──

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int firstNote = getFirstNote();
        int lastNote = getLastNote();
        int whiteCount = getWhiteKeyCount();
        if (whiteCount == 0) { g2.dispose(); return; }
        double whiteWidth = (double) w / whiteCount;
        int blackHeight = (int) (h * 0.6);
        boolean sw = Theme.isSynthwave();

        // Draw white keys
        int whiteIndex = 0;
        for (int n = firstNote; n <= lastNote; n++) {
            if (!IS_BLACK[n % 12]) {
                int kx = (int) (whiteIndex * whiteWidth);
                int kw = (int) ((whiteIndex + 1) * whiteWidth) - kx;
                boolean active = isNoteActive(n) || pressedNote == n;

                if (sw) {
                    if (active) {
                        g2.setColor(Theme.SW_CYAN);
                        g2.fillRect(kx, 0, kw - 1, h - 1);
                        // Glow effect for active keys
                        SynthwavePainter.paintGlow(g2, kx, 0, kw - 1, h - 1, Theme.SW_CYAN, 3);
                    } else {
                        // Light purple-gray with subtle scanlines
                        g2.setColor(Theme.ZINC_200);
                        g2.fillRect(kx, 0, kw - 1, h - 1);
                        // Subtle horizontal scanlines on key surface
                        g2.setColor(new Color(Theme.SW_PURPLE.getRed(), Theme.SW_PURPLE.getGreen(),
                                Theme.SW_PURPLE.getBlue(), 12));
                        for (int sy = 3; sy < h; sy += 4) {
                            g2.drawLine(kx, sy, kx + kw - 2, sy);
                        }
                    }
                    // Bevel effect
                    java.awt.Composite orig = g2.getComposite();
                    g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.3f));
                    g2.setColor(Color.WHITE);
                    g2.drawLine(kx + 1, 1, kx + kw - 3, 1); // top highlight
                    g2.drawLine(kx + 1, 1, kx + 1, h - 3);  // left highlight
                    g2.setColor(Color.BLACK);
                    g2.drawLine(kx + 1, h - 2, kx + kw - 2, h - 2); // bottom shadow
                    g2.setComposite(orig);
                    // Thick pixel border
                    g2.setColor(Theme.SW_PURPLE);
                    g2.setStroke(new java.awt.BasicStroke(2f));
                    g2.drawRect(kx, 0, kw - 1, h - 1);
                } else {
                    g2.setColor(active ? Theme.ACCENT : Theme.ZINC_200);
                    g2.fillRect(kx, 0, kw - 1, h - 1);
                    g2.setColor(Theme.BORDER);
                    g2.drawRect(kx, 0, kw - 1, h - 1);
                }

                // Note label
                String label = noteName(n);
                if (sw) {
                    g2.setFont(SynthwaveFonts.DISPLAY_SMALL);
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = kx + (kw - fm.stringWidth(label)) / 2;
                    int ty = h - 4;
                    g2.setColor(active ? Theme.BG : Theme.SW_PURPLE);
                    g2.drawString(label, tx, ty);
                } else {
                    g2.setFont(Theme.FONT_BASE.deriveFont(9f));
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = kx + (kw - fm.stringWidth(label)) / 2;
                    int ty = h - 4;
                    g2.setColor(Theme.ZINC_500);
                    g2.drawString(label, tx, ty);
                }

                whiteIndex++;
            }
        }

        // Draw black keys
        whiteIndex = 0;
        for (int n = firstNote; n <= lastNote; n++) {
            if (IS_BLACK[n % 12]) {
                double bx = whiteIndex * whiteWidth - whiteWidth * 0.3;
                double bw = whiteWidth * 0.6;
                boolean active = isNoteActive(n) || pressedNote == n;

                if (sw) {
                    if (active) {
                        g2.setColor(Theme.SW_CYAN);
                        g2.fillRect((int) bx, 0, (int) bw, blackHeight);
                        SynthwavePainter.paintGlow(g2, (int) bx, 0, (int) bw, blackHeight, Theme.SW_CYAN, 3);
                    } else {
                        // Deep purple gradient
                        java.awt.GradientPaint gp = new java.awt.GradientPaint(
                                0, 0, Theme.SW_BG_RAISED,
                                0, blackHeight, Theme.SW_BG_DEEP);
                        g2.setPaint(gp);
                        g2.fillRect((int) bx, 0, (int) bw, blackHeight);
                    }
                    // Bevel
                    java.awt.Composite orig = g2.getComposite();
                    g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.12f));
                    g2.setColor(Theme.SW_LAVENDER);
                    g2.drawLine((int) bx + 1, 1, (int)(bx + bw) - 2, 1);
                    g2.drawLine((int) bx + 1, 1, (int) bx + 1, blackHeight - 2);
                    g2.setComposite(orig);
                    g2.setColor(Theme.SW_PURPLE);
                    g2.setStroke(new java.awt.BasicStroke(2f));
                    g2.drawRect((int) bx, 0, (int) bw, blackHeight);
                } else {
                    g2.setColor(active ? Theme.ACCENT : Theme.ZINC_900);
                    g2.fillRoundRect((int) bx, 0, (int) bw, blackHeight, 4, 4);
                    g2.setColor(Theme.BORDER);
                    g2.drawRoundRect((int) bx, 0, (int) bw, blackHeight, 4, 4);
                }
            } else {
                whiteIndex++;
            }
        }

        g2.dispose();
    }
}
