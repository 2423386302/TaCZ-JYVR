package com.tacz.guns.compat.jyvr;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Pack-space feed mouths, not the midpoint of a long tube/barrel or an animation pivot. */
public final class VRLoadingPorts {
    public record Port(String bone,float x,float y,float z) {}
    public static List<Port> ports(String id){
        return switch(id){
            case "tacz:m870" -> List.of(new Port("magazine",0,7.5f,-1.5f));
            case "tacz:spas_12" -> List.of(new Port("bullet_in_mag",0,8.0f,5.0f));
            case "tacz:m1014" -> List.of(new Port("magazine",0,6.5f,7.8f));
            case "tacz:db_long","tacz:db_short" -> List.of(new Port("barrels",-.45f,9.2f,3),new Port("barrels",.45f,9.2f,3));
            case "tacz:springfield1873" -> List.of(new Port("chamber",0,10.9f,2.2f));
            default -> List.of();
        };
    }
    public static Vector3f modelPoint(Port p){return new Vector3f(p.x()/16,(24-p.y())/16,p.z()/16);}
    public static VRRig.Shape shape(BedrockGunModel model,ItemStack gun){
        if(!(gun.getItem() instanceof IGun g))return null;
        var ports=ports(g.getGunId(gun).toString());if(ports.isEmpty())return null;
        var base=VRClient.modelToHand(model,gun);var rotation=base.getUnnormalizedRotation(new Quaternionf()).normalize();
        List<VRRig.Box> boxes=new ArrayList<>();Vector3f sum=new Vector3f();
        for(var p:ports){
            Vector3f center=base.transformPosition(modelPoint(p));
            var frame=new Matrix4f().translation(center).rotate(rotation);
            frame=VRWeaponRig.gateDelta(model,gun,model.getVRPartPath(p.bone())).mul(frame);
            sum.add(frame.getTranslation(new Vector3f()));
            boxes.add(new VRRig.Box(frame,new Vector3f(-.022f,-.022f,-.032f),new Vector3f(.022f,.022f,.032f)));
        }
        return new VRRig.Shape(List.copyOf(boxes),sum.div(boxes.size()));
    }
}
