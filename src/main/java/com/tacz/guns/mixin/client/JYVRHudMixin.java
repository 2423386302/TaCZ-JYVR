package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.compat.jyvr.VRClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="org.vivecraft.client_vr.render.helpers.VREffectsHelper",remap=false)
public abstract class JYVRHudMixin {
    @Inject(method="renderGuiLayer",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private static void jyvr$aimingHud(float partialTick,boolean depthAlways,PoseStack pose,CallbackInfo ci){
        if(VRClient.hideAimingHud())ci.cancel();
    }
}
