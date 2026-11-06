package com.tacz.guns.compat.jyvr;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import java.util.ArrayList;
import java.util.List;

/** Physical aperture and pack reticle; no desktop mask or shared stencil mutation. */
public final class VROptic {
    private static final BufferBuilder BUFFER=new BufferBuilder(262144);
    public record Mesh(VRGeometry.Lens lens,float relief,List<OpticMath.Vertex> reticle) {}
    private static Matrix4f restPath(List<BedrockPart> path){
        Matrix4f matrix=new Matrix4f();for(BedrockPart part:path)matrix.mul(VRRig.restPartMatrix(part));return matrix;
    }
    /** Once per resource/model/view. No per-frame mesh extraction or clipping. */
    public static Mesh build(List<BedrockPart> ocular,List<BedrockPart> division,List<BedrockPart> view){
        var measured=VRGeometry.lens(ocular.get(ocular.size()-1),.018f);
        var lens=new VRGeometry.Lens(measured.x(),measured.y(),measured.z(),measured.radius()*.96f);
        Matrix4f inverse=restPath(ocular).invert();
        Vector3f eye=view==null?new Vector3f(lens.x(),lens.y(),lens.z()+lens.radius()*4):inverse.transformPosition(restPath(view).getTranslation(new Vector3f()));
        if(!eye.isFinite()||eye.z<=lens.z())eye.set(lens.x(),lens.y(),lens.z()+lens.radius()*4);
        List<OpticMath.Vertex> triangles=new ArrayList<>();
        if(division!=null){
            PoseStack stack=new PoseStack();stack.mulPoseMatrix(inverse.mul(restPath(division)));
            collect(division.get(division.size()-1),stack,new Capture(eye,lens,triangles),0);
        }
        return new Mesh(lens,eye.z-lens.z(),List.copyOf(triangles));
    }
    private static void collect(BedrockPart part,PoseStack pose,Capture capture,int depth){
        if(depth>64)return;
        part.compile(pose.last(),capture,15728880,0,1,1,1,1);
        for(BedrockPart child:part.children){pose.pushPose();try{pose.mulPoseMatrix(VRRig.restPartMatrix(child));collect(child,pose,capture,depth+1);}finally{pose.popPose();}}
    }
    private static final class Capture implements VertexConsumer {
        final Vector3f eye;final VRGeometry.Lens lens;final List<OpticMath.Vertex> output;
        final OpticMath.Vertex[] quad=new OpticMath.Vertex[4];int count;double x,y,z;float u,v,nz;
        Capture(Vector3f eye,VRGeometry.Lens lens,List<OpticMath.Vertex> output){this.eye=eye;this.lens=lens;this.output=output;}
        public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
        public VertexConsumer color(int r,int g,int b,int a){return this;}
        public VertexConsumer uv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer overlayCoords(int u,int v){return this;}
        public VertexConsumer uv2(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){nz=z;return this;}
        public void defaultColor(int r,int g,int b,int a){}
        public void unsetDefaultColor(){}
        public void endVertex(){
            quad[count++]=OpticMath.project(new Vector3f((float)x,(float)y,(float)z),u,v,eye,lens.x(),lens.y(),lens.z(),lens.radius());
            if(count==4){count=0;if(nz<=.01f)return;
                for(int i=1;i<3;i++){
                    if(quad[0]==null||quad[i]==null||quad[i+1]==null)continue;
                    var clipped=OpticMath.clip(List.of(quad[0],quad[i],quad[i+1]));
                    for(int n=1;n+1<clipped.size();n++){
                        var a=clipped.get(0);var b=clipped.get(n);var c=clipped.get(n+1);
                        if(Math.abs((b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x()))<1e-9f)continue;
                        output.add(a);output.add(b);output.add(c);
                    }
                }
            }
        }
    }
    public static void draw(PoseStack pose,ItemStack attachment,Mesh mesh,boolean magnified){
        if(VRClient.scopeScene())return; // Never sample the scope texture while writing that texture.
        int texture=magnified?VRClient.scopeTexture():-1;
        boolean optical=magnified&&VRConfig.SCOPES.get()&&texture>=0;var lens=mesh.lens();Matrix4f matrix=pose.last().pose();
        Vector3f eye=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(matrix).invert().transformPosition(new Vector3f()).sub(lens.x(),lens.y(),lens.z());
        var shader=RenderSystem.getShader();int previousTexture=RenderSystem.getShaderTexture(0);float[] color=RenderSystem.getShaderColor().clone();
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRGB=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRGB=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try{
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableCull();RenderSystem.enableDepthTest();
            if(optical){
                RenderSystem.disableBlend();RenderSystem.depthMask(true);
                RenderSystem.setShader(texture>=0?VRScopeShader::worldColor:GameRenderer::getPositionColorShader);
                if(texture>=0)RenderSystem.setShaderTexture(0,texture);
                BUFFER.begin(VertexFormat.Mode.TRIANGLES,texture>=0?DefaultVertexFormat.POSITION_TEX_COLOR:DefaultVertexFormat.POSITION_COLOR);
                for(int r=0;r<12;r++)for(int i=0;i<64;i++){
                    float a=(float)(i*Math.PI/32),b=(float)((i+1)*Math.PI/32);
                    float x0=(float)Math.cos(a)*r/12,y0=(float)Math.sin(a)*r/12,x1=(float)Math.cos(a)*(r+1)/12,y1=(float)Math.sin(a)*(r+1)/12;
                    float x2=(float)Math.cos(b)*(r+1)/12,y2=(float)Math.sin(b)*(r+1)/12,x3=(float)Math.cos(b)*r/12,y3=(float)Math.sin(b)*r/12;
                    lensVertex(matrix,lens,mesh.relief,eye,x0,y0,texture);lensVertex(matrix,lens,mesh.relief,eye,x1,y1,texture);lensVertex(matrix,lens,mesh.relief,eye,x2,y2,texture);
                    if(r>0){lensVertex(matrix,lens,mesh.relief,eye,x0,y0,texture);lensVertex(matrix,lens,mesh.relief,eye,x2,y2,texture);lensVertex(matrix,lens,mesh.relief,eye,x3,y3,texture);}
                }
                BufferUploader.drawWithShader(BUFFER.end());
            }
            if(magnified&&(!optical||texture>=0)){
                RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.depthMask(false);RenderSystem.setShader(GameRenderer::getPositionColorShader);
                BUFFER.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
                // No desktop background/mask geometry is projected into the scope. Hairline cross and range marks only.
                reticleRect(matrix,lens,mesh.relief,eye,-.004f,-.82f,.004f,.82f);
                reticleRect(matrix,lens,mesh.relief,eye,-.82f,-.004f,.82f,.004f);
                for(int k=1;k<=4;k++)reticleRect(matrix,lens,mesh.relief,eye,-.055f,-k*.14f-.003f,.055f,-k*.14f+.003f);
                BufferUploader.drawWithShader(BUFFER.end());
            }
            ResourceLocation reticleTexture=null;
            if(attachment!=null&&attachment.getItem() instanceof IAttachment a)reticleTexture=TimelessAPI.getClientAttachmentIndex(a.getAttachmentId(attachment)).map(i->i.getModelTexture()).orElse(null);
            if(!magnified&&reticleTexture!=null&&!mesh.reticle.isEmpty()){
                RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.depthMask(false);
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);RenderSystem.setShaderTexture(0,reticleTexture);
                BUFFER.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX_COLOR);
                for(var v:mesh.reticle){float alpha=optical?OpticMath.transmission(v.x(),v.y(),eye,lens.radius(),mesh.relief):1;
                    BUFFER.vertex(matrix,lens.x()+v.x()*lens.radius(),lens.y()+v.y()*lens.radius(),lens.z()+.0001f).uv(v.u(),v.v()).color(1f,1f,1f,alpha).endVertex();}
                BufferUploader.drawWithShader(BUFFER.end());
            }
        }finally{
            RenderSystem.setShaderTexture(0,previousTexture);if(shader!=null)RenderSystem.setShader(()->shader);
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);RenderSystem.depthMask(depthWrite);RenderSystem.blendFuncSeparate(srcRGB,dstRGB,srcAlpha,dstAlpha);
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
        }
    }
    private static void reticleRect(Matrix4f m,VRGeometry.Lens lens,float relief,Vector3f eye,float x0,float y0,float x1,float y1){
        float[][] points={{x0,y0},{x1,y0},{x1,y1},{x0,y0},{x1,y1},{x0,y1}};
        for(var p:points)BUFFER.vertex(m,lens.x()+p[0]*lens.radius(),lens.y()+p[1]*lens.radius(),lens.z()+.00015f).color(0f,0f,0f,OpticMath.transmission(p[0],p[1],eye,lens.radius(),relief)).endVertex();
    }
    private static void lensVertex(Matrix4f matrix,VRGeometry.Lens lens,float relief,Vector3f eye,float x,float y,int texture){
        float brightness=texture>=0?OpticMath.transmission(x,y,eye,lens.radius(),relief):0;
        var vertex=BUFFER.vertex(matrix,lens.x()+x*lens.radius(),lens.y()+y*lens.radius(),lens.z());if(texture>=0){
            vertex.uv(OpticMath.textureU(x),OpticMath.textureV(y));}vertex.color(brightness,brightness,brightness,1f).endVertex();
    }
}
