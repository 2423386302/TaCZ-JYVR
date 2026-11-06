package com.tacz.guns.compat.jyvr;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import java.util.List;

/** A persistent magazine; ammo lives here while detached, never in a client-only counter. */
public final class VRMagazineItem extends Item {
    public VRMagazineItem() { super(new Item.Properties().stacksTo(1)); }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            private VRMagazineRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new VRMagazineRenderer();
                return renderer;
            }
        });
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        if (stack.hasTag()) {
            lines.add(Component.literal(stack.getTag().getString("GunId")));
            lines.add(Component.translatable("jyvr.magazine_rounds", stack.getTag().getInt("Rounds")));
        }
    }
}
