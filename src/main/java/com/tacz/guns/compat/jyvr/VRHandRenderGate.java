package com.tacz.guns.compat.jyvr;

/** The item display transform is not the camera perspective: Vivecraft can use either hand transform. */
public final class VRHandRenderGate {
    private VRHandRenderGate() {}
    public static boolean accepts(boolean vr,boolean localPlayer,boolean mainItem,boolean reentrant,String context,String pass){
        if(!vr || !localPlayer || !mainItem || reentrant)return false;
        if("VANILLA".equals(pass) || "GUI".equals(pass) || "MIRROR".equals(pass))return false;
        return "FIRST_PERSON_RIGHT_HAND".equals(context) || "FIRST_PERSON_LEFT_HAND".equals(context)
            || "THIRD_PERSON_RIGHT_HAND".equals(context) || "THIRD_PERSON_LEFT_HAND".equals(context);
    }
}
