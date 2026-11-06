package com.tacz.guns.mixin.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.compat.jyvr.VRClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameRenderer.class, priority = 900)
public abstract class JYVRProjectionMixin {
    @Inject(method = "getProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void jyvr$zoom(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        var player = Minecraft.getInstance().player;
        if (!VRClient.scopePass() || !VRClient.wantsScope() || player == null || !(player.getMainHandItem().getItem() instanceof IGun g)) return;
        float zoom = g.getAimingZoom(player.getMainHandItem());
        if (!Float.isFinite(zoom)) return;
        zoom = Math.max(1, Math.min(32, zoom));
        float focal = VRClient.scopeFocal(zoom);
        // Preserve near/far clipping from Vivecraft. Neither LEFT nor RIGHT eye FOV is touched.
        cir.setReturnValue(new Matrix4f(cir.getReturnValue()).m00(focal).m11(focal));
    }
}
