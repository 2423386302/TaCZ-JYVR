package com.tacz.guns.mixin.client;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.compat.jyvr.VRClient;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Isolate the off-screen camera from GUI scissors and the headset hidden-area stencil. */
@Pseudo
@Mixin(targets="org.vivecraft.client_vr.render.helpers.VRPassHelper",remap=false)
public abstract class JYVRScopePassMixin {
    private static boolean jyvr$scope, jyvr$stencil, jyvr$scissor;
    private static int[] jyvr$scissorBox;
    private static com.mojang.blaze3d.pipeline.RenderTarget jyvr$target;
    @Inject(method="renderAndSubmit",at=@At("HEAD"),require=0,remap=false)
    private static void jyvr$newFrame(boolean level,long time,float tick,CallbackInfo ci){
        com.tacz.guns.compat.jyvr.ScopeSceneFrame.begin();
    }
    @Inject(method="renderSingleView",at=@At("HEAD"),require=0,remap=false)
    private static void jyvr$beginScope(@Coerce Object pass,float tick,long time,boolean level,CallbackInfo ci){

        jyvr$scope="SCOPER".equals(String.valueOf(pass))&&VRClient.wantsScope();if(!jyvr$scope)return;
        VRClient.capturingScope=true;
        jyvr$target=net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        jyvr$stencil=GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        jyvr$scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        jyvr$scissorBox=new int[4];GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,jyvr$scissorBox);
        GL11.glDisable(GL11.GL_STENCIL_TEST);RenderSystem.disableScissor();
        // These must be set BEFORE Vivecraft clears this render target.
        RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1,1,1,1);RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }
    @Inject(method="renderSingleView",at=@At("RETURN"),require=0,remap=false)
    private static void jyvr$endScope(@Coerce Object pass,float tick,long time,boolean level,CallbackInfo ci){
        com.tacz.guns.compat.jyvr.VRScopeDebug.endEye(String.valueOf(pass));
        if(!jyvr$scope)return;jyvr$scope=false;
        net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        // The telescope world pass has returned: entity buffers and world composition are finished.
        if(level && jyvr$target!=null && net.minecraft.client.Minecraft.getInstance().level!=null){
            int texture=jyvr$target.getColorTextureId();
            com.tacz.guns.compat.jyvr.ScopeSceneFrame.complete(texture);
            if(texture>0)com.tacz.guns.compat.jyvr.VRScopeDebug.captureTexture(texture,jyvr$target.width,jyvr$target.height,"SCOPER");
        }
        jyvr$target=null;
        VRClient.capturingScope=false;
        if(jyvr$stencil)GL11.glEnable(GL11.GL_STENCIL_TEST);else GL11.glDisable(GL11.GL_STENCIL_TEST);
        if(jyvr$scissor)RenderSystem.enableScissor(jyvr$scissorBox[0],jyvr$scissorBox[1],jyvr$scissorBox[2],jyvr$scissorBox[3]);
        else RenderSystem.disableScissor();
    }
}
