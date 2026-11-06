package com.tacz.guns.compat.jyvr;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/** Optional Vivecraft 1.3.15 bridge: scope camera follows the selected optic, not the HMD. */
public final class VRScopeCamera {
    private static Constructor<?> constructor;
    private static Field originField,scaleField,rotationField;
    public static Object create(Object data)throws ReflectiveOperationException{
        Class<?> owner=Class.forName("org.vivecraft.client_vr.VRData");String device="org.vivecraft.client_vr.VRData$VRDevicePose";
        if(constructor==null){
            // Non-static inner class: implicit outer, followed by the explicit VRData argument.
            constructor=Class.forName(device).getConstructor(owner,owner,Matrix4fc.class,Vector3fc.class,Vector3fc.class);
            originField=owner.getField("origin");scaleField=owner.getField("worldScale");rotationField=owner.getField("rotation_radians");
        }
        var player=Minecraft.getInstance().player;if(player==null)return null;
        Object hand=VivecraftAccess.call(owner.getName(),"getController",data,new Class<?>[]{int.class},0);
        Vec3 position=(Vec3)VivecraftAccess.call(device,"getPosition",hand);
        Matrix4f handRotation=new Matrix4f((Matrix4fc)VivecraftAccess.call(device,"getMatrix",hand));
        Matrix4f optic=VRClient.aimingViewInHand(player.getMainHandItem());
        float scale=scaleField.getFloat(data),yaw=rotationField.getFloat(data);
        if(!Float.isFinite(scale)||scale<=0||!Float.isFinite(yaw))return hand;
        Vector3f offset=handRotation.transformDirection(optic.getTranslation(new Vector3f())).mul(scale);
        Vec3 origin=(Vec3)originField.get(data);
        Vector3f roomPosition=position.subtract(origin).add(new Vec3(offset)).toVector3f().rotateY(-yaw).div(scale);
        Quaternionf rotation=optic.getUnnormalizedRotation(new Quaternionf()).normalize();
        Matrix4f roomRotation=new Matrix4f().rotationY(-yaw).mul(handRotation).rotate(rotation).rotateZ((float)Math.PI);
        Vector3f direction=roomRotation.transformDirection(new Vector3f(0,0,-1)).normalize();
        return constructor.newInstance(data,data,roomRotation,roomPosition,direction);
    }
}
