package com.tacz.guns.compat.jyvr;

/** Seat only after entering the socket and pushing inward; distances in metres. */
public final class InsertionGate {
    private boolean approached;
    private int stableTicks;
    public void reset(){approached=false;stableTicks=0;}
    public static boolean aligned(double axial,double lateral,double axisDot,double depth){
        return Double.isFinite(axial)&&Double.isFinite(lateral)&&Double.isFinite(axisDot)&&Double.isFinite(depth)
            && depth>=.0199 && depth<=.201 && lateral>=0 && lateral<=.012
            && axisDot>=.94 && axial>=-depth+Math.min(.025,depth*.4) && axial<=.008;
    }
    public boolean update(boolean valid,double axial,double lateral,double axisDot,double depth){
        if(!valid || !Double.isFinite(axial)||!Double.isFinite(lateral)||!Double.isFinite(axisDot)||!Double.isFinite(depth)||depth<.0199||depth>.201){reset();return false;}
        if(axial<-depth+.003 || lateral>.025){approached=true;stableTicks=0;return false;}
        if(!approached || !aligned(axial,lateral,axisDot,depth)){stableTicks=0;return false;}
        stableTicks=Math.min(3,stableTicks+1);return stableTicks>=3;
    }
}
