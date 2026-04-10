package org.delightofcomposition.realtime;

public class ControlState {
    private volatile double mix = 0.0;      // 0.0 = all granular, 1.0 = all source
    private volatile double density = 0.5;  // 0.0 = sparse, 1.0 = dense
    private volatile double pan = 0.5;      // 0.0 = left, 0.5 = center, 1.0 = right

    public double getMix() {
        return mix;
    }

    public void setMix(double mix) {
        this.mix = Math.max(0.0, Math.min(1.0, mix));
    }

    public double getDensity() {
        return density;
    }

    public void setDensity(double density) {
        this.density = Math.max(0.0, Math.min(1.0, density));
    }

    public double getPan() {
        return pan;
    }

    public void setPan(double pan) {
        this.pan = Math.max(0.0, Math.min(1.0, pan));
    }

    // Master volume (0.0-1.0)
    private volatile double volume = 0.8;
    public double getVolume() { return volume; }
    public void setVolume(double vol) { this.volume = Math.max(0.0, Math.min(1.0, vol)); }

    // Reverse grain amount (0.0 = all forward, 1.0 = all reversed)
    private volatile double reverseAmount = 0.0;
    public double getReverseAmount() { return reverseAmount; }
    public void setReverseAmount(double amount) { this.reverseAmount = Math.max(0.0, Math.min(1.0, amount)); }

    // Source sample looping
    private volatile boolean sourceLoop = false;
    private volatile double sourceLoopStart = 0.1;
    private volatile double sourceLoopEnd = 0.9;

    public boolean isSourceLoop() { return sourceLoop; }
    public void setSourceLoop(boolean loop) { this.sourceLoop = loop; }
    public double getSourceLoopStart() { return sourceLoopStart; }
    public void setSourceLoopStart(double start) { this.sourceLoopStart = Math.max(0.0, Math.min(1.0, start)); }
    public double getSourceLoopEnd() { return sourceLoopEnd; }
    public void setSourceLoopEnd(double end) { this.sourceLoopEnd = Math.max(0.0, Math.min(1.0, end)); }
}
