package com.tacz.guns.compat.jyvr;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;

/** One explicitly requested raw scope frame, before lens geometry and the eye-box vignette. */
public final class VRScopeDebug {
    private static long deadline;
    private static long pair;private static String pairEye="";
    public static int request(){
        deadline=System.nanoTime()+30_000_000_000L;
        var p=Minecraft.getInstance().player;
        if(p!=null)p.displayClientMessage(Component.literal("关闭聊天后在 30 秒内举枪开镜；将保存一张镜内原始画面到 screenshots。"),false);
        return 1;
    }
    public static void captureTexture(int tex,int w,int h,String pass){
        if(deadline==0)return;long end=deadline;deadline=0;if(System.nanoTime()>end)return;
        var mc=Minecraft.getInstance();int bound=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        try(var img=new com.mojang.blaze3d.platform.NativeImage(w,h,false)){
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,tex);img.downloadTexture(0,true);img.flipY();
            var dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);pair=System.currentTimeMillis();pairEye=pass.equals("SCOPER")?"LEFT":pass;var file=dir.resolve("jyvr-scope-"+pair+"-"+pass+"-source.png");img.writeToFile(file.toFile());
            if(mc.player!=null)mc.player.displayClientMessage(Component.literal("已保存镜内采样原图："+file.getFileName()),false);
        }catch(Exception ex){com.tacz.guns.GunMod.LOGGER.warn("Scope source capture failed",ex);}finally{org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,bound);}
    }
    public static void endEye(String eye){
        if(pair==0||!eye.equals(pairEye))return;
        long id=pair;pair=0;var mc=Minecraft.getInstance();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            var file=mc.gameDirectory.toPath().resolve("screenshots").resolve("jyvr-scope-"+id+"-"+eye+"-composite.png");image.writeToFile(file.toFile());
            if(mc.player!=null)mc.player.displayClientMessage(Component.literal("已保存同一眼的合成画面："+file.getFileName()),false);
        }catch(Exception ex){com.tacz.guns.GunMod.LOGGER.warn("Scope composite capture failed",ex);}
    }
    public static void afterPass(){
        if(deadline==0)return;
        long end=deadline;deadline=0;if(System.nanoTime()>end)return;
        var mc=Minecraft.getInstance();var target=mc.getMainRenderTarget();
        try(var image=Screenshot.takeScreenshot(target)){
            var dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);
            var file=dir.resolve("jyvr-scope-"+System.currentTimeMillis()+".png");image.writeToFile(file.toFile());
            if(mc.player!=null)mc.player.displayClientMessage(Component.literal("已保存镜内原始画面："+file.getFileName()),false);
        }catch(Exception ex){
            com.tacz.guns.GunMod.LOGGER.warn("Could not capture requested scope frame",ex);
            if(mc.player!=null)mc.player.displayClientMessage(Component.literal("保存镜内画面失败，请查看 latest.log。"),false);
        }
    }
}
