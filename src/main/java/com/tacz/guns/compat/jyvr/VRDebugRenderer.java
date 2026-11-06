package com.tacz.guns.compat.jyvr;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** Diagnostic x-ray overlay: actual collision boxes, two batches per eye, explicit submission. */
public final class VRDebugRenderer extends RenderType {
    private VRDebugRenderer(String name,VertexFormat format,VertexFormat.Mode mode,int size,boolean crumbling,boolean sorted,Runnable setup,Runnable clear){super(name,format,mode,size,crumbling,sorted,setup,clear);}
    private static final RenderType FILL=create("jyvr_collision_fill",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,262144,false,false,
        CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private static final RenderType EDGE=create("jyvr_collision_edge",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,262144,false,false,
        CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private static final int[] FACES={0,1,3,2,4,6,7,5,0,4,5,1,2,3,7,6,0,2,6,4,1,5,7,3};
    public static void render(PoseStack stack,MultiBufferSource.BufferSource buffers,VRRig.Geometry geometry,List<MagazineCollision.Box> carried,MagazineCollision.Pose pose){
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        try {
            // NO_DEPTH_TEST alone does not necessarily turn off a previous renderer's depth state.
            for(boolean fill:new boolean[]{true,false}){
                RenderSystem.disableDepthTest();
                var consumer=buffers.getBuffer(fill?FILL:EDGE);
                for(var b:geometry.debugBody())box(stack,consumer,fill,b.matrix(),b.min(),b.max(),.1f,.65f,1f,.02f);
                if(pose!=null)for(var local:carried){
                    var b=local.transform(pose.position(),pose.rotation());
                    Matrix4f m=new Matrix4f(b.x().x,b.x().y,b.x().z,0,b.y().x,b.y().y,b.y().z,0,b.z().x,b.z().y,b.z().z,0,b.center().x,b.center().y,b.center().z,1);
                    box(stack,consumer,fill,m,new Vector3f(b.half()).negate(),b.half(),pose.blocked()?1f:.2f,pose.blocked()?.2f:1f,.15f,.22f);
                }
                var player=net.minecraft.client.Minecraft.getInstance().player;
                if(player!=null){var gun=player.getMainHandItem();var mechanics=VRWeapon.get(gun);
                    var tracking=VRClient.pose(false);
                    var hand=tracking==null?new net.minecraft.world.phys.Vec3(100,100,100):VRClient.relative(tracking);
                    drawShape(stack,consumer,fill,VRClient.boltShape(gun),hand,1f,.45f,.06f);
                    if(mechanics.loose())drawShape(stack,consumer,fill,VRClient.loadingContact,hand,.15f,1f,.25f);
                    else drawShape(stack,consumer,fill,VRClient.magazineShape(gun),hand,1f,.85f,.08f);
                    if(mechanics.gated())drawShape(stack,consumer,fill,VRClient.gateContact,hand,.8f,.2f,1f);
                }
                var socket=geometry.socket();
                if(socket!=null)box(stack,consumer,fill,socket.frame(),new Vector3f(socket.lo().x,-socket.depth(),socket.lo().z),new Vector3f(socket.hi().x,-socket.depth()+.008f,socket.hi().z),1f,.75f,.05f,.35f);
                buffers.endBatch(fill?FILL:EDGE);
            }
        }finally{if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();}
    }
    private static void drawShape(PoseStack s,VertexConsumer c,boolean fill,VRRig.Shape shape,net.minecraft.world.phys.Vec3 hand,float r,float g,float b){
        boolean near=shape.touches(hand);int count=0;
        for(var box:shape.boxes()){
            if(++count>256)break;
            box(s,c,fill,box.matrix(),box.min(),box.max(),near?1:r,near?1:g,near?1:b,near?.40f:.20f);
        }
    }
    private static void box(PoseStack stack,VertexConsumer consumer,boolean fill,Matrix4f matrix,Vector3f lo,Vector3f hi,float r,float g,float b,float alpha){
        stack.pushPose();try {
            stack.mulPoseMatrix(matrix);stack.last().normal().mul(new org.joml.Matrix3f(matrix).invert().transpose());
            if(fill){for(int n:FACES)consumer.vertex(stack.last().pose(),(n&1)==0?lo.x:hi.x,(n&2)==0?lo.y:hi.y,(n&4)==0?lo.z:hi.z).color(r,g,b,alpha).endVertex();}
            else LevelRenderer.renderLineBox(stack,consumer,new AABB(lo.x,lo.y,lo.z,hi.x,hi.y,hi.z),r,g,b,.3f);
        }finally{stack.popPose();}
    }
}
