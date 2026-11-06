package com.tacz.guns.mixin.client;

import com.tacz.guns.compat.jyvr.VRClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.ArrayList;

@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.provider.VRRenderer", remap = false)
public abstract class JYVRPassMixin {
    @Inject(method = "getRenderPasses", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void jyvr$passes(boolean includeNonRendered, CallbackInfoReturnable<List<Object>> cir) {
        if (VRClient.wantsScope()) {
            List<Object> passes = new ArrayList<>(cir.getReturnValue());
            VRClient.addScopePass(passes);
            cir.setReturnValue(passes);
        }
    }
}
