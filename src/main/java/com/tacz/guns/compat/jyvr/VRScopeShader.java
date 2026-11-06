package com.tacz.guns.compat.jyvr;

import java.io.IOException;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.tacz.guns.GunMod;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opaque scene color for magnified VR lenses. Reticles retain their own alpha. */
@Mod.EventBusSubscriber(modid=GunMod.MOD_ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class VRScopeShader {
    private static ShaderInstance worldColor;

    private VRScopeShader() {}

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
            new ResourceLocation(GunMod.MOD_ID, "vr_scope_world"), DefaultVertexFormat.POSITION_TEX_COLOR),
            shader -> worldColor = shader);
    }

    public static ShaderInstance worldColor() { return worldColor; }
}
