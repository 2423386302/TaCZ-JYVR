package com.tacz.guns.compat.jyvr;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Short entrance guide. It produces a target; the collision solver still decides where it can move. */
public final class MagazineGuide {
    private boolean engaged;
    public void reset() { engaged=false; }
    public boolean engaged() { return engaged; }
    public record Target(Vector3f position, Quaternionf rotation) {}

    public Target target(boolean compatible, Matrix4f socket, float depth, Vector3f tipFromCenter,
                         Vector3f position, Quaternionf rotation) {
        Vector3f tip=new Quaternionf(rotation).transform(new Vector3f(tipFromCenter)).add(position);
        Vector3f error=new Matrix4f(socket).invert().transformPosition(tip);
        float lateral=(float)Math.hypot(error.x,error.z);
        float angle=2*(float)Math.acos(Math.min(1,Math.abs(rotation.w)));
        if(!compatible || !error.isFinite() || !rotation.isFinite() || lateral>.05f
            || error.y < -depth-.08f || error.y>.04f || angle>Math.toRadians(45)) engaged=false;
        else if(!engaged && lateral<=.022f && angle<=Math.toRadians(25)
            && error.y>=-depth-.04f && error.y<=-depth+.006f) engaged=true;
        if(!engaged)return new Target(new Vector3f(position),new Quaternionf(rotation));
        // Match the installed magazine's orientation, including twist, and retain the user's push/pull.
        Vector3f guidedTip=socket.transformPosition(new Vector3f(0,Math.min(0,error.y),0));
        return new Target(guidedTip.sub(tipFromCenter),new Quaternionf());
    }
}
