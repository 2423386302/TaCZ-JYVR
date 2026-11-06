package com.tacz.guns.compat.jyvr;

/** Pure interaction state, independent of Minecraft and frame rate. */
public final class InteractionGate {
    public enum Stage { IDLE, PULLING, DETACHED }
    private Stage stage = Stage.IDLE;
    private int ticks;
    public Stage stage() { return stage; }
    public void reset() { stage = Stage.IDLE; ticks = 0; }
    public boolean update(boolean valid, boolean pressed, boolean risingEdge, boolean nearWell, double pullDistance) {
        if (!valid) { reset(); return false; }
        if (stage == Stage.IDLE && risingEdge && nearWell) { stage = Stage.PULLING; ticks = 0; }
        if (stage == Stage.PULLING) {
            if (!pressed || ++ticks > 60) { reset(); return false; }
            if (Double.isFinite(pullDistance) && pullDistance >= 0.09 && pullDistance <= 0.8) {
                stage = Stage.DETACHED; return true;
            }
        } else if (stage == Stage.DETACHED && !pressed) reset();
        return false;
    }
    public static boolean aiming(boolean previous, double distance, double alignment, double enter) {
        return Double.isFinite(distance) && Double.isFinite(alignment) && distance >= 0 &&
            distance < enter + (previous ? 0.07 : 0) && alignment > (previous ? 0.82 : 0.90);
    }
}
