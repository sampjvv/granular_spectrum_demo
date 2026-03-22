package org.delightofcomposition.envelopes;

import java.util.ArrayList;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Graphics;
import javax.swing.Timer;

import org.delightofcomposition.util.TextIO;

import java.awt.Color;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

import javax.swing.JOptionPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JFileChooser;
import java.io.File;
import java.awt.event.WindowListener;
import java.awt.event.WindowEvent;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.MouseListener;

/**
 * Write a description of class GUI here.
 * 
 * Controls:
 * Linear Envelopes draw straight lines between points; continuous envelopes
 * draw continuous curves.
 * 
 * Linear Envelopes:
 * Click and drag to move a node.
 * Command / ctrl click to delete a node.
 * Option click to draw a straight horizontal line from the last node.
 *
 * @author (your name)
 * @version (a version number or a date)
 */
public class GUI {

    public Timer clock;
    public ArrayList<Envelope> envs;
    public static Envelope selectedEnv;
    public static int[] selectedCoord;
    public int lastX, lastX1;
    public File file;
    public double xView;
    public boolean shift;
    public static JMenu envMenu;
    boolean mouseLifted = true;
    int clickX = 0;
    int clickY = 0;
    double clickxView = xView;
    double scale = 1;
    public static ArrayList<Double> sectionEnds;
    public static int quantFact = 10;// 30// keeps there from being too many points (slows processor)
    public static int selectedCoordIndex = 0;// used for env type 1 so far (to prevent placing nodes out of order)

    /**
     * Constructor for objects of class GUI
     */

    public void hook() {
        // new Piece().piece2(envs);
    }

    public GUI() {
        xView = 0;
        shift = false;
        Toolkit myToolkit = Toolkit.getDefaultToolkit();
        Dimension screenSize = myToolkit.getScreenSize();
        int width = screenSize.width;

        envs = new ArrayList<Envelope>();
        sectionEnds = new ArrayList<Double>();
        JFrame frame = new JFrame("Envelope Design");
        frame.setBounds(0, 0, width, 600);

        JMenuBar bar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        envMenu = new JMenu("Envelope");

        JMenuItem newItem = new JMenuItem("New Continuous Env");
        newItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Envelope env = new Envelope();
                envs.add(env);
                selectedEnv = env;
                JMenuItem selectItem = new JMenuItem("Select " + (envs.size()));
                final int index = envs.size() - 1;
                selectItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        selectedEnv = envs.get(index);
                    }
                });
                envMenu.add(selectItem);
            }
        });

        JMenuItem newLinearItem = new JMenuItem("New Linear Env");
        newLinearItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Envelope env = new Envelope();
                env.type = 1;
                envs.add(env);
                selectedEnv = env;
                JMenuItem selectItem = new JMenuItem("Select " + (envs.size()));
                final int index = envs.size() - 1;
                selectItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        selectedEnv = envs.get(index);
                    }
                });
                envMenu.add(selectItem);
            }
        });

        JMenuItem openItem = new JMenuItem("Open");
        openItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
                chooser.showOpenDialog(frame);
                file = chooser.getSelectedFile();
                envs = GUI.open(file);
                selectedEnv = envs.get(0);
            }
        });
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (file == null) {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
                    chooser.showSaveDialog(null);
                    file = chooser.getSelectedFile();
                }
                TextIO.writeFile(file.getPath());
                for (Envelope env : envs) {
                    env.times = new double[env.coords.size()];
                    env.values = new double[env.coords.size()];
                    TextIO.putln("ENV");
                    TextIO.putln("ENV TYPE");
                    TextIO.putln(env.type);
                    for (int i = 0; i < env.coords.size(); i++) {
                        int[] coord = env.coords.get(i);
                        TextIO.putln(coord[0]);
                        TextIO.putln(coord[1]);
                        env.times[i] = coord[0] / (100.0);
                        env.values[i] = (500 - coord[1]) / 500.0;
                    }
                }
            }
        });

        JMenuItem hookItem = new JMenuItem("Execute Script Hook");
        hookItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hook();
            }
        });

        fileMenu.add(hookItem);
        fileMenu.add(newItem);
        fileMenu.add(newLinearItem);
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        bar.add(fileMenu);

        JMenuItem sinusoid = new JMenuItem("Sinusoid");
        sinusoid.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectedEnv.coords = new ArrayList<int[]>();
                double freq = 1 / (Math.random() * 20 + 10);
                double phase = Math.random() * Math.PI;
                for (int x = 0; x < 100 * 60 * 15; x++) {
                    selectedEnv.coords.add(new int[] { x, (int) (500 - 500 *
                            (Math.sin(phase + freq * 2 * Math.PI * x / 100.0) + 1) / 2.0) });
                }
            }
        });
        envMenu.add(sinusoid);

        JMenuItem deleteEnv = new JMenuItem("deleteEnv");
        deleteEnv.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                envs.remove(selectedEnv);
            }
        });
        envMenu.add(deleteEnv);
        bar.add(envMenu);

        frame.setJMenuBar(bar);
        Color[] colors = new Color[] { Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.ORANGE, Color.PINK,
                Color.CYAN, Color.YELLOW };
        JPanel panel = new JPanel() {
            @Override
            public void paint(Graphics g) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, 500);
                double lastSectionEnd = 0;
                Random rand = new Random(123);
                for (double end : sectionEnds) {
                    int x1 = (int) Math.rint((lastSectionEnd * 100) * scale - xView);
                    int x2 = (int) Math.rint((end * 100) * scale - xView);
                    Color c = new Color(192 + (int) (rand.nextDouble() * 64), 192 + (int) (rand.nextDouble() * 64),
                            192 + (int) (rand.nextDouble() * 64));
                    g.setColor(c);
                    g.fillRect(x1, 0, x2 - x1, 500);
                    lastSectionEnd = end;
                }
                /*
                 * g.setColor(Color.BLACK);
                 * for(int[] coord: coords){
                 * g.fillOval(coord[0], coord[1], 3, 3);
                 * }
                 */
                int n = 0;
                for (Envelope env : envs) {
                    Color c = colors[n++ % colors.length];

                    if (env == selectedEnv)
                        c = c.darker();

                    g.setColor(c);

                    for (int i = 0; i < env.coords.size() - 1; i++) {
                        int lastX = (int) Math.rint((env.coords.get(i + 1)[0]) * scale - xView);
                        if (env.coords.get(i + 1)[0] == Integer.MAX_VALUE)
                            lastX = Integer.MAX_VALUE;
                        g.drawLine((int) Math.rint((env.coords.get(i)[0]) * scale - xView),
                                env.coords.get(i)[1],
                                lastX,
                                env.coords.get(i + 1)[1]);
                    }
                    env.times = new double[env.coords.size()];
                    env.values = new double[env.coords.size()];
                    for (int i = 0; i < env.coords.size(); i++) {
                        int[] coord = env.coords.get(i);
                        env.times[i] = coord[0] / (100.0);
                        env.values[i] = (500 - coord[1]) / 500.0;
                    }

                    int offset = ((int) Math.rint(xView)) % (int) Math.rint(100 * scale);
                    int sec = ((int) Math.rint(xView)) / (int) Math.rint(100 * scale);
                    g.setColor(Color.BLACK);
                    int secFreq = 1;
                    if (scale <= 0.25)
                        secFreq = (int) Math.pow(2, (-Math.log(scale) / Math.log(2)));

                    for (int i = (int) Math.rint(100 * scale) - offset; i < width; i += (int) Math.rint(100 * scale)
                            * secFreq) {
                        int min = (sec + 1) / 60;
                        int dispSec = (sec + 1) - (60 * min);
                        g.drawString((min) + "'" + dispSec + "''", i, 10);
                        sec += secFreq;
                    }
                }
            }

        };
        clock = new Timer(50, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                panel.validate();
                panel.repaint();
                // System.out.println("HI");
            }
        });
        clock.start();

        panel.addMouseListener(new MouseListener() {
            public void mouseClicked(MouseEvent e) {

            }

            public void mouseEntered(MouseEvent e) {
            }

            public void mouseExited(MouseEvent e) {
            }

            public void mousePressed(MouseEvent e) {
                clickX = e.getX();
                clickY = e.getY();
                clickxView = xView;

                quantFact = 10;// 30// keeps there from being too many points (slows processor)
                int x = (int) (Math.rint((e.getX() + xView) / (scale * quantFact)) * quantFact);
                if (selectedEnv.type == 1) {
                    if (e.getModifiersEx() == 1024) {// don't add a point on modified click, but add it here
                                                     // (unmodified)

                        int[] coord = { x, e.getY() };
                        int index = Collections.binarySearch(selectedEnv.coords, coord, new Comparator<int[]>() {
                            @Override
                            public int compare(int[] c1, int[] c2) {
                                return c1[0] - c2[0];
                            }
                        });

                        if (index > 0) {
                            selectedEnv.coords.remove(index);
                        } else {
                            index = -(index + 1);
                        }

                        selectedEnv.coords.add(index, coord);

                        selectedEnv.coords.get(0)[1] = selectedEnv.coords.get(1)[1];
                        selectedEnv.coords.get(selectedEnv.coords.size() - 1)[1] = selectedEnv.coords
                                .get(selectedEnv.coords.size() - 2)[1];
                        selectedCoord = coord;
                        selectedCoordIndex = index;

                    } else if (e.getModifiersEx() == 1280 || e.getModifiersEx() == 1152) { // delete point on command click
                        int shortestX = Integer.MAX_VALUE;
                        int[] closest = null;
                        for (int[] coord : selectedEnv.coords) {
                            int dist = Math.abs(coord[0] - x);
                            if (dist < shortestX) {
                                shortestX = dist;
                                closest = coord;
                            }
                        }
                        selectedEnv.coords.remove(closest);
                    } else if (e.getModifiersEx() == 1536) { // flat line on option click
                        int[] lastNode = null;
                        int index = 0;
                        for (int i = 0; i < selectedEnv.coords.size(); i++) {
                            int[] coord = selectedEnv.coords.get(i);
                            if (coord[0] > x) {
                                break;
                            }
                            lastNode = coord;
                            index = i;
                        }
                        if (lastNode != null) {
                            int[] coord = new int[] { x, lastNode[1] };
                            selectedEnv.coords.add(index + 1, coord);
                            selectedCoord = coord;
                            selectedCoordIndex = index + 1;
                        }
                    }

                }
                System.out.println(e.getModifiersEx());
            }

            public void mouseReleased(MouseEvent e) {
                mouseLifted = true;
            }
        });

        panel.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseMoved(MouseEvent e) {
                lastX1 = e.getX();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int x = e.getX();
                if (e.getModifiersEx() == 1088) {// shift
                    xView = clickxView + (clickX - x);
                    // xView -= (e.getX() - lastX);
                } else if (selectedEnv.type == 0) {
                    quantFact = 10;// 30// keeps there from being too many points (slows processor)
                    x = (int) (Math.rint((e.getX() + xView) / (scale * quantFact)) * quantFact);
                    int[] coord = { x, e.getY() };
                    int index = Collections.binarySearch(selectedEnv.coords, coord, new Comparator<int[]>() {
                        @Override
                        public int compare(int[] c1, int[] c2) {
                            return c1[0] - c2[0];
                        }
                    });

                    if (index > 0) {
                        selectedEnv.coords.remove(index);
                    } else {
                        index = -(index + 1);
                    }

                    selectedEnv.coords.add(index, coord);
                    int i = index;
                    if (!mouseLifted) {
                        if (x > lastX)
                            while (i >= 0 && selectedEnv.coords.get(i)[0] > lastX) {
                                selectedEnv.coords.get(i)[1] = e.getY();
                                i--;
                            }
                        else
                            while (selectedEnv.coords.get(i)[0] < lastX) {
                                selectedEnv.coords.get(i)[1] = e.getY();
                                i++;
                            }

                    }
                    mouseLifted = false;
                    selectedEnv.coords.get(0)[1] = selectedEnv.coords.get(1)[1];
                    selectedEnv.coords.get(selectedEnv.coords.size() - 1)[1] = selectedEnv.coords
                            .get(selectedEnv.coords.size() - 2)[1];

                } else if (selectedEnv.type == 1) {
                    quantFact = 10;// 30// keeps there from being too many points (slows processor)
                    x = (int) (Math.rint((e.getX() + xView) / (scale * quantFact)) * quantFact);
                    if (
                        (selectedCoordIndex == 0 || selectedEnv.coords.get(selectedCoordIndex - 1)[0] < x)
                        && (selectedCoordIndex == selectedEnv.coords.size() - 1 || x < selectedEnv.coords.get(selectedCoordIndex + 1)[0]))
                        selectedCoord[0] = x;
                    if (e.getModifiersEx() != 1536) {
                        selectedCoord[1] = e.getY();
                    }
                }
                lastX = x;
            }
        });

        frame.add(panel);

        frame.addWindowListener(new WindowListener() {
            public void windowActivated(WindowEvent e) {
            }

            public void windowClosed(WindowEvent e) {

            }

            public void windowClosing(WindowEvent e) {

            }

            public void windowDeactivated(WindowEvent e) {
            }

            public void windowDeiconified(WindowEvent e) {
            }

            public void windowIconified(WindowEvent e) {
            }

            public void windowOpened(WindowEvent e) {
            }
        });

        frame.addKeyListener(new KeyListener() {
            public void keyPressed(KeyEvent e) {
            }

            public void keyReleased(KeyEvent e) {
                try {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT:
                            xView -= 1600;
                            break;
                        case KeyEvent.VK_RIGHT:
                            xView += 1600;
                            break;
                        case KeyEvent.VK_1:
                            selectedEnv = envs.get(0);
                            break;
                        case KeyEvent.VK_2:
                            selectedEnv = envs.get(1);
                            break;
                        case KeyEvent.VK_3:
                            selectedEnv = envs.get(2);
                            break;
                        case KeyEvent.VK_4:
                            selectedEnv = envs.get(3);
                            break;
                        case KeyEvent.VK_5:
                            selectedEnv = envs.get(4);
                            break;
                        case KeyEvent.VK_6:
                            selectedEnv = envs.get(5);
                            break;
                        case KeyEvent.VK_7:
                            selectedEnv = envs.get(6);
                            break;
                        case KeyEvent.VK_8:
                            selectedEnv = envs.get(7);
                            break;
                        case KeyEvent.VK_9:
                            selectedEnv = envs.get(8);
                            break;
                        case KeyEvent.VK_EQUALS:
                            if (scale < 1)
                                scale *= 2;
                            else
                                scale++;
                            break;
                        case KeyEvent.VK_MINUS:
                            if (scale > 1)
                                scale--;
                            else if (scale > 0.01)
                                scale /= 2.0;
                            break;
                    }
                } catch (IndexOutOfBoundsException ex) {
                }
            }

            public void keyTyped(KeyEvent e) {
            }
        });

        frame.setVisible(true);
    }

    public static void openCorrespondingSectionFile(File envFile) {
        sectionEnds = new ArrayList<Double>();
        String path = envFile.getPath();
        path = path.replace(".txt", ".sections");
        if (!new File(path).exists()) {
            return;
        }

        TextIO.readFile(path);
        try {
            while (true) {
                sectionEnds.add(Double.parseDouble(TextIO.getln()));
            }
        } catch (Exception ex) {
        }
    }

    public static ArrayList<Envelope> open(File file) {
        openCorrespondingSectionFile(file);
        ArrayList<Envelope> envs = new ArrayList<Envelope>();
        Envelope env = null;
        TextIO.readFile(file.getPath());
        try {
            while (true) {
                String x = TextIO.getln();
                if (x.equals("ENV")) {
                    if (env != null) {
                        env.times = new double[env.coords.size()];
                        env.values = new double[env.coords.size()];
                        for (int i = 0; i < env.coords.size(); i++) {
                            int[] coord = env.coords.get(i);
                            env.times[i] = coord[0] / (100.0);
                            env.values[i] = (500 - coord[1]) / 500.0;
                        }
                        envs.add(env);
                        if (envMenu != null) { // only applies when envs are not opened programmatically
                            JMenuItem selectItem = new JMenuItem("Select " + (envs.size()));
                            final int index = envs.size() - 1;
                            selectItem.addActionListener(new ActionListener() {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    selectedEnv = envs.get(index);
                                }
                            });
                            envMenu.add(selectItem);
                        }
                    }
                    env = new Envelope();
                    env.file = file;
                    env.coords = new ArrayList<int[]>();
                    // env.coords.add(new int[]{0,800});
                    // env.coords.add(new int[]{800,800});
                    x = TextIO.getln();
                }
                int type = 0;
                if (x.equals("ENV TYPE")) {
                    type = Integer.parseInt(TextIO.getln());
                    env.type = type;
                    x = TextIO.getln();
                }
                String y = TextIO.getln();
                env.coords.add(new int[] { Integer.parseInt(x), Integer.parseInt(y) });
            }
        } catch (Exception ex) {
        }
        env.times = new double[env.coords.size()];
        env.values = new double[env.coords.size()];
        for (int i = 0; i < env.coords.size(); i++) {
            int[] coord = env.coords.get(i);
            env.times[i] = coord[0] / (100.0);
            env.values[i] = (500 - coord[1]) / 500.0;
        }
        envs.add(env);
        return envs;

    }

    /**
     * Creates a standalone envelope editor JPanel that can be embedded in another container.
     * The panel includes full painting and mouse interaction logic for envelope editing.
     *
     * @param panelWidth  the width of the editor canvas
     * @param panelHeight the height of the editor canvas
     * @param envelope    the envelope to edit (must be pre-created)
     * @return a JPanel with interactive envelope editing
     */
    public static JPanel createEditorPanel(int panelWidth, int panelHeight, Envelope envelope) {
        return createEditorPanel(panelWidth, panelHeight, envelope,
                Color.RED, Color.WHITE, new Color(230, 230, 230), Color.BLACK);
    }

    /**
     * Color-parameterized version for themed embedding.
     */
    public static JPanel createEditorPanel(int panelWidth, int panelHeight, Envelope envelope,
            Color curveColor, Color bgColor, Color gridColor, Color textColor) {
        // Local state for the embedded editor
        final double[] localXView = {0};
        final double[] localScale = {1.0};
        final boolean[] localMouseLifted = {true};
        final int[] localLastX = {0};
        final int[] localClickX = {0};
        final int[] localClickY = {0};
        final double[] localClickXView = {0};
        final int[] localSelectedCoordIndex = {0};
        final int[][] localSelectedCoord = {null};

        Color editorColor = curveColor;

        JPanel panel = new JPanel() {
            @Override
            public void paint(Graphics g) {
                int w = getWidth();
                int h = getHeight();
                g.setColor(bgColor);
                g.fillRect(0, 0, w, h);

                // Draw grid lines
                g.setColor(gridColor);
                for (int y = 0; y < h; y += h / 4) {
                    g.drawLine(0, y, w, y);
                }

                g.setColor(editorColor);
                for (int i = 0; i < envelope.coords.size() - 1; i++) {
                    int x1 = (int) Math.rint(envelope.coords.get(i)[0] * localScale[0] - localXView[0]);
                    int x2 = (int) Math.rint(envelope.coords.get(i + 1)[0] * localScale[0] - localXView[0]);
                    if (envelope.coords.get(i + 1)[0] == Integer.MAX_VALUE)
                        x2 = Integer.MAX_VALUE;

                    // Scale Y from GUI coords (0-500) to panel height
                    int y1 = (int) (envelope.coords.get(i)[1] * h / 500.0);
                    int y2 = (int) (envelope.coords.get(i + 1)[1] * h / 500.0);
                    g.drawLine(x1, y1, x2, y2);
                }

                // Draw nodes for linear envelopes
                if (envelope.type == 1) {
                    g.setColor(editorColor.darker());
                    for (int[] coord : envelope.coords) {
                        int x = (int) Math.rint(coord[0] * localScale[0] - localXView[0]);
                        int y = (int) (coord[1] * h / 500.0);
                        g.fillOval(x - 4, y - 4, 8, 8);
                    }
                }

                // Update times/values arrays for the envelope
                envelope.times = new double[envelope.coords.size()];
                envelope.values = new double[envelope.coords.size()];
                for (int i = 0; i < envelope.coords.size(); i++) {
                    int[] coord = envelope.coords.get(i);
                    envelope.times[i] = coord[0] / 100.0;
                    envelope.values[i] = (500 - coord[1]) / 500.0;
                }

                // Time labels
                int offset = ((int) Math.rint(localXView[0])) % (int) Math.max(1, Math.rint(100 * localScale[0]));
                int sec = ((int) Math.rint(localXView[0])) / (int) Math.max(1, Math.rint(100 * localScale[0]));
                g.setColor(textColor);
                int secFreq = 1;
                if (localScale[0] <= 0.25)
                    secFreq = (int) Math.pow(2, (-Math.log(localScale[0]) / Math.log(2)));
                int step = (int) Math.max(1, Math.rint(100 * localScale[0])) * secFreq;
                for (int i = step - offset; i < w; i += step) {
                    int min = (sec + 1) / 60;
                    int dispSec = (sec + 1) - (60 * min);
                    g.drawString(min + "'" + dispSec + "''", i, 12);
                    sec += secFreq;
                }
            }
        };

        panel.setPreferredSize(new Dimension(panelWidth, panelHeight));

        // Timer for repaint
        Timer timer = new Timer(50, e -> {
            panel.validate();
            panel.repaint();
        });
        timer.start();

        // Mouse listeners for editing
        panel.addMouseListener(new MouseListener() {
            public void mouseClicked(MouseEvent e) {}
            public void mouseEntered(MouseEvent e) {}
            public void mouseExited(MouseEvent e) {}

            public void mousePressed(MouseEvent e) {
                localClickX[0] = e.getX();
                localClickY[0] = e.getY();
                localClickXView[0] = localXView[0];
                int h = panel.getHeight();

                int x = (int) (Math.rint((e.getX() + localXView[0]) / (localScale[0] * quantFact)) * quantFact);
                int y = (int) (e.getY() * 500.0 / h);

                if (envelope.type == 1) {
                    if (e.getModifiersEx() == 1024) { // unmodified click: add/move point
                        int[] coord = {x, y};
                        int index = Collections.binarySearch(envelope.coords, coord, (c1, c2) -> c1[0] - c2[0]);
                        if (index > 0) {
                            envelope.coords.remove(index);
                        } else {
                            index = -(index + 1);
                        }
                        envelope.coords.add(index, coord);
                        envelope.coords.get(0)[1] = envelope.coords.get(1)[1];
                        envelope.coords.get(envelope.coords.size() - 1)[1] =
                                envelope.coords.get(envelope.coords.size() - 2)[1];
                        localSelectedCoord[0] = coord;
                        localSelectedCoordIndex[0] = index;
                    } else if (e.getModifiersEx() == 1280 || e.getModifiersEx() == 1152) { // ctrl/cmd click: delete
                        int shortestX = Integer.MAX_VALUE;
                        int[] closest = null;
                        for (int[] coord : envelope.coords) {
                            int dist = Math.abs(coord[0] - x);
                            if (dist < shortestX) {
                                shortestX = dist;
                                closest = coord;
                            }
                        }
                        envelope.coords.remove(closest);
                    } else if (e.getModifiersEx() == 1536) { // option click: flat line
                        int[] lastNode = null;
                        int index = 0;
                        for (int i = 0; i < envelope.coords.size(); i++) {
                            int[] coord = envelope.coords.get(i);
                            if (coord[0] > x) break;
                            lastNode = coord;
                            index = i;
                        }
                        if (lastNode != null) {
                            int[] coord = new int[]{x, lastNode[1]};
                            envelope.coords.add(index + 1, coord);
                            localSelectedCoord[0] = coord;
                            localSelectedCoordIndex[0] = index + 1;
                        }
                    }
                }
            }

            public void mouseReleased(MouseEvent e) {
                localMouseLifted[0] = true;
            }
        });

        panel.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseMoved(MouseEvent e) {}

            @Override
            public void mouseDragged(MouseEvent e) {
                int x = e.getX();
                int h = panel.getHeight();

                if (e.getModifiersEx() == 1088) { // shift+drag: scroll
                    localXView[0] = localClickXView[0] + (localClickX[0] - x);
                } else if (envelope.type == 0) {
                    int qx = (int) (Math.rint((e.getX() + localXView[0]) / (localScale[0] * quantFact)) * quantFact);
                    int y = (int) (e.getY() * 500.0 / h);
                    int[] coord = {qx, y};
                    int index = Collections.binarySearch(envelope.coords, coord, (c1, c2) -> c1[0] - c2[0]);
                    if (index > 0) {
                        envelope.coords.remove(index);
                    } else {
                        index = -(index + 1);
                    }
                    envelope.coords.add(index, coord);
                    int i = index;
                    if (!localMouseLifted[0]) {
                        if (qx > localLastX[0])
                            while (i >= 0 && envelope.coords.get(i)[0] > localLastX[0]) {
                                envelope.coords.get(i)[1] = y;
                                i--;
                            }
                        else
                            while (i < envelope.coords.size() && envelope.coords.get(i)[0] < localLastX[0]) {
                                envelope.coords.get(i)[1] = y;
                                i++;
                            }
                    }
                    localMouseLifted[0] = false;
                    envelope.coords.get(0)[1] = envelope.coords.get(1)[1];
                    envelope.coords.get(envelope.coords.size() - 1)[1] =
                            envelope.coords.get(envelope.coords.size() - 2)[1];
                    localLastX[0] = qx;
                } else if (envelope.type == 1 && localSelectedCoord[0] != null) {
                    int qx = (int) (Math.rint((e.getX() + localXView[0]) / (localScale[0] * quantFact)) * quantFact);
                    int sidx = localSelectedCoordIndex[0];
                    if ((sidx == 0 || envelope.coords.get(sidx - 1)[0] < qx)
                            && (sidx == envelope.coords.size() - 1 || qx < envelope.coords.get(sidx + 1)[0]))
                        localSelectedCoord[0][0] = qx;
                    if (e.getModifiersEx() != 1536) {
                        localSelectedCoord[0][1] = (int) (e.getY() * 500.0 / h);
                    }
                }
            }
        });

        return panel;
    }

    public static void main(String[] args) {
        new GUI();
    }
}
