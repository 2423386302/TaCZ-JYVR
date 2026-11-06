package com.tacz.guns.compat.jyvr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.input.ReloadKey;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.network.NetworkHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public final class VRClient {
    public static final KeyMapping GRAB = new KeyMapping("key.tacz.vr_grab", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN, "key.category.tacz");
    public static final KeyMapping DEBUG_BOXES=new KeyMapping("key.tacz.vr_debug_boxes",org.lwjgl.glfw.GLFW.GLFW_KEY_F8,"key.category.tacz");
    private static ItemStack heldMagazine=ItemStack.EMPTY,heldVisualGun=ItemStack.EMPTY;
    private static UUID magazineOwner,heldId;
    private static MagazineCollision.Pose heldPose,previousHeldPose,extractionPose;
    private static final VRRigCache mainRig=new VRRigCache(),heldRig=new VRRigCache();
    private static net.minecraft.client.renderer.MultiBufferSource.BufferSource handBuffers;
    private static Vector3f carryOffset=new Vector3f(),pendingDetachOffset;
    private static org.joml.Quaternionf carryRotation=new org.joml.Quaternionf(),pendingDetachRotation;
    private static VRRig.Geometry geometry;
    private static java.util.List<MagazineCollision.Box> heldBoxes=java.util.List.of();
    public static void receiveMagazine(UUID player,ItemStack mag){
        UUID next=mag.hasTag()&&mag.getTag().hasUUID("HeldId")?mag.getTag().getUUID("HeldId"):null;
        if(!java.util.Objects.equals(next,heldId)||!java.util.Objects.equals(player,magazineOwner)){
            heldPose=previousHeldPose=null;insertion.reset();magazineGuide.reset();
            carryOffset=next!=null && pendingDetachOffset!=null?pendingDetachOffset:new Vector3f();pendingDetachOffset=null;
            carryRotation=next!=null && pendingDetachRotation!=null?pendingDetachRotation:new org.joml.Quaternionf();pendingDetachRotation=null;
        }
        heldId=next;magazineOwner=player;heldMagazine=mag.copy();
        heldVisualGun=mag.hasTag()?ItemStack.of(mag.getTag().getCompound("VisualGun")):ItemStack.EMPTY;
        if(!heldVisualGun.isEmpty()){
            heldVisualGun.getOrCreateTag().remove(VRServer.OUT);
            if(heldVisualGun.getItem() instanceof IGun g)g.setCurrentAmmoCount(heldVisualGun,mag.getTag().getInt("Rounds"));
        }
    }
    private static ItemStack heldMagazine(){
        var p=Minecraft.getInstance().player;
        return p!=null && p.getUUID().equals(magazineOwner)?heldMagazine:ItemStack.EMPTY;
    }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){
        ScopeSceneFrame.begin();heldMagazine=heldVisualGun=ItemStack.EMPTY;magazineOwner=heldId=null;heldPose=previousHeldPose=null;geometry=null;mainRig.clear();heldRig.clear();lastRenderTick=Integer.MIN_VALUE;renderHookLogged=false;renderRoute="none";reset();
    }
    private static final InteractionGate pull = new InteractionGate();
    private static UUID gunIdentity;
    private static net.minecraft.client.player.LocalPlayer owner;
    private static boolean held, aiming, racking;
    private static Vec3 pullStart = Vec3.ZERO;
    private static int cooldown;
    private static final InsertionGate insertion = new InsertionGate();
    private static final MagazineGuide magazineGuide=new MagazineGuide();
    private static boolean renderingGun;
    private static Vec3 magazinePull=Vec3.ZERO,lastMagazinePull=Vec3.ZERO;
    private static float boltPull,lastBoltPull;
    private static final RackGesture rackGesture=new RackGesture();
    private static boolean rackReturning;
    private static VRWeaponRig.Stroke rackStroke=new VRWeaponRig.Stroke(new Vec3(0,0,1),.055f);
    private static int rackSequence,pendingRack,pendingRackTicks;
    public static boolean actionBusy(){return manual()&&(racking||pendingRack!=0||boltPull>.001f);}
    public static void rackResult(VRActionResultMessage result){
        var p=Minecraft.getInstance().player;
        if(p==null||pendingRack==0||result.sequence()!=pendingRack||!result.gun().equals(gunIdentity))return;
        ItemStack current=p.getMainHandItem();
        if(!current.hasTag()||!current.getTag().hasUUID(VRServer.ID)||!result.gun().equals(current.getTag().getUUID(VRServer.ID)))return;
        if(result.result()==2)return; // transient draw/reload cooldown: bounded retry, same operation
        pendingRack=0;pendingRackTicks=0;
        if(result.result()==1&&p.getMainHandItem().getItem() instanceof IGun g){
            g.setCurrentAmmoCount(p.getMainHandItem(),result.ammo());g.setBulletInBarrel(p.getMainHandItem(),result.loaded());
            boltPull=lastBoltPull=0;rackGesture.reset();rackReturning=false;
            p.playSound(net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE,.25f,1.5f);
        }else { rackFailed(); p.displayClientMessage(net.minecraft.network.chat.Component.literal("上膛未被确认，请靠近枪栓重新操作"),true); }
    }
    private static void rackFailed(){
        rackGesture.reset();racking=false;
        var p=Minecraft.getInstance().player;
        if(p!=null&&VRWeapon.get(p.getMainHandItem()).returnStroke()){
            boltPull=lastBoltPull=rackStroke.travel();
            rackGesture.update(boltPull,0,rackStroke.travel(),true,false);rackReturning=true;
        }else{boltPull=lastBoltPull=0;rackReturning=false;}
    }
    private static boolean rackReady,lastDetached;
    private static int rackTicks,settleTicks,detachPending;
    private static float openAmount,lastOpenAmount;
    private static boolean loadLatched;
    public static VRRig.Shape loadingContact=new VRRig.Shape(java.util.List.of(),new Vector3f());
    public static VRRig.Shape gateContact=loadingContact;
    public static boolean manual() {
        var p=Minecraft.getInstance().player;
        if (!active() || p==null || !(p.getMainHandItem().getItem() instanceof IGun g)) return false;
        ItemStack s=p.getMainHandItem();
        return !g.useInventoryAmmo(s) && !g.useDummyAmmo(s) &&
            TimelessAPI.getClientGunIndex(g.getGunId(s)).map(i -> i.getGunData()!=null && (i.getGunData().getReloadData().getType()==com.tacz.guns.resource.pojo.data.gun.FeedType.MAGAZINE || i.getGunData().getReloadData().getType()==com.tacz.guns.resource.pojo.data.gun.FeedType.MANUAL) && !i.getGunData().getReloadData().isInfinite()).orElse(false);
    }
    private static boolean available = true;
    private static boolean warnedConflict;
    public static boolean active() {
        if (!available || VRConfig.ENABLED == null || !VRConfig.ENABLED.get() || !ModList.get().isLoaded("vivecraft")) return false;
        if (ModList.get().isLoaded("taczvr")) {
            if (!warnedConflict) { warnedConflict = true; GunMod.LOGGER.error("JYVR disabled: remove the other taczvr addon before using this fork"); }
            return false;
        }
        try {
            String api = "org.vivecraft.api.client.VRClientAPI";
            return (Boolean) VivecraftAccess.call(api, "isVRActive", VivecraftAccess.call(api, "instance", null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            available = false; VivecraftAccess.warn(e); return false;
        }
    }
    public static VivecraftAccess.Pose pose(boolean render) {
        try {
            String api = "org.vivecraft.api.client.VRClientAPI";
            Object p = VivecraftAccess.call(api, render ? "getWorldRenderPose" : "getPreTickWorldPose", VivecraftAccess.call(api,"instance",null));
            return VivecraftAccess.readPose(p);
        } catch (ReflectiveOperationException | RuntimeException e) { VivecraftAccess.warn(e); return null; }
    }
    public static String pass() {
        if (!active()) return "VANILLA";
        try {
            String api = "org.vivecraft.api.client.VRRenderingAPI";
            return String.valueOf(VivecraftAccess.call(api, "getCurrentRenderPass", VivecraftAccess.call(api,"instance",null)));
        } catch (ReflectiveOperationException e) { return "VANILLA"; }
    }
    private static int lastRenderTick = Integer.MIN_VALUE;
    private static boolean renderHookLogged;
    private static String renderRoute = "none";

    /** Called only by ItemInHandRenderer, never world entity / inventory rendering. */
    public static boolean renderHandItem(net.minecraft.world.entity.LivingEntity entity,ItemStack stack,
        ItemDisplayContext context,PoseStack poseStack,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();var player=mc.player;
        if(scopeScene()&&entity==player)return true;
        if(player==null || !(stack.getItem() instanceof IGun))return false;
        ItemStack main=player.getMainHandItem();
        boolean mainItem=stack==main || (stack.hasTag() && main.hasTag() && stack.getTag().hasUUID(VRServer.ID)
            && main.getTag().hasUUID(VRServer.ID) && stack.getTag().getUUID(VRServer.ID).equals(main.getTag().getUUID(VRServer.ID)));
        boolean vr=active();
        if(!VRHandRenderGate.accepts(vr,entity==player,mainItem,renderingGun,context.name(),vr?pass():"VANILLA"))return false;
        if(scopePass())return true;
        // If tracking/resources are unavailable, let the normal renderer retain a visible gun.
        if(pose(true)==null || TimelessAPI.getGunDisplay(stack).map(d->d.getGunModel()==null).orElse(true))return false;
        var renderer=net.minecraftforge.client.extensions.common.IClientItemExtensions.of(stack.getItem()).getCustomRenderer();
        if(!(renderer instanceof com.tacz.guns.client.renderer.item.GunItemRendererWrapper gunRenderer))return false;
        if(gunRenderer.needReInit(stack))gunRenderer.tryInit(stack,player,mc.getFrameTime());
        renderRoute=context.name();
        gunRenderer.renderFirstPerson(player,stack,context,poseStack,buffers,light,mc.getFrameTime());
        if(!renderHookLogged){renderHookLogged=true;GunMod.LOGGER.info("JYVR 0.1.0: hand-item render hook active; display context={}",context);}
        return true;
    }
    private static net.minecraft.network.chat.Component debugStatus(){
        var p=Minecraft.getInstance().player;
        boolean recent=p!=null && (long)p.tickCount-lastRenderTick<40;
        return net.minecraft.network.chat.Component.translatable("jyvr.debug_status",
            net.minecraft.network.chat.Component.translatable(recent?"jyvr.debug_render_ok":"jyvr.debug_render_missing"),
            net.minecraft.network.chat.Component.translatable(!heldMagazine().isEmpty()?"jyvr.debug_data_ready":"jyvr.debug_data_empty"),
            net.minecraft.network.chat.Component.translatable(heldRig.magazine.present()?"jyvr.debug_model_ready":"jyvr.debug_model_missing"));
    }
    public static void setDebugBoxes(boolean value) {
        VRConfig.DEBUG_BOXES.set(value);
        VRConfig.DEBUG_BOXES.save();
        var p=Minecraft.getInstance().player;
        if(p!=null){
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable(value?"jyvr.debug_on":"jyvr.debug_off"),true);
            if(value){
                p.displayClientMessage(debugStatus(),false);
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("交互箱：橙=枪栓/护木，绿=装填口，紫=开合部位，黄=弹匣；接触变白"),false);
                GunMod.LOGGER.info("JYVR 0.1.0 debug: route={}, heldMagazine={}, heldPose={}, magazineMesh={}, geometry={}",renderRoute,!heldMagazine().isEmpty(),heldPose!=null,heldRig.magazine.present(),geometry!=null);
            }
        }
    }
    @SubscribeEvent public static void registerCommands(net.minecraftforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("jyvr")
            .executes(context->showDebugStatus())
            .then(net.minecraft.commands.Commands.literal("status").executes(context->showDebugStatus()))
            .then(net.minecraft.commands.Commands.literal("scope").then(net.minecraft.commands.Commands.literal("clean").executes(c->{VRConfig.SCOPES.set(true);VRConfig.SCOPES.save();Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal("TaCZ-JYVR 0.1.0：倍镜使用完整世界画面（含实体）"),false);return 1;})).then(net.minecraft.commands.Commands.literal("capture").executes(c->VRScopeDebug.request())))
            .then(net.minecraft.commands.Commands.literal("casings")
                .then(net.minecraft.commands.Commands.literal("lifetime").then(net.minecraft.commands.Commands.literal("off").executes(c->{VRConfig.TIMED.set(false);VRConfig.TIMED.save();return 1;})).then(net.minecraft.commands.Commands.argument("seconds",com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(.1,3600)).executes(c->{VRConfig.SECONDS.set(com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c,"seconds"));VRConfig.TIMED.set(true);VRConfig.SECONDS.save();VRConfig.TIMED.save();return 1;})))
                .then(net.minecraft.commands.Commands.literal("on").executes(c->setCasings(true)))
                .then(net.minecraft.commands.Commands.literal("off").executes(c->setCasings(false))))
            .then(net.minecraft.commands.Commands.literal("debug")
                .then(net.minecraft.commands.Commands.literal("on").executes(context->{setDebugBoxes(true);return 1;}))
                .then(net.minecraft.commands.Commands.literal("off").executes(context->{setDebugBoxes(false);return 1;}))));
    }
    private static int setCasings(boolean enabled){VRConfig.CASINGS.set(enabled);VRConfig.CASINGS.save();if(!enabled)VRCasings.clear();
        var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(net.minecraft.network.chat.Component.translatable(enabled?"jyvr.casings_on":"jyvr.casings_off"),true);return 1;}
    private static int showDebugStatus() {
        var p=Minecraft.getInstance().player;
        if(p!=null){p.displayClientMessage(debugStatus(),false);
            if(p.getMainHandItem().getItem() instanceof IGun){var m=VRWeapon.get(p.getMainHandItem());
                String key=m.gated()?"reload_gate":m.loose()?"reload_loose":m.returnStroke()?"reload_cycle":m.motion()==WeaponMechanics.Motion.SLIDE?"reload_slide":"reload_magazine";
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("jyvr."+key),false);}}
        return 1;
    }
    public static boolean renderingGun() { return renderingGun; }
    public static boolean scopePass() { return "SCOPER".equals(pass()); }
    public static boolean capturingScope;
    public static boolean scopeScene(){return capturingScope||(scopePass()&&wantsScope());}
    public static boolean scopeLocalGun(ItemStack stack){
        if(!scopeScene())return false;
        var player=Minecraft.getInstance().player;if(player==null)return false;
        var local=player.getMainHandItem();String key=VRServer.ID;
        return stack==local||(stack.hasTag()&&local.hasTag()&&stack.getTag().hasUUID(key)&&local.getTag().hasUUID(key)&&stack.getTag().getUUID(key).equals(local.getTag().getUUID(key)));
    }
    @SubscribeEvent public static void scopePlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre event){
        if(event.getEntity()==Minecraft.getInstance().player&&scopeScene())event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void scopeHands(net.minecraftforge.client.event.RenderHandEvent event){
        if(scopeScene())event.setCanceled(true);
    }
    public static boolean wantsScope() {
        var p = Minecraft.getInstance().player;
        if (!active() || !VRConfig.SCOPES.get() || !aiming || p == null || !(p.getMainHandItem().getItem() instanceof IGun g)) return false;
        ItemStack s = p.getMainHandItem();
        var id = g.getAttachmentId(s, AttachmentType.SCOPE);
        if (com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(id)) id = g.getBuiltInAttachmentId(s, AttachmentType.SCOPE);
        return TimelessAPI.getClientAttachmentIndex(id).map(index -> index.isScope() && index.getAttachmentModel()!=null && index.getAttachmentModel().isMagnifiedView(scopeViewIndex(s,index))).orElse(false);
    }
    public static void addScopePass(List<Object> passes) {
        if (!wantsScope()) return;
        try {
            Object pass = Class.forName("org.vivecraft.api.client.data.RenderPass").getField("SCOPER").get(null);
            passes.remove(pass); passes.add(0, pass); // current-frame texture, never the last eye's framebuffer
        } catch (ReflectiveOperationException e) { VivecraftAccess.warn(e); }
    }
    public static int scopeTexture() {
        return wantsScope()&&!capturingScope ? ScopeSceneFrame.texture() : -1;
    }
    public static Matrix4f pathMatrix(List<BedrockPart> path) {
        PoseStack p = new PoseStack();
        if (path != null) for (BedrockPart part : path) part.translateAndRotateAndScale(p);
        return new Matrix4f(p.last().pose());
    }
    public static Matrix4f restPathMatrix(List<BedrockPart> path) {
        Matrix4f result=new Matrix4f();
        if(path!=null)for(BedrockPart part:path)result.mul(VRRig.restPartMatrix(part));
        return result;
    }
    public static Vector3f packScale(ItemStack gun) {
        Vector3f scale=TimelessAPI.getGunDisplay(gun).map(d->d.getTransform().getScale().getThirdPerson()).orElse(null);
        if(scale==null || !scale.isFinite() || scale.x<=0 || scale.y<=0 || scale.z<=0)return new Vector3f(.6f);
        return new Vector3f(scale);
    }
    public static Matrix4f modelToHand(BedrockGunModel model, ItemStack gun) {
        var p=VRProfiles.get(gun);
        Matrix4f grip=restPathMatrix(model.getThirdPersonHandOriginPath());
        if(Math.abs(grip.determinant())<1e-8)grip.identity();
        var rotation=new org.joml.Quaternionf().rotateY((float)Math.toRadians(p.yaw))
            .rotateX((float)Math.toRadians(p.pitch)).rotateZ((float)Math.toRadians(p.roll));
        return VRSpatialMath.modelToHand(grip,packScale(gun).mul((float)p.scale),
            new Vector3f((float)p.x,(float)p.y,(float)p.z),rotation);
    }
    /** Same view rotation Vivecraft applies before positioning its own arms. */
    public static PoseStack handRenderSpace(InteractionHand hand) throws ReflectiveOperationException {
        String api="org.vivecraft.api.client.VRRenderingAPI";
        Object instance=VivecraftAccess.call(api,"instance",null);
        Object pass=VivecraftAccess.call(api,"getCurrentRenderPass",instance);
        Matrix4f camera=(Matrix4f)VivecraftAccess.call(api,"getRenderPassMatrix",instance,
            new Class<?>[]{Class.forName("org.vivecraft.api.client.data.RenderPass")},pass);
        PoseStack handOnly=new PoseStack();
        VivecraftAccess.call(api,"setupRenderingAtHand",instance,
            new Class<?>[]{InteractionHand.class,PoseStack.class},hand,handOnly);
        PoseStack result=new PoseStack();
        result.mulPoseMatrix(VRSpatialMath.viewAtHand(camera,handOnly.last().pose()));
        result.last().normal().set(new org.joml.Matrix3f(result.last().pose()).invert().transpose());
        return result;
    }
    public static Vec3 relative(VivecraftAccess.Pose pose) {
        return new Vec3(VRSpatialMath.relative(pose.off().position().subtract(pose.main().position()).toVector3f(),
            pose.main().rotation(),worldScale()));
    }
    public static VRRig.Shape magazineShape(ItemStack s) {
        mainRig.ensure(s);return mainRig.magazine;
    }
    public static VRRig.Shape boltShape(ItemStack s) {
        var stroke=VRWeaponRig.stroke(s);
        return VRWeaponRig.actionContact(s,boltPull/stroke.travel(),rackReturning);
    }
    public static int scopeViewIndex(ItemStack gun,com.tacz.guns.client.resource.index.ClientAttachmentIndex index){
        if(!(gun.getItem() instanceof IGun g))return 0;int[] views=index.getViews();if(views==null||views.length==0)return 0;
        int zoom=com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor.getZoomNumberFromTag(g.getAttachmentTag(gun,AttachmentType.SCOPE));
        return Math.max(0,views[Math.floorMod(zoom,views.length)]-1);
    }
    /** Shared physical optic transform for aim detection and the independent scope camera. */
    public static Matrix4f aimingViewInHand(ItemStack gun){
        var display=TimelessAPI.getGunDisplay(gun);if(display.isEmpty()||display.get().getGunModel()==null)return new Matrix4f();
        var model=display.get().getGunModel();Matrix4f target=restPathMatrix(model.getIronSightPath());
        if(gun.getItem() instanceof IGun g&&model.getScopePosPath()!=null){
            var id=g.getAttachmentId(gun,AttachmentType.SCOPE);if(com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(id))id=g.getBuiltInAttachmentId(gun,AttachmentType.SCOPE);
            var index=TimelessAPI.getClientAttachmentIndex(id);
            if(index.isPresent()&&index.get().getAttachmentModel()!=null){
                var view=index.get().getAttachmentModel().getScopeViewPath(scopeViewIndex(gun,index.get()));
                if(view!=null)target=OpticMath.attachmentView(restPathMatrix(model.getScopePosPath()),restPathMatrix(view));
            }
        }
        return modelToHand(model,gun).mul(target);
    }
    private static Vec3 eyeTarget(ItemStack gun,VivecraftAccess.Pose pose){
        Vector3f local=aimingViewInHand(gun).getTranslation(new Vector3f());pose.main().rotation().transform(local);
        return pose.main().position().add(new Vec3(local).scale(worldScale()));
    }
    public static boolean hideAimingHud(){
        var mc=Minecraft.getInstance();return OpticMath.hideHud(active(),mc.player!=null&&mc.player.getMainHandItem().getItem() instanceof IGun,aiming,mc.screen!=null,VRConfig.HIDE_AIMING_HUD.get());
    }
    public static float scopeFocal(float zoom){
        var player=Minecraft.getInstance().player;
        if(player!=null&&player.getMainHandItem().getItem() instanceof IGun g){
            var gun=player.getMainHandItem();var id=g.getAttachmentId(gun,AttachmentType.SCOPE);
            if(com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(id))id=g.getBuiltInAttachmentId(gun,AttachmentType.SCOPE);
            var index=TimelessAPI.getClientAttachmentIndex(id);
            if(index.isPresent()&&index.get().getAttachmentModel()!=null){
                var mesh=index.get().getAttachmentModel().vrOptic(scopeViewIndex(gun,index.get()));
                if(mesh!=null)return OpticMath.focal(zoom,mesh.relief(),mesh.lens().radius());
            }
        }
        return (float)(zoom/Math.tan(Math.toRadians(70)/2));
    }
    public static float worldScale() {
        try {
            String api = "org.vivecraft.api.client.VRClientAPI";
            return Math.max(.1f, Math.min(10f, (Float) VivecraftAccess.call(api,"getWorldScale",VivecraftAccess.call(api,"instance",null))));
        } catch (ReflectiveOperationException e) { return 1; }
    }
    public static boolean canInteract(boolean spatial) {
        var mc = Minecraft.getInstance(); var player = mc.player;
        if (!manual() || player == null || !player.isAlive() || player.isSpectator() || mc.screen != null ||
            !(player.getMainHandItem().getItem() instanceof IGun)) return false;
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty()) return false;
        if (!heldMagazine().isEmpty()) return true;
        if (!spatial) return true;
        var p = pose(false);
        if (p == null) return false;
        Vec3 relative = relative(p);
        boolean gun=magazineShape(player.getMainHandItem()).touches(relative) || boltShape(player.getMainHandItem()).touches(relative) || VRWeaponRig.contact(player.getMainHandItem(),true).touches(relative) || VRWeaponRig.contact(player.getMainHandItem(),false).touches(relative);
        if (pull.stage()==InteractionGate.Stage.PULLING || racking) return true;
        double y = (p.head().position().y-p.off().position().y)/worldScale();
        boolean belt = y > .45 && y < 1.15 && Math.hypot(p.head().position().x-p.off().position().x,p.head().position().z-p.off().position().z)/worldScale() < .65;
        return gun || belt;
    }
    @SubscribeEvent public static void preventBlockAttack(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered e) {
        var player = Minecraft.getInstance().player;
        if (active() && player != null && player.getMainHandItem().getItem() instanceof IGun && e.isAttack()) {
            e.setCanceled(true); e.setSwingHand(false);
        }
    }

    private static void reset() {
        if (aiming && owner != null) IClientPlayerGunOperator.fromLocalPlayer(owner).aim(false);
        if (owner != null) com.tacz.guns.client.input.ShootKey.shootControllerTick(false);
        VRInput.gripping = false;
        aiming = false; held = false; racking = false;
        gunIdentity = null; owner = null; cooldown = 0; insertion.reset();magazineGuide.reset(); pull.reset();
        heldPose=previousHeldPose=null;extractionPose=null;pendingDetachOffset=null;pendingDetachRotation=null;geometry=null;
        magazinePull=lastMagazinePull=Vec3.ZERO;boltPull=lastBoltPull=0;lastDetached=false;
        rackReady=false;rackTicks=settleTicks=detachPending=0;rackGesture.reset();rackReturning=false;pendingRack=pendingRackTicks=0;openAmount=lastOpenAmount=0;loadLatched=false;
    }
    private static void send(int action) {
        var p = Minecraft.getInstance().player;
        if (p == null || gunIdentity == null || cooldown > 0) return;
        if(action==VRActionMessage.RACK){pendingRack=++rackSequence;if(pendingRack==0)pendingRack=++rackSequence;pendingRackTicks=40;}
        NetworkHandler.CHANNEL.sendToServer(new VRActionMessage(p.getInventory().selected, gunIdentity, action,action==VRActionMessage.RACK?pendingRack:0));
        cooldown = 8;
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance(); var p = mc.player;
        while(DEBUG_BOXES.consumeClick())setDebugBoxes(!VRConfig.DEBUG_BOXES.get());
        if (!active() || p == null || !p.isAlive() || p.isSpectator() || mc.screen != null || mc.isPaused() ||
            !(p.getMainHandItem().getItem() instanceof IGun g)) { reset(); return; }
        if(VRConfig.DEBUG_BOXES.get() && p.tickCount%40==0)p.displayClientMessage(debugStatus(),true);
        ItemStack s = p.getMainHandItem();
        if (!s.hasTag() || !s.getTag().hasUUID(VRServer.ID)) { reset(); return; }
        UUID id = s.getTag().getUUID(VRServer.ID);
        if (owner != p || !id.equals(gunIdentity)) { reset(); owner = p; gunIdentity = id; held = GRAB.isDown() || VRInput.gripping; }
        var pose = pose(false);
        if (pose == null) { reset(); return; }
        com.tacz.guns.client.input.ShootKey.shootControllerTick(mc.options.keyAttack.isDown());
        if (cooldown > 0) cooldown--;
        if(pendingRack!=0){
            if(--pendingRackTicks<=0){pendingRack=0;rackFailed();p.displayClientMessage(net.minecraft.network.chat.Component.literal("上膛确认超时，请重新操作"),true);}
            else if(pendingRackTicks%6==0)NetworkHandler.CHANNEL.sendToServer(new VRActionMessage(p.getInventory().selected,gunIdentity,VRActionMessage.RACK,pendingRack));
        }
        Vec3 idealEye = eyeTarget(s,pose);
        double eyeDistance = idealEye.distanceTo(pose.head().position()) / worldScale();
        boolean nextAim = InteractionGate.aiming(aiming, eyeDistance,
            pose.main().direction().dot(pose.head().direction()), VRConfig.AIM_DISTANCE.get());
        var op = IClientPlayerGunOperator.fromLocalPlayer(p);
        if (op.isAim() != nextAim) op.aim(nextAim);
        aiming = nextAim;
        if (!manual() || !p.getOffhandItem().isEmpty()) {heldPose=previousHeldPose=null;geometry=null;return;}
        while (ReloadKey.RELOAD_KEY.consumeClick()) {}
        lastMagazinePull=magazinePull;lastBoltPull=boltPull;
        if (settleTicks>0) settleTicks--;
        if (detachPending>0) detachPending--;
        var mechanics=VRWeapon.get(s);
        loadingContact=mechanics.loose()?VRWeaponRig.contact(s,false):new VRRig.Shape(java.util.List.of(),new Vector3f());
        gateContact=mechanics.gated()?VRWeaponRig.contact(s,true):new VRRig.Shape(java.util.List.of(),new Vector3f());
        lastOpenAmount=openAmount;openAmount+=(VRServer.open(s)?1-openAmount:-openAmount)*.45f;
        boolean detached=VRServer.detached(s);
        if (lastDetached && !detached) {
            settleTicks=4;p.playSound(net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE,.25f,1.8f);
        } else if (!lastDetached && detached) p.playSound(net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,.2f,1.7f);
        lastDetached=detached;
        boolean pressed=GRAB.isDown() || VRInput.gripping, rising=pressed&&!held, released=!pressed&&held;held=pressed;
        Vec3 off=relative(pose);
        VRRig.Shape magazine=magazineShape(s),bolt=boltShape(s);
        boolean near=!mechanics.loose()&&magazine.touches(off);
        boolean nearGate=mechanics.gated()&&gateContact.touches(off);
        double hipY=(pose.head().position().y-pose.off().position().y)/worldScale();
        boolean belt=hipY>.45 && hipY<1.15 && Math.hypot(pose.head().position().x-pose.off().position().x,pose.head().position().z-pose.off().position().z)/worldScale()<.65;
        ItemStack mag=heldMagazine();
        boolean nearSocket=false;
        var targetDisplay=TimelessAPI.getGunDisplay(s);
        geometry=mainRig.geometry(detached);
        if (!mechanics.loose() && !mag.isEmpty() && geometry!=null) {
            var heldDisplay=TimelessAPI.getGunDisplay(heldVisualGun);
            if(heldDisplay.isPresent() && heldRig.ensure(heldVisualGun)){
                var heldShape=heldRig.magazine;
                var mouth=heldRig.mouth;
                heldBoxes=heldRig.carried;
                var rotation=new org.joml.Quaternionf(pose.main().rotation()).conjugate().mul(pose.off().rotation()).normalize();
                previousHeldPose=heldPose;
                var socket=geometry.socket();
                Vector3f desired=rotation.transform(new Vector3f(carryOffset)).add(off.toVector3f());
                rotation.mul(carryRotation).normalize();
                boolean compatible=heldVisualGun.getItem() instanceof IGun hg && hg.getGunId(heldVisualGun).equals(g.getGunId(s))
                    && VRRig.magazineLevel(heldVisualGun)==VRRig.magazineLevel(s);
                if(socket!=null && mouth!=null){
                    var target=magazineGuide.target(detached && compatible,socket.frame(),socket.depth(),
                        new Vector3f(mouth.point()).sub(heldShape.center()),desired,rotation);
                    desired=target.position();rotation=target.rotation();
                }else magazineGuide.reset();
                heldPose=MagazineCollision.move(heldBoxes,geometry.world(),heldPose,desired,rotation);
                if(heldPose!=null && mouth!=null && socket!=null){
                    Vector3f tip=heldPose.rotation().transform(new Vector3f(mouth.point()).sub(heldShape.center())).add(heldPose.position());
                    Vector3f error=socket.error(tip);
                    Vector3f axis=heldPose.rotation().transform(new Vector3f(mouth.axis()));
                    float dot=axis.dot(socket.axis());double lateral=Math.hypot(error.x,error.z);
                    nearSocket=error.lengthSquared()<.18*.18;
                    if(insertion.update(detached,error.y,lateral,dot,socket.depth()) && cooldown==0){send(VRActionMessage.INSERT);insertion.reset();magazineGuide.reset();}
                    if(p.tickCount%10==0 && nearSocket && detached && cooldown==0){
                        String key=dot<.94?"align_magazine":heldPose.blocked()?"collision_blocked":"push_magazine";
                        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("jyvr."+key),true);
                    }
                }else {insertion.reset();magazineGuide.reset();}
            }else {heldPose=previousHeldPose=null;insertion.reset();magazineGuide.reset();}
        }else {heldPose=previousHeldPose=null;insertion.reset();magazineGuide.reset();}
        if(mechanics.loose()&&!mag.isEmpty()){
            boolean contact=VRLooseRound.touches(loadingContact,off,new org.joml.Quaternionf(pose.main().rotation()).conjugate().mul(pose.off().rotation()).normalize(),mag);
            if(!contact)loadLatched=false;
            if(contact&&cooldown==0&&WeaponMechanics.mayLoad(mechanics,VRServer.open(s),g.getCurrentAmmoCount(s)+((mechanics.feed()==WeaponMechanics.Feed.CHAMBERS||mechanics.feed()==WeaponMechanics.Feed.SINGLE)&&g.hasBulletInBarrel(s)?1:0),com.tacz.guns.util.AttachmentDataUtils.getAmmoCountWithAttachment(s,TimelessAPI.getClientGunIndex(g.getGunId(s)).orElseThrow().getGunData()))){send(VRActionMessage.LOAD);loadLatched=true;}
        }else loadLatched=false;
        if(rising&&nearGate&&mag.isEmpty()&&cooldown==0){send(VRActionMessage.TOGGLE);racking=false;pull.reset();return;}
        if (rising && belt && !near && !nearGate && !nearSocket && !bolt.touches(off) && cooldown==0) {
            if (mag.isEmpty()) {pendingDetachOffset=null;pendingDetachRotation=null;carryOffset.zero();carryRotation.identity();send(VRActionMessage.TAKE);}
            else if (mag.is(ModItems.VR_MAGAZINE.get())) send(VRActionMessage.STORE);
            pull.reset();racking=false;return;
        }
        boolean emptyHand=mag.isEmpty() && cooldown==0;
        boolean canPull=!mechanics.loose()&&(!mechanics.gated()||VRServer.open(s))&&magazine.present() && emptyHand && !detached && !racking && detachPending==0;
        if (rising && near && canPull) {
            pullStart=off;racking=false;extractionPose=new MagazineCollision.Pose(new Vector3f(magazine.center()),new org.joml.Quaternionf(),false);
        }
        Vec3 resolvedPull=Vec3.ZERO;double withdrawal=0;
        if(extractionPose!=null && (pull.stage()==InteractionGate.Stage.PULLING || (rising && near && canPull) || detachPending>0) && targetDisplay.isPresent()){
            var openGeometry=mainRig.geometry(true);
            extractionPose=MagazineCollision.move(mainRig.carried,openGeometry.world(),extractionPose,
                new Vector3f(magazine.center()).add(off.subtract(pullStart).toVector3f()),new org.joml.Quaternionf());
            resolvedPull=new Vec3(new Vector3f(extractionPose.position()).sub(magazine.center()));
            if(openGeometry.socket()!=null)withdrawal=Math.max(0,-resolvedPull.toVector3f().dot(openGeometry.socket().axis()));
            geometry=openGeometry;
        }
        if (pull.update(canPull,pressed,rising,near,withdrawal)) {
            var q=new org.joml.Quaternionf(pose.main().rotation()).conjugate().mul(pose.off().rotation()).normalize();
            pendingDetachRotation=new org.joml.Quaternionf(q).conjugate();
            pendingDetachOffset=q.conjugate().transform(new Vector3f(extractionPose.position()).sub(off.toVector3f()));
            send(VRActionMessage.DETACH);detachPending=12;
        }
        if (detached) {magazinePull=Vec3.ZERO;extractionPose=null;}
        else if (pull.stage()==InteractionGate.Stage.PULLING || detachPending>0) magazinePull=resolvedPull;
        else {magazinePull=magazinePull.scale(.45);extractionPose=null;}
        if (rising && pendingRack==0 && emptyHand && !near && !nearGate && !VRServer.open(s) && mechanics.motion()!=WeaponMechanics.Motion.NONE && bolt.touches(off) && pull.stage()!=InteractionGate.Stage.PULLING) {
            racking=true;rackStroke=VRWeaponRig.stroke(s);pullStart=off.subtract(rackStroke.axis().scale(boltPull));rackTicks=0;
            if(boltPull<.001f){rackGesture.reset();rackReturning=false;}
        }
        if (racking) {
            Vec3 delta=off.subtract(pullStart);
            if (++rackTicks>120 || !mag.isEmpty() || delta.length()>.35) {racking=false;}
            else {
                float along=(float)delta.dot(rackStroke.axis());
                float side=(float)delta.subtract(rackStroke.axis().scale(along)).length();
                boolean complete=rackGesture.update(along,side,rackStroke.travel(),mechanics.returnStroke(),released);
                rackReturning=rackGesture.returning();boltPull=rackGesture.position();
                if(complete){
                    // Queue completion even if a preceding magazine operation still has its local cooldown.
                    cooldown=0;send(VRActionMessage.RACK);racking=false;
                }else if(released)racking=false;
            }
        } else if(!mechanics.returnStroke())boltPull*=.35f;
        // Manually operated bolts/pumps remain where released until re-gripped and closed.

    }
    public static void render(GunDisplayInstance display, ItemStack s, int light) {
        if (scopePass() || pose(true) == null) return;

        var model = display.getGunModel();
        PoseStack pose;
        try { pose=handRenderSpace(InteractionHand.MAIN_HAND); }
        catch(ReflectiveOperationException e){VivecraftAccess.warn(e);return;}
        PoseStack handSpace=new PoseStack();handSpace.mulPoseMatrix(pose.last().pose());handSpace.last().normal().set(pose.last().normal());
        // Desktop draw/reload/root animations must never move the controller anchor or magazine socket.
        VRRig.stabilize(model);
        pose.mulPoseMatrix(VRRecoil.offset(s));pose.mulPoseMatrix(modelToHand(model,s));
        pose.last().normal().set(new org.joml.Matrix3f(pose.last().pose()).invert().transpose());
        boolean hands = model.getRenderHand();
        float partial=Minecraft.getInstance().getFrameTime();
        Vec3 delta=lastMagazinePull.lerp(magazinePull,partial);
        if (settleTicks>0) delta=delta.add(0,-.008*(settleTicks/4.0),0);
        Runnable restoreMag=VRWeapon.get(s).loose()?()->{}:VRRig.move(model,s,model.getVRMagazinePath(),delta);
        float slide=net.minecraft.util.Mth.lerp(partial,lastBoltPull,boltPull);
        Runnable restoreBolt=VRWeaponRig.animate(model,s,slide,net.minecraft.util.Mth.lerp(partial,lastOpenAmount,openAmount),rackReturning);
        renderingGun = true;
        try {
            model.setRenderHand(false);
            RenderType type = display.enablesTransparency() ? RenderType.entityTranslucent(display.getModelTexture()) : RenderType.entityCutout(display.getModelTexture());
            model.render(pose,s,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,type,light,OverlayTexture.NO_OVERLAY);
        } finally {
            restoreBolt.run();restoreMag.run();renderingGun = false; model.setRenderHand(hands); model.cleanAnimationTransform();
        }
        if(VRConfig.DEBUG_BOXES.get() && mainRig.ensure(s))geometry=mainRig.geometry(VRServer.detached(s) || extractionPose!=null);
        renderHeld(handSpace,light);
        renderLooseRound(light);
        var localPlayer=Minecraft.getInstance().player;if(localPlayer!=null)lastRenderTick=localPlayer.tickCount;
    }
    private static void renderLooseRound(int light){
        ItemStack held=heldMagazine();if(held.isEmpty()||!held.hasTag()||!held.getTag().getBoolean("LooseRound"))return;
        try{PoseStack pose=handRenderSpace(InteractionHand.OFF_HAND);
            VRLooseRound.render(pose,handBuffers,held);
        }catch(ReflectiveOperationException ex){VivecraftAccess.warn(ex);}
    }

    private static void renderHeld(PoseStack handSpace,int light){
        if(handBuffers==null)handBuffers=net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(262144));
        var buffers=handBuffers;
        MagazineCollision.Pose visual=heldPose;
        // Physics runs only on client ticks, never once per eye or scope pass.
        if(visual!=null && previousHeldPose!=null){
            float t=net.minecraft.util.Mth.clamp(Minecraft.getInstance().getFrameTime(),0,1);
            visual=new MagazineCollision.Pose(new Vector3f(previousHeldPose.position()).lerp(visual.position(),t),
                new org.joml.Quaternionf(previousHeldPose.rotation()).slerp(visual.rotation(),t),visual.blocked());
        }
        try {
            if(visual!=null && geometry!=null && !heldMagazine().isEmpty()){
                var display=TimelessAPI.getGunDisplay(heldVisualGun);
                if(display.isPresent()){
                    handSpace.pushPose();
                    try {
                        handSpace.translate(visual.position().x,visual.position().y,visual.position().z);handSpace.mulPose(visual.rotation());
                        handSpace.mulPoseMatrix(heldRig.carry);
                        handSpace.last().normal().mul(new org.joml.Matrix3f(heldRig.carry).invert().transpose());
                        var d=display.get();RenderType type=d.enablesTransparency()?RenderType.entityTranslucent(d.getModelTexture()):RenderType.entityCutout(d.getModelTexture());
                        d.getGunModel().renderVRMagazine(handSpace,heldVisualGun,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,buffers,type,light,OverlayTexture.NO_OVERLAY);
                        // Submit while Vivecraft's current eye framebuffer and hand transform are active.
                        buffers.endBatch(type);
                    }finally{handSpace.popPose();}
                }
            }
            if(VRConfig.DEBUG_BOXES.get() && geometry!=null){
                VRDebugRenderer.render(handSpace,buffers,geometry,visual!=null?heldBoxes:mainRig.carried,visual!=null?visual:extractionPose);
            }
        }finally{buffers.endBatch();}
    }
}
