package org.delightofcomposition.realtime;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.delightofcomposition.sound.WaveWriter;

/**
 * Accumulates stereo audio from the live audio engine and writes it
 * to a WAV file on demand. Designed to be called from the audio thread
 * with minimal overhead — write() is a fast no-op when not recording.
 */
public class LiveRecorder {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    private volatile boolean recording;
    private List<float[]> chunksL;
    private List<float[]> chunksR;
    private int totalSamples;

    public boolean isRecording() {
        return recording;
    }

    public void startRecording() {
        chunksL = new ArrayList<>();
        chunksR = new ArrayList<>();
        totalSamples = 0;
        recording = true;
    }

    /**
     * Called from the audio thread every block. Copies the post-volume,
     * post-clip stereo signal into chunk buffers. Fast no-op when not recording.
     */
    public void write(double[] bufL, double[] bufR, int len) {
        if (!recording) return;

        float[] chunkL = new float[len];
        float[] chunkR = new float[len];
        for (int i = 0; i < len; i++) {
            chunkL[i] = (float) bufL[i];
            chunkR[i] = (float) bufR[i];
        }
        chunksL.add(chunkL);
        chunksR.add(chunkR);
        totalSamples += len;
    }

    /**
     * Stop recording and write the captured audio to a WAV file.
     * Returns the file written, or null if nothing was recorded.
     * This method may block (disk I/O) — call from a background thread.
     */
    public File stopAndSave(File outputDir) {
        recording = false;

        if (totalSamples == 0) return null;

        // Flatten chunks into a contiguous stereo buffer
        float[] flatL = new float[totalSamples];
        float[] flatR = new float[totalSamples];
        int offset = 0;
        for (int c = 0; c < chunksL.size(); c++) {
            float[] cl = chunksL.get(c);
            float[] cr = chunksR.get(c);
            System.arraycopy(cl, 0, flatL, offset, cl.length);
            System.arraycopy(cr, 0, flatR, offset, cr.length);
            offset += cl.length;
        }

        // Free chunk lists
        chunksL = null;
        chunksR = null;
        totalSamples = 0;

        // Ensure output directory exists
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Generate timestamped filename
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String fileName = "recording-" + timestamp + ".wav";
        File outFile = new File(outputDir, fileName);

        // Write via WaveWriter
        WaveWriter ww = new WaveWriter("_rec_temp", flatL.length);
        ww.df[0] = flatL;
        ww.df[1] = flatR;
        ww.renderToFile(outFile.getPath());

        return outFile;
    }
}
