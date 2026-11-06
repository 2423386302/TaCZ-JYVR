package com.tacz.guns.mixin.client;

import com.tacz.guns.compat.jyvr.VRClient;
import com.tacz.guns.compat.jyvr.VivecraftAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.VRData", remap = false)
public abstract class JYVRDataMixin {
    @Inject(method = "getEye", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void jyvr$scopeCamera(@Coerce Object pass, CallbackInfoReturnable<Object> cir) {
        if ("SCOPER".equals(String.valueOf(pass)) && VRClient.wantsScope()) {
            try {
                Object pose=com.tacz.guns.compat.jyvr.VRScopeCamera.create(this);
                if(pose!=null)cir.setReturnValue(pose);
            } catch (ReflectiveOperationException e) { VivecraftAccess.warn(e); }
        }
    }
}
