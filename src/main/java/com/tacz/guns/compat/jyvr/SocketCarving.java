package com.tacz.guns.compat.jyvr;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Conservative cavity subtraction: retained solids are always subsets of the original mesh cube. */
public final class SocketCarving {
    public record Bounds(Vector3f lo, Vector3f hi) {}
    public static List<Bounds> subtract(Matrix4f cube,Vector3f lo,Vector3f hi,
                                       Matrix4f channel,Vector3f cutLo,Vector3f cutHi) {
        Matrix4f toCube=new Matrix4f(cube).invert().mul(channel);
        Vector3f a=new Vector3f(Float.POSITIVE_INFINITY),b=new Vector3f(Float.NEGATIVE_INFINITY);
        for(int n=0;n<8;n++){
            Vector3f p=toCube.transformPosition(new Vector3f((n&1)==0?cutLo.x:cutHi.x,
                (n&2)==0?cutLo.y:cutHi.y,(n&4)==0?cutLo.z:cutHi.z));a.min(p);b.max(p);
        }
        if(hi.x<=a.x || lo.x>=b.x || hi.y<=a.y || lo.y>=b.y || hi.z<=a.z || lo.z>=b.z)
            return List.of(new Bounds(new Vector3f(lo),new Vector3f(hi)));
        float x0=Math.max(lo.x,a.x),x1=Math.min(hi.x,b.x),y0=Math.max(lo.y,a.y),y1=Math.min(hi.y,b.y),z0=Math.max(lo.z,a.z),z1=Math.min(hi.z,b.z);
        List<Bounds> result=new ArrayList<>(6);
        add(result,lo.x,lo.y,lo.z,x0,hi.y,hi.z);add(result,x1,lo.y,lo.z,hi.x,hi.y,hi.z);
        add(result,x0,lo.y,lo.z,x1,y0,hi.z);add(result,x0,y1,lo.z,x1,hi.y,hi.z);
        add(result,x0,y0,lo.z,x1,y1,z0);add(result,x0,y0,z1,x1,y1,hi.z);
        return result;
    }
    private static void add(List<Bounds> result,float x0,float y0,float z0,float x1,float y1,float z1){
        if(x1-x0>1e-5f && y1-y0>1e-5f && z1-z0>1e-5f)
            result.add(new Bounds(new Vector3f(x0,y0,z0),new Vector3f(x1,y1,z1)));
    }
}
