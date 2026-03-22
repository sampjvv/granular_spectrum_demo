package org.delightofcomposition.realtime;

public class ControlState {
    private volatile double mix = 0.0;      // 0.0 = all granular, 1.0 = all source
    private volatile double density = 0.5;  // 0.0 = sparse, 1.0 = dense

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
}
