package com.tacz.guns.compat.jyvr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class VRMagazineRenderer extends BlockEntityWithoutLevelRenderer {
    public VRMagazineRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }
    @Override public void renderByItem(ItemStack s, ItemDisplayContext context, PoseStack supplied, MultiBufferSource buffers, int light, int overlay) {
        if (!s.hasTag() || !s.getTag().contains("VisualGun")) return;
        ItemStack gun = ItemStack.of(s.getTag().getCompound("VisualGun"));
        gun.getOrCreateTag().remove(VRServer.OUT);
        TimelessAPI.getGunDisplay(gun).ifPresent(display -> {
            PoseStack stack = supplied;
            boolean vr = VRClient.active() && context.firstPerson();
            if (vr) {
                try { stack=VRClient.handRenderSpace(InteractionHand.OFF_HAND); }
                catch(ReflectiveOperationException e){VivecraftAccess.warn(e);return;}
            }
            stack.pushPose();
            try {
                if (!vr) stack.translate(.5,.5,.5);
                stack.mulPoseMatrix(VRRig.carry(display.getGunModel(),gun));
                display.getGunModel().renderVRMagazine(stack,gun,context,buffers,RenderType.entityCutout(display.getModelTexture()),light,overlay);
            } finally { stack.popPose(); }
        });
    }
}
