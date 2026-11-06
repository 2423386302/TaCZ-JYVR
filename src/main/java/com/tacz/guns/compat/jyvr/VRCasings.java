package com.tacz.guns.compat.jyvr;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayDeque;
@Mod.EventBusSubscriber(value=Dist.CLIENT,modid=GunMod.MOD_ID)
public final class VRCasings {
    private static final ArrayDeque<Case> cases=new ArrayDeque<>();
    private static MultiBufferSource.BufferSource buffers;
    private static Object level;
    private static final class Case {
        Vec3 previous,position,velocity;final Matrix4f orientation;final BedrockAmmoModel model;final ResourceLocation texture;final Vector3f spin;int age;boolean resting;float spinTime;
        Case(Vec3 p,Vec3 v,Matrix4f o,BedrockAmmoModel m,ResourceLocation t,Vector3f s){previous=position=p;velocity=v;orientation=o;model=m;texture=t;spin=s;}
    }
    public static void clear(){cases.clear();level=null;}
    public static void spawn(ItemStack gun,int count){
        var mc=Minecraft.getInstance();if(!VRClient.active()||!VRConfig.CASINGS.get()||mc.level==null||mc.player==null||count<=0||!(gun.getItem() instanceof IGun g))return;
        if(level!=mc.level){clear();level=mc.level;}
        var held=mc.player.getMainHandItem();if(!held.hasTag()||!gun.hasTag()||!held.getTag().hasUUID(VRServer.ID)||!gun.getTag().hasUUID(VRServer.ID)||!held.getTag().getUUID(VRServer.ID).equals(gun.getTag().getUUID(VRServer.ID)))return;
        var pose=VRClient.pose(false);var display=TimelessAPI.getGunDisplay(gun);var data=TimelessAPI.getClientGunIndex(g.getGunId(gun));
        if(pose==null||display.isEmpty()||data.isEmpty()||display.get().getGunModel()==null)return;
        var index=TimelessAPI.getClientAmmoIndex(data.get().getGunData().getAmmoId());if(index.isEmpty()||index.get().getShellModel()==null||index.get().getShellTextureLocation()==null)return;
        var model=display.get().getGunModel();var path=model.getVRPartPath("shell");
        if(path==null)path=model.getVRPartPath("shell_ejection");
        if(path==null)path=model.getVRPartPath(VRWeapon.get(gun).portBone());
        Matrix4f frame=new Matrix4f().rotation(pose.main().rotation()).scale(VRClient.worldScale()).mul(VRWeaponRig.frame(model,gun,path));
        Vec3 origin=pose.main().position().add(new Vec3(frame.getTranslation(new Vector3f())));frame.setTranslation(0,0,0);
        var settings=display.get().getShellEjection();
        Vector3f speed=settings==null?new Vector3f(3,2,0):new Vector3f(settings.getInitialVelocity());
        Vector3f random=settings==null?new Vector3f(.5f,.5f,.3f):new Vector3f(settings.getRandomVelocity());
        for(int n=0;n<Math.min(64,count);n++){
            Vector3f velocity=new Vector3f(-speed.x-random.x*mc.level.random.nextFloat(),-speed.y-random.y*mc.level.random.nextFloat(),speed.z+random.z*mc.level.random.nextFloat());frame.transformDirection(velocity);
            if(cases.size()>=256)cases.removeFirst();
            cases.addLast(new Case(origin,new Vec3(velocity),new Matrix4f(frame),index.get().getShellModel(),index.get().getShellTextureLocation(),new Vector3f(8,11,4)));
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level!=level||!VRClient.active()||!VRConfig.CASINGS.get()){clear();return;}if(mc.isPaused())return;
        int limit=(int)Math.ceil(20*VRConfig.SECONDS.get());cases.removeIf(c->++c.age>=limit&&VRConfig.TIMED.get());
        double gravity=.49*VRClient.worldScale();
        for(var c:cases){c.previous=c.position;if(c.resting)continue;c.velocity=c.velocity.add(0,-gravity,0);Vec3 next=c.position.add(c.velocity.scale(.05));
            var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(c.position,next,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK){Vec3 n=Vec3.atLowerCornerOf(hit.getDirection().getNormal());c.position=hit.getLocation().add(n.scale(.008));c.velocity=c.velocity.subtract(n.scale(1.25*c.velocity.dot(n))).scale(.45);c.resting=hit.getDirection()==net.minecraft.core.Direction.UP&&c.velocity.lengthSqr()<.5;}else c.position=next;c.spinTime=c.age*.05f;}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||cases.isEmpty()||!VRClient.active()||VRClient.scopePass()||!VRConfig.CASINGS.get())return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new BufferBuilder(65536));
        PoseStack pose=e.getPoseStack();Vec3 camera=e.getCamera().getPosition();float partial=e.getPartialTick();
        try{for(var c:cases){Vec3 p=c.previous.lerp(c.position,partial).subtract(camera);pose.pushPose();
            try{pose.translate(p.x,p.y,p.z);pose.mulPoseMatrix(c.orientation);float time=c.resting?c.spinTime:(c.age+partial)*.05f;
                pose.mulPose(new Quaternionf().rotationXYZ(time*c.spin.x,time*c.spin.y,time*c.spin.z));pose.translate(0,-1.5,0);
                pose.last().normal().set(new Matrix3f(pose.last().pose()).invert().transpose());
                c.model.renderVRBatched(pose,ItemDisplayContext.GROUND,buffers.getBuffer(RenderType.entityCutout(c.texture)),15728880,OverlayTexture.NO_OVERLAY);
            }finally{pose.popPose();}}
        }finally{buffers.endBatch();}
    }
}
