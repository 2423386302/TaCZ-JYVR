package com.tacz.guns.compat.jyvr;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;
import java.util.Objects;

/** Rest geometry is keyed by model identity (including resource reload), gun, extension and calibration. */
final class VRRigCache {
    private BedrockGunModel model;
    private Object gunId;
    private int level,profileHash;
    private ItemStack template=ItemStack.EMPTY;
    VRRig.Shape magazine=new VRRig.Shape(List.of(),new Vector3f()),bolt=magazine;
    VRRig.MagazineMouth mouth;
    List<MagazineCollision.Box> carried=List.of();
    Matrix4f carry=new Matrix4f();
    private VRRig.Geometry open,closed;
    boolean ensure(ItemStack gun){
        var display=TimelessAPI.getGunDisplay(gun);
        if(display.isEmpty() || display.get().getGunModel()==null){clear();return false;}
        var next=display.get().getGunModel();var id=gun.getItem() instanceof IGun g?g.getGunId(gun):null;
        int extension=VRRig.magazineLevel(gun);var p=VRProfiles.get(gun);
        int hash=Objects.hash(p.scale,p.x,p.y,p.z,p.pitch,p.yaw,p.roll,VRClient.packScale(gun));
        if(model==next && Objects.equals(gunId,id) && level==extension && profileHash==hash)return true;
        model=next;gunId=id;level=extension;profileHash=hash;template=gun.copy();open=closed=null;
        magazine=VRRig.magazine(model,template);bolt=VRRig.bolt(model,template);
        mouth=VRRig.magazineMouth(model,template,magazine);carried=VRRig.carriedProxy(magazine,mouth);
        carry=new Matrix4f().translation(new Vector3f(magazine.center()).negate()).mul(VRClient.modelToHand(model,template)).mul(VRClient.restPathMatrix(model.getVRMagazinePath()));
        return true;
    }
    VRRig.Geometry geometry(boolean detached){
        if(model==null)return null;
        if(detached){if(open==null)open=VRRig.geometry(model,template,magazine,true);return open;}
        if(closed==null)closed=VRRig.geometry(model,template,magazine,false);return closed;
    }
    void clear(){model=null;template=ItemStack.EMPTY;open=closed=null;magazine=new VRRig.Shape(List.of(),new Vector3f());bolt=magazine;mouth=null;carried=List.of();}
}
