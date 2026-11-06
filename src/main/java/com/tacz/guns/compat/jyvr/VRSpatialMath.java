package com.tacz.guns.compat.jyvr;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Metres in controller space. The eye rotation is used only for drawing. */
public final class VRSpatialMath {
    private VRSpatialMath() {}

    public static Matrix4f viewAtHand(Matrix4f eyeToWorld, Matrix4f handRelativeToEye) {
        return new Matrix4f(eyeToWorld).transpose().mul(handRelativeToEye);
    }

    public static Matrix4f modelToHand(Matrix4f restGrip, Vector3f scale, Vector3f offset, Quaternionf calibration) {
        return new Matrix4f().translation(offset).rotate(calibration).rotateZ((float)Math.PI)
            .scale(scale).mul(new Matrix4f(restGrip).invert());
    }

    public static Vector3f relative(Vector3f offset, Quaternionf handRotation, float worldScale) {
        return new Quaternionf(handRotation).conjugate().transform(new Vector3f(offset)).div(worldScale);
    }
}
