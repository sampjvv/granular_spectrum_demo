package org.delightofcomposition;

import java.util.List;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

/**
 * Manages background rendering of the granular spectrum synthesis.
 * Uses SwingWorker so the GUI stays responsive during the heavy FFT work.
 *
 * The actual render pipeline lives in {@link OfflineRenderer} — this class
 * is purely the Swing-side wrapper (SwingWorker lifecycle, progress bar
 * updates, callback dispatch).
 */
public class RenderController {

    public interface RenderCallback {
        void onComplete(float[][] stereoBuffer);
        void onError(String message);
        void onCancelled();
    }

    private SwingWorker<float[][], Integer> worker;
    private JProgressBar progressBar;
    private RenderCallback callback;

    public RenderController(JProgressBar progressBar, RenderCallback callback) {
        this.progressBar = progressBar;
        this.callback = callback;
    }

    public void render(SynthParameters params) {
        cancel(); // cancel any existing render

        SynthParameters snap = params.snapshot();
        Demo.cancelled = false;

        worker = new SwingWorker<float[][], Integer>() {
            @Override
            protected float[][] doInBackground() {
                return OfflineRenderer.render(snap, pct -> publish(pct));
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (!chunks.isEmpty()) {
                    int latest = chunks.get(chunks.size() - 1);
                    progressBar.setValue(latest);
                    progressBar.setString("Rendering " + latest + "%");
                }
            }

            @Override
            protected void done() {
                try {
                    float[][] result = get();
                    if (result == null) {
                        callback.onCancelled();
                    } else {
                        callback.onComplete(result);
                    }
                } catch (Exception e) {
                    if (isCancelled()) {
                        callback.onCancelled();
                    } else {
                        callback.onError(e.getMessage());
                    }
                }
            }
        };

        progressBar.setValue(0);
        worker.execute();
    }

    public void cancel() {
        Demo.cancelled = true;
        if (worker != null && !worker.isDone()) {
            worker.cancel(false);
        }
    }

    public boolean isRendering() {
        return worker != null && !worker.isDone();
    }
}
