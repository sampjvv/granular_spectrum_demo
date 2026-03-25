package org.delightofcomposition.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

/**
 * Post-render visualization with waveform and spectrogram modes.
 * Uses SegmentedControl tabs to switch between views.
 */
public class WaveformDisplay extends JPanel {

    private static final int STFT_WINDOW = 2048;
    private static final int STFT_HOP = 512;

    private float[][] stereoBuffer;
    private final SegmentedControl tabs;
    private final WaveformCanvas canvas;
    private final JPanel card;
    private BufferedImage spectrogramImage;
    private volatile boolean computingSpectrogram = false;

    public WaveformDisplay() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Card wrapper
        card = new JPanel(new BorderLayout(0, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_LG, Theme.RADIUS_LG);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_LG, Theme.RADIUS_LG);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // Header with tabs
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(Theme.sectionLabel("Output"), BorderLayout.WEST);

        tabs = new SegmentedControl(new String[]{"Waveform", "Spectrogram"}, 0);
        tabs.setPreferredSize(new Dimension(200, 28));
        tabs.setMaximumSize(new Dimension(200, 28));
        header.add(tabs, BorderLayout.EAST);

        card.add(header, BorderLayout.NORTH);

        canvas = new WaveformCanvas();
        card.add(canvas, BorderLayout.CENTER);

        tabs.addChangeListener(e -> canvas.repaint());

        add(card);
    }

    public void setTimbralPreview(TimbralPreview preview) {
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        south.add(Theme.sectionLabel("Timbral Blend"), BorderLayout.NORTH);
        south.add(preview, BorderLayout.CENTER);
        card.add(south, BorderLayout.SOUTH);
        card.revalidate();
    }

    public void setBuffer(float[][] buffer) {
        this.stereoBuffer = buffer;
        this.spectrogramImage = null;
        // Pre-compute spectrogram in background
        if (buffer != null) {
            computeSpectrogram(buffer);
        }
        canvas.repaint();
    }

    private void computeSpectrogram(float[][] buffer) {
        if (computingSpectrogram) return;
        computingSpectrogram = true;

        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                // Mono mixdown
                float[] mono = new float[buffer[0].length];
                for (int i = 0; i < mono.length; i++) {
                    mono[i] = (buffer[0][i] + buffer[1][i]) / 2f;
                }

                int numFrames = Math.max(1, (mono.length - STFT_WINDOW) / STFT_HOP + 1);
                int freqBins = STFT_WINDOW / 2;
                // Limit to ~10kHz (assuming 44100 sample rate)
                int maxBin = Math.min(freqBins, (int) (10000.0 / 44100 * STFT_WINDOW));

                double[][] magnitudes = new double[numFrames][maxBin];
                double globalMax = 0;

                for (int frame = 0; frame < numFrames; frame++) {
                    int offset = frame * STFT_HOP;
                    double[] windowed = new double[STFT_WINDOW];
                    for (int n = 0; n < STFT_WINDOW && (offset + n) < mono.length; n++) {
                        // Hamming window
                        double w = 0.54 - 0.46 * Math.cos(2 * Math.PI * n / (STFT_WINDOW - 1));
                        windowed[n] = mono[offset + n] * w;
                    }

                    // Pad to power of 2 (STFT_WINDOW is already 2048)
                    double[][] fftResult = org.delightofcomposition.sound.FFT.getTransformationWPhase(windowed);
                    for (int bin = 0; bin < maxBin; bin++) {
                        magnitudes[frame][bin] = fftResult[0][bin];
                        globalMax = Math.max(globalMax, magnitudes[frame][bin]);
                    }
                }

                // Create image
                int imgW = numFrames;
                int imgH = maxBin;
                BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);

                for (int x = 0; x < imgW; x++) {
                    for (int y = 0; y < imgH; y++) {
                        int freqIdx = imgH - 1 - y; // flip so low freq at bottom
                        double mag = (globalMax > 0) ? magnitudes[x][freqIdx] / globalMax : 0;
                        // Log scale for better visualization
                        mag = Math.log1p(mag * 20) / Math.log1p(20);
                        mag = Math.max(0, Math.min(1, mag));

                        // Color gradient: BG_INPUT -> ACCENT -> ZINC_50
                        Color c = gradientColor(mag);
                        img.setRGB(x, y, c.getRGB());
                    }
                }

                return img;
            }

            @Override
            protected void done() {
                try {
                    spectrogramImage = get();
                } catch (Exception e) {
                    // ignore
                }
                computingSpectrogram = false;
                canvas.repaint();
            }
        }.execute();
    }

    private static Color gradientColor(double t) {
        // 0.0 = BG_INPUT, 0.5 = ACCENT, 1.0 = ZINC_50
        if (t < 0.5) {
            double f = t / 0.5;
            return blendColor(Theme.BG_INPUT, Theme.ACCENT, f);
        } else {
            double f = (t - 0.5) / 0.5;
            return blendColor(Theme.ACCENT, Theme.ZINC_50, f);
        }
    }

    private static Color blendColor(Color a, Color b, double f) {
        return new Color(
                (int) (a.getRed() + f * (b.getRed() - a.getRed())),
                (int) (a.getGreen() + f * (b.getGreen() - a.getGreen())),
                (int) (a.getBlue() + f * (b.getBlue() - a.getBlue())));
    }

    /**
     * Canvas for rendering waveform or spectrogram.
     */
    private class WaveformCanvas extends JComponent {

        WaveformCanvas() {
            setPreferredSize(new Dimension(600, 120));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Background
            g2.setColor(Theme.BG_INPUT);
            g2.fillRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);

            if (stereoBuffer == null || stereoBuffer[0].length == 0) {
                // Empty state
                g2.setColor(Theme.FG_DIM);
                g2.setFont(Theme.FONT_SMALL);
                String msg = "Render audio to see visualization";
                int tw = g2.getFontMetrics().stringWidth(msg);
                g2.drawString(msg, (w - tw) / 2, h / 2 + 4);
            } else if (tabs.getSelectedIndex() == 0) {
                drawWaveform(g2, w, h);
            } else {
                drawSpectrogram(g2, w, h);
            }

            // Border
            g2.setColor(Theme.BORDER_SUBTLE);
            g2.setStroke(new BasicStroke(1));
            g2.drawRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);

            g2.dispose();
        }

        private void drawWaveform(Graphics2D g2, int w, int h) {
            float[] left = stereoBuffer[0];
            float[] right = stereoBuffer[1];
            int numSamples = left.length;

            // Center line
            g2.setColor(Theme.BORDER_SUBTLE);
            g2.drawLine(0, h / 2, w, h / 2);

            // Draw waveform: for each pixel column, find min/max in sample range
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(1));

            for (int x = 0; x < w; x++) {
                int startSample = (int) ((long) x * numSamples / w);
                int endSample = (int) ((long) (x + 1) * numSamples / w);
                endSample = Math.min(endSample, numSamples);

                float minVal = 0, maxVal = 0;
                for (int s = startSample; s < endSample; s++) {
                    float mono = (left[s] + right[s]) / 2f;
                    minVal = Math.min(minVal, mono);
                    maxVal = Math.max(maxVal, mono);
                }

                int yMin = (int) (h / 2 - maxVal * h / 2);
                int yMax = (int) (h / 2 - minVal * h / 2);
                yMin = Math.max(0, Math.min(h - 1, yMin));
                yMax = Math.max(0, Math.min(h - 1, yMax));
                g2.drawLine(x, yMin, x, yMax);
            }
        }

        private void drawSpectrogram(Graphics2D g2, int w, int h) {
            if (spectrogramImage != null) {
                g2.drawImage(spectrogramImage, 0, 0, w, h, null);
            } else if (computingSpectrogram) {
                g2.setColor(Theme.FG_DIM);
                g2.setFont(Theme.FONT_SMALL);
                String msg = "Computing spectrogram...";
                int tw = g2.getFontMetrics().stringWidth(msg);
                g2.drawString(msg, (w - tw) / 2, h / 2 + 4);
            }
        }
    }
}
