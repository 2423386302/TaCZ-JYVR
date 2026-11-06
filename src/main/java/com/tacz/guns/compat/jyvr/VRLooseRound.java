package com.tacz.guns.compat.jyvr;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** One visible cartridge, with the very same hand-space dimensions used by insertion. */
public final class VRLooseRound extends RenderType {
    private VRLooseRound(String n,VertexFormat f,VertexFormat.Mode m,int s,boolean c,boolean sort,Runnable a,Runnable b){super(n,f,m,s,c,sort,a,b);}
    private static final RenderType TYPE=create("jyvr_live_round",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,2048,false,false,
        CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false));
    public record Size(float radius,float length,boolean shell){}
    public static Size size(String id){return id.contains("12g")?new Size(.009f,.060f,true):id.contains("40mm")?new Size(.020f,.090f,false):new Size(.0055f,.065f,false);}
    private static Size size(ItemStack held){return size(held.getOrCreateTag().getString("AmmoId"));}
    public static Vector3f point(Size s,float fraction){return new Vector3f(0,0,-.025f+(0.5f-fraction)*s.length());}
    public static boolean touches(VRRig.Shape port,Vec3 hand,Quaternionf rotation,ItemStack held){
        var s=size(held);
        // Sample the short visible cartridge, rather than a distant controller origin.
        for(int i=0;i<=6;i++){
            Vec3 point=new Vec3(rotation.transform(point(s,i/6f))).add(hand);
            for(var box:port.boxes())if(box.touches(point,s.radius()+.006f))return true;
        }
        return false;
    }
    public static void render(PoseStack stack,MultiBufferSource.BufferSource buffers,ItemStack held){
        var s=size(held);var out=buffers.getBuffer(TYPE);var m=stack.last().pose();
        float back=point(s,0).z,front=point(s,1).z;
        ring(m,out,s.radius()*1.08f,back,back-.006f,.76f,.55f,.19f);
        ring(m,out,s.radius(),back-.006f,front+.012f,s.shell()?.65f:.72f,s.shell()?.08f:.50f,s.shell()?.04f:.18f);
        ring(m,out,s.radius()*(s.shell()?1:.7f),front+.012f,front,s.shell()?.55f:.65f,s.shell()?.07f:.36f,s.shell()?.03f:.16f);
        buffers.endBatch(TYPE);
    }
    private static void ring(Matrix4f m,VertexConsumer out,float r,float a,float b,float red,float green,float blue){
        for(int i=0;i<12;i++){
            double t=i*Math.PI/6,u=(i+1)*Math.PI/6;float x=(float)Math.cos(t)*r,y=(float)Math.sin(t)*r,X=(float)Math.cos(u)*r,Y=(float)Math.sin(u)*r;
            float shade=.72f+.28f*(float)Math.abs(Math.cos(t));
            v(m,out,x,y,a,red*shade,green*shade,blue*shade);v(m,out,X,Y,a,red*shade,green*shade,blue*shade);v(m,out,X,Y,b,red*shade,green*shade,blue*shade);v(m,out,x,y,b,red*shade,green*shade,blue*shade);
            v(m,out,0,0,b,red,green,blue);v(m,out,x,y,b,red,green,blue);v(m,out,X,Y,b,red,green,blue);v(m,out,0,0,b,red,green,blue);
            v(m,out,0,0,a,red,green,blue);v(m,out,X,Y,a,red,green,blue);v(m,out,x,y,a,red,green,blue);v(m,out,0,0,a,red,green,blue);
        }
    }
    private static void v(Matrix4f m,VertexConsumer c,float x,float y,float z,float r,float g,float b){c.vertex(m,x,y,z).color(r,g,b,1).endVertex();}
}
