package com.tacz.guns.compat.jyvr;

/** Pure contact and rack rules, also used by the regression harness. */
public final class ContactMath {
    public static boolean inside(double x,double y,double z,double ax,double ay,double az,double bx,double by,double bz,double margin) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(margin) && margin>=0 &&
            x>=ax-margin && x<=bx+margin && y>=ay-margin && y<=by+margin && z>=az-margin && z<=bz+margin;
    }
    public static boolean rackComplete(double rearTravel, double lateralTravel) {
        return Double.isFinite(rearTravel) && Double.isFinite(lateralTravel) && rearTravel>=.04 && rearTravel<=.25 && lateralTravel>=0 && lateralTravel<=.04;
    }
}
