package com.tacz.guns.compat.jyvr;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = GunMod.MOD_ID)
public final class VRServer {
    public static final String ID = "JYVRGun", OUT = "JYVRMagazineOut";
    public static final String OPEN="JYVROpenAction", SPENT="JYVRSpentCases";
    public static boolean open(ItemStack gun){return gun.hasTag()&&gun.getTag().getBoolean(OPEN);}
    public static void fired(net.minecraft.world.entity.LivingEntity entity,ItemStack gun){
        if(!(entity instanceof ServerPlayer p)||VivecraftAccess.serverPose(p)==null)return;
        var m=VRWeapon.get(gun);
        if(m.retainsCases())gun.getOrCreateTag().putInt(SPENT,Math.min(64,gun.getOrCreateTag().getInt(SPENT)+1));
        else effect(p,gun,1);
    }
    private static void effect(ServerPlayer p,ItemStack gun,int count){
        if(count>0)com.tacz.guns.network.NetworkHandler.sendToClientPlayer(new VRCasingMessage(gun.copy(),Math.min(64,count)),p);
    }
    private static void eject(ServerPlayer p,ItemStack gun){int count=gun.getOrCreateTag().getInt(SPENT);gun.getOrCreateTag().remove(SPENT);effect(p,gun,count);}
    private static final String LAST_ACTION = "JYVRActionTick";
    public static boolean detached(ItemStack stack) { return stack.hasTag() && stack.getTag().getBoolean(OUT); }
    public static boolean manual(net.minecraft.world.entity.LivingEntity entity, ItemStack stack) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player player) || !(stack.getItem() instanceof IGun g)) return false;
        return !g.useInventoryAmmo(stack) && !g.useDummyAmmo(stack) &&
            TimelessAPI.getCommonGunIndex(g.getGunId(stack)).map(i -> (i.getGunData().getReloadData().getType()==FeedType.MAGAZINE || i.getGunData().getReloadData().getType()==FeedType.MANUAL) && !i.getGunData().getReloadData().isInfinite()).orElse(false)
            && VivecraftAccess.serverPose(player)!=null;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if(p.tickCount%20==0) {
            VRHeldMagazine.migrate(p);
            if(!VRHeldMagazine.get(p).isEmpty() && (!manual(p,p.getMainHandItem()) || !p.getOffhandItem().isEmpty()))VRHeldMagazine.store(p);
            VRHeldMagazine.sync(p);
        }
        ItemStack s = p.getMainHandItem();
        if (s.getItem() instanceof IGun && !s.getOrCreateTag().hasUUID(ID)) {
            s.getOrCreateTag().putUUID(ID, UUID.randomUUID());
            p.getInventory().setChanged();
        }
    }
    @SubscribeEvent public static void reload(GunReloadEvent e) {
        if (manual(e.getEntity(),e.getGunItemStack())) {e.setCanceled(true);return;}
        if(e.getLogicalSide().isServer()&&e.getEntity() instanceof net.minecraft.world.entity.player.Player player&&VivecraftAccess.serverPose(player)==null){
            e.getGunItemStack().getOrCreateTag().remove(OPEN);e.getGunItemStack().getOrCreateTag().remove(SPENT);
        }
        if (!detached(e.getGunItemStack())) return;
        if (e.getEntity() instanceof net.minecraft.world.entity.player.Player player && VivecraftAccess.serverPose(player) == null) {
            if (e.getLogicalSide().isServer()) e.getGunItemStack().getOrCreateTag().remove(OUT);
        } else e.setCanceled(true);
    }
    private static void hint(ServerPlayer p, String key) { p.displayClientMessage(Component.translatable("jyvr." + key), true); }
    public static void handle(ServerPlayer p, VRActionMessage m) {
        int result=handleAction(p,m);
        if(m.action()==VRActionMessage.RACK){
            ItemStack gun=p.getMainHandItem();
            if(gun.getItem() instanceof IGun g && gun.hasTag() && gun.getTag().hasUUID(ID) && m.gun().equals(gun.getTag().getUUID(ID)))
                com.tacz.guns.network.NetworkHandler.sendToClientPlayer(new VRActionResultMessage(m.gun(),m.sequence(),result,g.getCurrentAmmoCount(gun),g.hasBulletInBarrel(gun)),p);
        }
    }
    private static void chamber(ItemStack gun,IGun g,com.tacz.guns.resource.pojo.data.gun.Bolt bolt){
        if(bolt==com.tacz.guns.resource.pojo.data.gun.Bolt.OPEN_BOLT)return;
        var result=MagazineMath.rack(g.getCurrentAmmoCount(gun),g.hasBulletInBarrel(gun));
        g.setCurrentAmmoCount(gun,result.magazine());g.setBulletInBarrel(gun,result.loaded());
    }
    private static int handleAction(ServerPlayer p, VRActionMessage m) {
        if (!p.isAlive() || p.isSpectator() || p.containerMenu != p.inventoryMenu || m.slot() > 8 ||
            m.slot() != p.getInventory().selected || m.action() < 0 || m.action() > 6) return 0;
        ItemStack gun = p.getMainHandItem();
        if (!(gun.getItem() instanceof IGun g) || !gun.hasTag() || !gun.getTag().hasUUID(ID) ||
            !m.gun().equals(gun.getTag().getUUID(ID))) return 0;
        var pose = VivecraftAccess.serverPose(p);
        if (pose == null) { hint(p, "server_vr_required"); return 0; }
        long now = p.level().getGameTime();
        var state = p.getPersistentData();
        if (state.contains(LAST_ACTION) && now >= state.getLong(LAST_ACTION) && now - state.getLong(LAST_ACTION) < 3) return 2;
        state.putLong(LAST_ACTION, now);
        var op = IGunOperator.fromLivingEntity(p);
        if (op.getSynReloadState().getStateType().isReloading() || op.getSynIsBolting() ||
            op.getSynDrawCoolDown() > 0 || (m.action()!=VRActionMessage.RACK && op.getSynShootCoolDown() > 0)) {
            if (m.action()==VRActionMessage.INSERT) hint(p,"wait_gun");
            return 2;
        }
        var idx = TimelessAPI.getCommonGunIndex(g.getGunId(gun));
        if (idx.isEmpty() || g.useInventoryAmmo(gun) || g.useDummyAmmo(gun) ||
            (idx.get().getGunData().getReloadData().getType() != FeedType.MAGAZINE && idx.get().getGunData().getReloadData().getType()!=FeedType.MANUAL) ||
            idx.get().getGunData().getReloadData().isInfinite()) { hint(p, "unsupported_feed"); return 0; }
        var mechanics=VRWeapon.get(gun);
        // Old versions allowed tube-fed weapons to retain an irrelevant detached-magazine tag.
        if(mechanics.loose())gun.getOrCreateTag().remove(OUT);
        int capacity = AttachmentDataUtils.getAmmoCountWithAttachment(gun, idx.get().getGunData());
        if (capacity < 1 || capacity > 10000) return 0;
        if(!p.getOffhandItem().isEmpty()) { hint(p,"offhand_occupied"); return 0; }
        ItemStack off = VRHeldMagazine.get(p);
        double hands = pose.off().position().distanceTo(pose.main().position());
        double beltY = pose.head().position().y - pose.off().position().y;
        double beltXZ = Math.hypot(pose.head().position().x - pose.off().position().x, pose.head().position().z - pose.off().position().z);
        boolean belt = beltY > 0.35 && beltY < 1.25 && beltXZ < 0.8;
        if (m.action() == VRActionMessage.DETACH) {
            if (mechanics.loose() || (mechanics.gated()&&!open(gun)) || hands > 1.35 || !off.isEmpty() || detached(gun)) return 0;
            int rounds = g.getCurrentAmmoCount(gun);
            if (rounds < 0 || rounds > capacity) return 0;
            ItemStack mag = new ItemStack(ModItems.VR_MAGAZINE.get());
            mag.getOrCreateTag().putString("GunId", g.getGunId(gun).toString());
            mag.getOrCreateTag().putString("AmmoId", idx.get().getGunData().getAmmoId().toString());
            mag.getOrCreateTag().putInt("Rounds", rounds);
            mag.getOrCreateTag().put("VisualGun", gun.save(new CompoundTag()));
            g.setCurrentAmmoCount(gun, 0);
            gun.getOrCreateTag().putBoolean(OUT, true);
            VRHeldMagazine.set(p,mag);
            hint(p, "detached");
        } else if (m.action() == VRActionMessage.STORE) {
            if (!belt || !off.is(ModItems.VR_MAGAZINE.get())) return 0;
            if(!VRHeldMagazine.store(p))hint(p,"invalid_magazine");
            else hint(p,"stored_ammo");
        } else if (m.action() == VRActionMessage.TAKE) {
            if (!belt || !off.isEmpty()) return 0;
            int limit=mechanics.loose()?1:capacity;
            int count = 0;
            for (ItemStack s : p.getInventory().items) {
                if (s.getItem() instanceof IAmmo a && a.isAmmoOfGun(gun, s)) {
                    int n = MagazineMath.take(s.getCount(), limit - count);
                    s.shrink(n); count += n;
                    if (count == limit) break;
                }
            }
            if (count == 0) { hint(p,"no_ammo"); return 0; }
            ItemStack mag = new ItemStack(ModItems.VR_MAGAZINE.get());
            mag.getOrCreateTag().putString("GunId", g.getGunId(gun).toString());
            mag.getOrCreateTag().putString("AmmoId", idx.get().getGunData().getAmmoId().toString());
            mag.getOrCreateTag().putInt("Rounds", count);
            mag.getOrCreateTag().putBoolean("LooseRound",mechanics.loose());
            mag.getOrCreateTag().put("VisualGun", gun.save(new CompoundTag()));
            VRHeldMagazine.set(p,mag);
            hint(p, "taken");
        } else if (m.action() == VRActionMessage.INSERT) {
            if (hands > 1.35) { hint(p,"hands_too_far"); return 0; }
            if (mechanics.loose() || (mechanics.gated()&&!open(gun))) return 0;
            if (!detached(gun) || g.getCurrentAmmoCount(gun)!=0) { hint(p,"well_not_empty"); return 0; }
            if (!compatible(off,gun,g,capacity)) { hint(p,"wrong_magazine"); return 0; }
            var transfer = MagazineMath.insert(0, off.getTag().getInt("Rounds"), capacity);
            g.setCurrentAmmoCount(gun, transfer.gun());
            gun.getOrCreateTag().remove(OUT);
            VRHeldMagazine.set(p,ItemStack.EMPTY);
            hint(p, "inserted");
        } else if(m.action()==VRActionMessage.TOGGLE){
            if(!mechanics.gated()||hands>1.35||!off.isEmpty())return 0;
            boolean next=!open(gun);gun.getOrCreateTag().putBoolean(OPEN,next);
            if(next)eject(p,gun);
            else if(mechanics.loose())chamber(gun,g,idx.get().getGunData().getBolt());
            hint(p,next?"action_open":"action_closed");
        } else if(m.action()==VRActionMessage.LOAD){
            int rounds=g.getCurrentAmmoCount(gun);
            if(!mechanics.loose()||hands>1.35||!WeaponMechanics.mayLoad(mechanics,open(gun),rounds+((mechanics.feed()==WeaponMechanics.Feed.CHAMBERS||mechanics.feed()==WeaponMechanics.Feed.SINGLE)&&g.hasBulletInBarrel(gun)?1:0),capacity)
                ||!compatible(off,gun,g,capacity)||!off.getTag().getBoolean("LooseRound")||off.getTag().getInt("Rounds")!=1)return 0;
            g.setCurrentAmmoCount(gun,rounds+1);
            if(mechanics.motion()==WeaponMechanics.Motion.NONE)chamber(gun,g,idx.get().getGunData().getBolt());
            VRHeldMagazine.set(p,ItemStack.EMPTY);hint(p,"round_loaded");
            p.serverLevel().playSound(null,p.getX(),p.getEyeY(),p.getZ(),idx.get().getGunData().getAmmoId().getPath().contains("12g")?com.tacz.guns.init.ModSounds.INSERT_SHELL.get():com.tacz.guns.init.ModSounds.INSERT_ROUND.get(),net.minecraft.sounds.SoundSource.PLAYERS,.55f,1f);
        } else if (m.action() == VRActionMessage.RACK) {
            if (hands > 1.35 || (!mechanics.loose()&&detached(gun)) || open(gun) || !off.isEmpty() || mechanics.motion()==WeaponMechanics.Motion.NONE) return 0;
            eject(p,gun);
            if (idx.get().getGunData().getBolt()!=com.tacz.guns.resource.pojo.data.gun.Bolt.OPEN_BOLT) {
                var chamber=MagazineMath.rack(g.getCurrentAmmoCount(gun),g.hasBulletInBarrel(gun));
                g.setCurrentAmmoCount(gun,chamber.magazine());
                g.setBulletInBarrel(gun,chamber.loaded());
            }
        }
        p.getInventory().setChanged();
        p.inventoryMenu.broadcastChanges();
        return 1;
    }
    private static boolean compatible(ItemStack s, ItemStack gun, IGun g, int capacity) {
        return s.is(ModItems.VR_MAGAZINE.get()) && s.hasTag() && s.getCount() == 1 &&
            g.getGunId(gun).toString().equals(s.getTag().getString("GunId")) &&
            s.getTag().getInt("Rounds") >= 0 && s.getTag().getInt("Rounds") <= capacity;
    }
}
