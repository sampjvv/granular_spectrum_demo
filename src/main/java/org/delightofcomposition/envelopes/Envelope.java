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
import javax.swing.JOptionPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JFileChooser;
import java.io.File;
import java.awt.event.WindowListener;
import java.awt.event.WindowEvent;

/**
 * Write a description of class Envelope here.
 *
 * @author (your name)
 * @version (a version number or a date)
 */
public class Envelope {
    public double[] times, values;
    double duration;
    int lastX = 0;
    Timer clock;
    public ArrayList<int[]> coords;
    boolean block;
    File file;
    public int type;// 0 = continuous, 1 = linear

    public Envelope(double[] t, double[] v) {
        times = t;
        values = v;
        duration = 1;
        coords = new ArrayList<int[]>();
        coords.add(new int[] { 0, 0 });
        coords.add(new int[] { Integer.MAX_VALUE, 0 });
        type = 0;
    }

    public Envelope() {
        duration = 1;
        coords = new ArrayList<int[]>();
        coords.add(new int[] { 0, 0 });
        coords.add(new int[] { Integer.MAX_VALUE, 0 });
        type = 0;
        // gui();

        // String name = JOptionPane.showInputDialog(frame, "Please name your
        // envelope.");
    }

    public Envelope(String name) {
        duration = 1;
        coords = new ArrayList<int[]>();
        coords.add(new int[] { 0, 0 });
        coords.add(new int[] { Integer.MAX_VALUE, 0 });
        new JOptionPane().showConfirmDialog(null, "Please design a " + name + " envelope.");
        type = 0;
        // gui();

    }

    public File getFile() {
        if (file != null)
            return file;
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        chooser.showOpenDialog(null);
        file = chooser.getSelectedFile();
        return file;
    }

    public void gui() {
        block = true;

        coords = new ArrayList<int[]>();
        coords.add(new int[] { 0, 800 });
        coords.add(new int[] { 800, 800 });
        JFrame frame = new JFrame("Envelope Design");
        frame.setBounds(0, 0, 800, 800);

        JMenuBar bar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open");
        openItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
                chooser.showOpenDialog(frame);
                File file = chooser.getSelectedFile();
                coords = new ArrayList<int[]>();
                coords.add(new int[] { 0, 800 });
                coords.add(new int[] { 800, 800 });
                TextIO.readFile(file.getPath());
                try {
                    while (true) {
                        coords.add(new int[] { Integer.parseInt(TextIO.getln()), Integer.parseInt(TextIO.getln()) });
                    }
                } catch (Exception ex) {
                }

            }
        });
        JMenuItem saveItem = new JMenuItem("Save and Exit");
        saveItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
                chooser.showSaveDialog(frame);
                File file = chooser.getSelectedFile();
                TextIO.writeFile(file.getPath());
                times = new double[coords.size()];
                values = new double[coords.size()];
                for (int i = 0; i < coords.size(); i++) {
                    int[] coord = coords.get(i);
                    TextIO.putln(coord[0]);
                    TextIO.putln(coord[1]);
                    times[i] = coord[0] / 800.0;
                    values[i] = (800 - coord[1]) / 800.0;
                }
                clock.stop();
                frame.setVisible(false);
                block = false;
            }
        });

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clock.stop();
                frame.setVisible(false);
                block = false;
            }
        });

        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(exitItem);
        bar.add(fileMenu);

        frame.setJMenuBar(bar);

        JPanel panel = new JPanel() {
            @Override
            public void paint(Graphics g) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 800, 800);
                /*
                 * g.setColor(Color.BLACK);
                 * for(int[] coord: coords){
                 * g.fillOval(coord[0], coord[1], 3, 3);
                 * }
                 */
                g.setColor(Color.RED);
                for (int i = 0; i < coords.size() - 1; i++) {
                    g.drawLine(coords.get(i)[0], coords.get(i)[1], coords.get(i + 1)[0], coords.get(i + 1)[1]);
                }
                times = new double[coords.size()];
                values = new double[coords.size()];
                for (int i = 0; i < coords.size(); i++) {
                    int[] coord = coords.get(i);
                    times[i] = coord[0] / 800.0;
                    values[i] = (800 - coord[1]) / 800.0;
                }
            }

        };
        clock = new Timer(50, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                panel.validate();
                panel.repaint();
            }
        });
        clock.start();

        panel.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseMoved(MouseEvent e) {
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int[] coord = { e.getX(), e.getY() };
                int index = Collections.binarySearch(coords, coord, new Comparator<int[]>() {
                    @Override
                    public int compare(int[] c1, int[] c2) {
                        return c1[0] - c2[0];
                    }
                });
                if (index > 0) {
                    coords.remove(index);
                } else {
                    index = -(index + 1);
                }

                coords.add(index, coord);
                int i = index;
                while (i >= 0 && coords.get(i)[0] > lastX) {
                    coords.get(i)[1] = e.getY();
                    i--;
                }

                coords.get(0)[1] = coords.get(1)[1];
                coords.get(coords.size() - 1)[1] = coords.get(coords.size() - 2)[1];
                lastX = e.getX();
            }
        });

        frame.add(panel);

        frame.addWindowListener(new WindowListener() {
            public void windowActivated(WindowEvent e) {
            }

            public void windowClosed(WindowEvent e) {
                block = false;
            }

            public void windowClosing(WindowEvent e) {
                block = false;
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
        frame.setVisible(true);
        while (block)
            try {
                Thread.sleep(250);
            } catch (Exception e) {
            }
    }

    public void display() {

        JPanel jp = new JPanel() {
            // double[] myNormVals;
            @Override
            public void paint(Graphics g) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 500, 500);

                g.setColor(Color.BLACK);
                // duration = 10;
                for (double i = 0; i < 1; i += 1 / 500.0) {
                    double d = 500 * getValue(i);
                    int x = (int) (500 * i / 1);
                    g.fillOval(x, (int) (500 - d), 3, 3);
                }

            }
        };
        JFrame jf = new JFrame("ENVELOPE");
        jf.setBounds(0, 0, 500, 500);
        jf.add(jp);
        jf.setVisible(true);
        jp.repaint();
        // System.out.println(size());
    }

    public void setDuration(double dur) {
        duration = dur;
    }

    public double getValue(double time) {
        time /= duration;
        double x1 = 0;
        double x2 = 0;
        double y1 = 0;
        double y2 = 0;
        for (int i = 0; i < times.length && times[i] < time; i++) {
            x1 = times[i];
            y1 = values[i];
            if (i < times.length - 1) {
                x2 = times[i + 1];
                y2 = values[i + 1];
            } else {
                x2 = x1 + 1;
                y2 = y1;
            }
        }
        if (x1 == x2)
            return y2;// values[0] ... maybe before it was a drawn env redundant X's indicated a
                      // mistake or trival case

        double m = (y1 - y2) / (x1 - x2);
        double b = y1 - m * x1;
        return m * time + b;

    }
}
