package com.tacz.guns.compat.jyvr;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Optical geometry, independent of Minecraft and OpenGL. */
public final class OpticMath {
    private OpticMath() {}
    public record Vertex(float x,float y,float u,float v) {
        Vertex mix(Vertex b,float t){return new Vertex(x+(b.x-x)*t,y+(b.y-y)*t,u+(b.u-u)*t,v+(b.v-v)*t);}
    }
    public static Matrix4f attachmentView(Matrix4f anchor,Matrix4f view){return new Matrix4f(anchor).translate(0,-1.5f,0).mul(view);}
    public static boolean hideHud(boolean active,boolean gun,boolean aiming,boolean screen,boolean enabled){return enabled&&active&&gun&&aiming&&!screen;}
    public static Vertex project(Vector3f vertex,float u,float v,Vector3f eye,float cx,float cy,float z,float radius){
        float dz=vertex.z-eye.z;
        if(Math.abs(dz)<1e-6f||radius<=0)return null;
        float t=(z-eye.z)/dz;
        if(!(t>0)||!Float.isFinite(t))return null;
        float x=(eye.x+(vertex.x-eye.x)*t-cx)/radius,y=(eye.y+(vertex.y-eye.y)*t-cy)/radius;
        return Float.isFinite(x)&&Float.isFinite(y)?new Vertex(x,y,u,v):null;
    }
    /** Clip geometry and UVs together to an inscribed 64-sided aperture. */
    public static List<Vertex> clip(List<Vertex> triangle){
        List<Vertex> out=new ArrayList<>(triangle);float limit=(float)Math.cos(Math.PI/64);
        for(int side=0;side<64&&!out.isEmpty();side++){
            double angle=(side+.5)*Math.PI/32;float nx=(float)Math.cos(angle),ny=(float)Math.sin(angle);
            List<Vertex> input=out;out=new ArrayList<>();Vertex a=input.get(input.size()-1);float da=limit-a.x*nx-a.y*ny;
            for(Vertex b:input){float db=limit-b.x*nx-b.y*ny;if((da>=0)!=(db>=0))out.add(a.mix(b,da/(da-db)));if(db>=0)out.add(b);a=b;da=db;}
        }
        return out;
    }
    /** Per-eye exit-pupil approximation; all lengths use the same lens-local units. */
    public static float transmission(float x,float y,Vector3f eye,float radius,float relief){
        if(!eye.isFinite()||radius<=0||relief<=0||eye.z<=radius*.1f)return 0;
        float longitudinal=Math.abs(eye.z-relief)/Math.max(relief*1.8f,radius*4);
        float aperture=Math.max(0,1.65f-longitudinal),dx=x+eye.x/radius*.9f,dy=y+eye.y/radius*.9f;
        float t=Math.max(0,Math.min(1,(aperture-(float)Math.sqrt(dx*dx+dy*dy))/.35f));return t*t*(3-2*t);
    }
    public static float textureU(float x){return .5f-.5f*x;}
    public static float textureV(float y){return .5f-.5f*y;}
    public static float focal(float zoom,float relief,float radius){
        if(!Float.isFinite(zoom)||!Float.isFinite(relief)||!Float.isFinite(radius)||relief<=0||radius<=0)return 1;
        return Math.max(1,Math.min(32,zoom))*relief/radius;
    }
}
