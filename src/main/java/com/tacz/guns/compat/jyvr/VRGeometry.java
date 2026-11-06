package com.tacz.guns.compat.jyvr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.*;
import org.joml.Vector3f;

/** Measures the gun pack's ocular geometry in its bone-local coordinates. */
public final class VRGeometry {
    public record Lens(float x, float y, float z, float radius) {}
    public static Lens lens(BedrockPart ocular, float fallbackRadius) {
        float[] bounds={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,
                        Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        collect(ocular,new PoseStack(),bounds,0);
        if (!Float.isFinite(bounds[0])) return new Lens(0,0,.001f,fallbackRadius);
        float radius=.5f*Math.min(bounds[3]-bounds[0],bounds[4]-bounds[1]);
        if (!Float.isFinite(radius) || radius<.003f || radius>.15f) radius=fallbackRadius;
        // Positive local Z is the rear (eye-facing) side of standard TaCZ models.
        return new Lens((bounds[0]+bounds[3])*.5f,(bounds[1]+bounds[4])*.5f,bounds[5]+.0005f,radius);
    }
    private static void collect(BedrockPart part,PoseStack pose,float[] bounds,int depth) {
        if (depth>64) return;
        for (BedrockCube cube:part.cubes) {
            float[] p;
            if(cube instanceof BedrockCubeBox c) p=new float[]{c.minX-c.inflate,c.minY-c.inflate,c.minZ-c.inflate,c.maxX+c.inflate,c.maxY+c.inflate,c.maxZ+c.inflate};
            else if(cube instanceof BedrockCubePerFace c) p=new float[]{c.minX-c.inflate,c.minY-c.inflate,c.minZ-c.inflate,c.maxX+c.inflate,c.maxY+c.inflate,c.maxZ+c.inflate};
            else continue;
            for(int n=0;n<8;n++) {
                Vector3f v=new Vector3f(p[(n&1)==0?0:3]/16,p[(n&2)==0?1:4]/16,p[(n&4)==0?2:5]/16);
                pose.last().pose().transformPosition(v);
                bounds[0]=Math.min(bounds[0],v.x); bounds[1]=Math.min(bounds[1],v.y); bounds[2]=Math.min(bounds[2],v.z);
                bounds[3]=Math.max(bounds[3],v.x); bounds[4]=Math.max(bounds[4],v.y); bounds[5]=Math.max(bounds[5],v.z);
            }
        }
        for(BedrockPart child:part.children) {
            pose.pushPose();
            try { child.translateAndRotateAndScale(pose); collect(child,pose,bounds,depth+1); }
            finally { pose.popPose(); }
        }
    }
}
