package com.tacz.guns.compat.jyvr;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.world.item.ItemStack;
public final class VRWeapon {
    public static com.tacz.guns.resource.pojo.data.gun.Bolt effectiveBolt(ItemStack gun,com.tacz.guns.resource.pojo.data.gun.Bolt original,boolean vr){
        // The default M107 data labels this self-loading rifle as manual_action for its desktop animation.
        if(vr && gun.getItem() instanceof IGun g && "tacz:m107".equals(g.getGunId(gun).toString()))return com.tacz.guns.resource.pojo.data.gun.Bolt.CLOSED_BOLT;
        return original;
    }
    public static WeaponMechanics get(ItemStack gun){
        if(gun.getItem() instanceof IGun g){var data=TimelessAPI.getCommonGunIndex(g.getGunId(gun));
            if(data.isPresent()){var d=data.get().getGunData();return WeaponMechanics.of(g.getGunId(gun).toString(),d.getBolt().name(),d.getReloadData().getType().name());}}
        return WeaponMechanics.of("","CLOSED_BOLT","MAGAZINE");
    }
}
