package com.tacz.guns.compat.jyvr;

/** Tick-only mechanical stroke. Completion is latched; render frames cannot chamber ammunition. */
public final class RackGesture {
    private boolean rear, completed;
    private float position;
    public void reset(){rear=completed=false;position=0;}
    public float position(){return position;}
    public boolean returning(){return rear;}
    public boolean update(float distance,float lateral,float travel,boolean returnStroke,boolean released){
        if(completed||!Float.isFinite(distance)||!Float.isFinite(lateral)||travel<=0)return false;
        position=Math.max(0,Math.min(travel,distance));
        if(lateral>.09f)return false;
        if(distance>=travel*.9f)rear=true;
        // Completing the forward stroke works while still gripping. Spring-return actions complete on release.
        boolean closed=returnStroke?distance<=travel*.30f:released;
        if(rear&&closed){completed=true;position=0;return true;}
        return false;
    }
}
