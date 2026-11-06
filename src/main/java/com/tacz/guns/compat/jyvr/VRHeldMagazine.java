package com.tacz.guns.compat.jyvr;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.network.NetworkHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.UUID;

/** Server-owned temporary hand state. No stack is inserted into the player's inventory. */
@Mod.EventBusSubscriber(modid=GunMod.MOD_ID)
public final class VRHeldMagazine {
    private static final String KEY="JYVRHeldMagazine";
    public static ItemStack get(ServerPlayer p) { return ItemStack.of(p.getPersistentData().getCompound(KEY)); }
    public static void set(ServerPlayer p,ItemStack mag) {
        if(mag.isEmpty()) p.getPersistentData().remove(KEY);
        else {
            if(!mag.getOrCreateTag().hasUUID("HeldId"))mag.getOrCreateTag().putUUID("HeldId",UUID.randomUUID());
            p.getPersistentData().put(KEY,mag.save(new CompoundTag()));
        }
        sync(p);
    }
    public static void sync(ServerPlayer p) {
        NetworkHandler.sendToClientPlayer(new VRMagazineStateMessage(p.getUUID(),get(p)),p);
    }
    private static ResourceLocation ammoId(ItemStack mag) {
        if(!mag.is(ModItems.VR_MAGAZINE.get()) || !mag.hasTag() || mag.getCount()!=1) return null;
        int n=mag.getTag().getInt("Rounds");
        if(n<0 || n>10000)return null;
        ResourceLocation id=ResourceLocation.tryParse(mag.getTag().getString("AmmoId"));
        return id!=null && TimelessAPI.getCommonAmmoIndex(id).isPresent()?id:null;
    }
    private static void refund(ServerPlayer p,ItemStack mag,ResourceLocation id) {
        int left=mag.getTag().getInt("Rounds");
        while(left>0) {
            ItemStack ammo=AmmoItemBuilder.create().setId(id).build();
            int n=Math.min(left,ammo.getMaxStackSize());ammo.setCount(n);left-=n;
            p.getInventory().add(ammo);
            if(!ammo.isEmpty())p.drop(ammo,false); // Full inventory never deletes paid ammunition.
        }
    }
    public static boolean store(ServerPlayer p) {
        ItemStack mag=get(p);if(mag.isEmpty())return true;
        ResourceLocation id=ammoId(mag);if(id==null)return false;
        set(p,ItemStack.EMPTY);refund(p,mag,id);
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();return true;
    }
    /** Convert old inventory magazines once, including an old occupied offhand. */
    public static void migrate(ServerPlayer p) {
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            ItemStack old=p.getInventory().getItem(i);
            if(!old.is(ModItems.VR_MAGAZINE.get()))continue;
            ResourceLocation id=ammoId(old);if(id==null)continue;
            p.getInventory().setItem(i,ItemStack.EMPTY);refund(p,old,id);
            p.getInventory().setChanged();
        }
    }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone e) {
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        if(!e.isWasDeath() || p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            CompoundTag old=e.getOriginal().getPersistentData();
            if(old.contains(KEY))p.getPersistentData().put(KEY,old.getCompound(KEY).copy());
        }
    }
    @SubscribeEvent public static void drops(LivingDropsEvent e) {
        if(!(e.getEntity() instanceof ServerPlayer p) || p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))return;
        ItemStack mag=get(p);ResourceLocation id=ammoId(mag);if(id==null)return;
        int left=mag.getTag().getInt("Rounds");
        while(left>0) {
            ItemStack ammo=AmmoItemBuilder.create().setId(id).build();int n=Math.min(left,ammo.getMaxStackSize());ammo.setCount(n);left-=n;
            e.getDrops().add(new ItemEntity(p.level(),p.getX(),p.getY(),p.getZ(),ammo));
        }
        set(p,ItemStack.EMPTY);
    }
}
