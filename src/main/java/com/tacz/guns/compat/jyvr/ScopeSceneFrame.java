package com.tacz.guns.compat.jyvr;

/** Published only after the telescope world view, including its entity rendering, returns.
 * Render-thread only. The eyes may reuse the completed color texture for the rest of this frame. */
public final class ScopeSceneFrame {
    private static int completed = -1;
    private ScopeSceneFrame() {}
    public static void begin() { completed = -1; }
    public static void complete(int texture) { completed = texture > 0 ? texture : -1; }
    public static int texture() { return completed; }
}
