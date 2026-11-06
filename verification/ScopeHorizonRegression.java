import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.datafixers.util.Pair;
import com.tacz.guns.compat.jyvr.ScopeWorldCopy;
import com.tacz.guns.compat.jyvr.VRScopeShader;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.resources.*;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Same captured world texture and pupil geometry for the old and new shader.
 * The source RGB contains NO black band. Uncovered fog retains alpha 0, geometry writes alpha 1.
 * Args: EGL JNI helper, MC client jar, TaCZ release jar, optional output directory. */
public class ScopeHorizonRegression {
 static final int W=512,H=320;static final double R=141,X=255,Y=158;
 static final float[] I={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
 static void require(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 static int target(){int f=glGenFramebuffers(),r=glGenRenderbuffers();glBindFramebuffer(GL_FRAMEBUFFER,f);glBindRenderbuffer(GL_RENDERBUFFER,r);glRenderbufferStorage(GL_RENDERBUFFER,GL_RGBA8,W,H);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_RENDERBUFFER,r);glViewport(0,0,W,H);require(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"FBO");return f;}
 static void rect(int x,int y,int w,int h,int r,int g,int b,int a){glEnable(GL_SCISSOR_TEST);glScissor(x,y,w,h);glClearColor(r/255f,g/255f,b/255f,a/255f);glClear(GL_COLOR_BUFFER_BIT);}
 static void plane(ShaderInstance shader,float[] points,int primitive,int r,int g,int b){
   int program=shader.getId();glUseProgram(program);
   float[] projection=new org.joml.Matrix4f().perspective((float)Math.toRadians(15),(float)W/H,.05f,1024f).get(new float[16]);
   glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,projection);glUniformMatrix4fv(glGetUniformLocation(program,"ModelViewMat"),false,I);
   glUniform4f(glGetUniformLocation(program,"ColorModulator"),r/255f,g/255f,b/255f,1);
   int vao=glGenVertexArrays(),buffer=glGenBuffers();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,buffer);glBufferData(GL_ARRAY_BUFFER,points,GL_STATIC_DRAW);
   int pos=glGetAttribLocation(program,"Position"),col=glGetAttribLocation(program,"Color");glVertexAttrib4f(col,1,1,1,1);glEnableVertexAttribArray(pos);glVertexAttribPointer(pos,3,GL_FLOAT,false,12,0L);glDrawArrays(primitive,0,points.length/3);
   glBindVertexArray(0);glDeleteBuffers(buffer);glDeleteVertexArrays(vao);glUseProgram(0);
 }
 static void input(ShaderInstance shader){
   // FogRenderer.setupColor + LevelRenderer's color clear; valid fog RGB with alpha zero.
   rect(0,0,W,H,171,201,244,0);glDisable(GL_SCISSOR_TEST);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
   // Upper sky disk: LevelRenderer.buildSkyDisc uses height 16, radius 512 and steps of 45 degrees.
   // Project the disk and a finite flat ground plane instead of drawing a band into the input.
   float[] sky=new float[30];sky[1]=16;int n=3;
   for(int degree=-180;degree<=180;degree+=45){double angle=Math.toRadians(degree);sky[n++]=(float)(512*Math.cos(angle));sky[n++]=16;sky[n++]=(float)(512*Math.sin(angle));}
   plane(shader,sky,GL_TRIANGLE_FAN,158,191,244);
   plane(shader,new float[]{-96,-1.62f,-96,96,-1.62f,-96,96,-1.62f,1,-96,-1.62f,1},GL_TRIANGLE_FAN,81,113,51);
   for(int i=0;i<3;i++){int x=148+i*38;rect(x,123,3,32,91,77,56,255);rect(x-10,149,24,31,163,165,158,255);rect(x-3,158,9,9,130,59,57,255);}
   rect(331,166,6,5,0,0,0,20);rect(361,176,8,9,144,106,72,128);glDisable(GL_SCISSOR_TEST);
 }
 static void backing(){
   rect(0,0,W,H,158,191,244,255);rect(0,0,W,136,81,113,51,255);
   for(int y=0;y<H;y++)for(int i=0;i<2;i++){double rad=R+(i==0?7:0),dy=y-Y;if(Math.abs(dy)>=rad)continue;int dx=(int)Math.sqrt(rad*rad-dy*dy),v=i==0?36:7;rect((int)X-dx,y,2*dx+1,1,v,v,v,255);}
   for(int y=144;y<=174;y++){int dx=(int)Math.sqrt(225-(y-159)*(y-159));rect(278-dx,y,2*dx+1,1,171,201,244,255);}
   glDisable(GL_SCISSOR_TEST);
 }
 static ByteBuffer pixels(){var b=BufferUtils.createByteBuffer(W*H*4);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,b);return b;}
 static int c(ByteBuffer b,int x,int y,int k){return Byte.toUnsignedInt(b.get(4*(y*W+x)+k));}
 static int rgb(ByteBuffer b,int x,int y){return c(b,x,y,0)<<16|c(b,x,y,1)<<8|c(b,x,y,2);}
 static void vert(float[] a,int k,double x,double y,float tint){float[] v={(float)(2*x/W-1),(float)(2*y/H-1),0,(float)(x/W),(float)(y/H),tint,tint,tint,1};System.arraycopy(v,0,a,k*9,9);}
 static ByteBuffer draw(ShaderInstance shader,int tex,float tint){
   backing();glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
   int program=shader.getId();glUseProgram(program);glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,I);glUniformMatrix4fv(glGetUniformLocation(program,"ModelViewMat"),false,I);
   glUniform4f(glGetUniformLocation(program,"ColorModulator"),1,1,1,1);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,tex);glUniform1i(glGetUniformLocation(program,"Sampler0"),0);
   int vao=glGenVertexArrays(),buf=glGenBuffers();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,buf);
   float[] a=new float[66*9];vert(a,0,X,Y,tint);for(int k=0;k<=64;k++){double angle=Math.PI*k/32;vert(a,k+1,X+R*Math.cos(angle),Y+R*Math.sin(angle),tint);}glBufferData(GL_ARRAY_BUFFER,a,GL_STATIC_DRAW);
   int off=0;for(String attr:new String[]{"Position","UV0","Color"}){int loc=glGetAttribLocation(program,attr);require(loc>=0,attr);int n=attr.equals("Position")?3:attr.equals("UV0")?2:4;glEnableVertexAttribArray(loc);glVertexAttribPointer(loc,n,GL_FLOAT,false,36,4L*off);off+=n;}
   glDrawArrays(GL_TRIANGLE_FAN,0,66);var out=pixels();glUseProgram(0);glBindVertexArray(0);glDeleteBuffers(buf);glDeleteVertexArrays(vao);return out;
 }
 static void save(Path dir,String name,ByteBuffer b)throws Exception{Files.createDirectories(dir);var im=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++)im.setRGB(x,H-1-y,rgb(b,x,y));ImageIO.write(im,"png",dir.resolve(name).toFile());}
 static void movingMob(ShaderInstance scene,ShaderInstance lens){
   // Bake the vanilla creeper model and submit its quads AFTER terrain into the scope target.
   var mesh=net.minecraft.client.model.CreeperModel.createBodyLayer(net.minecraft.client.model.geom.builders.CubeDeformation.NONE).bakeRoot();
   var b=new com.mojang.blaze3d.vertex.BufferBuilder(16384);var format=DefaultVertexFormat.NEW_ENTITY;
   var display=target();int tex=glGenTextures();glBindTexture(GL_TEXTURE_2D,tex);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,W,H,0,GL_RGBA,GL_UNSIGNED_BYTE,0L);
   glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
   var src=target();glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,tex,0);double last=-1;
   for(float x:new float[]{-.7f,.7f}){
     com.tacz.guns.compat.jyvr.ScopeSceneFrame.begin();glBindFramebuffer(GL_FRAMEBUFFER,src);input(scene);
     require(com.tacz.guns.compat.jyvr.ScopeSceneFrame.texture()<0,"cannot publish terrain before entities");
     var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.translate(x,.7,-14);pose.scale(1,-1,1);
     b.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,format);mesh.render(pose,b,15728880,0);
     var data=b.end();var vertices=data.vertexBuffer().order(java.nio.ByteOrder.nativeOrder());int count=data.drawState().vertexCount();float[] tris=new float[count/4*18];int n=0;
     for(int q=0;q<count;q+=4)for(int k:new int[]{0,1,2,2,3,0})for(int axis=0;axis<3;axis++)tris[n++]=vertices.getFloat((q+k)*format.getVertexSize()+axis*4);
     data.release();plane(scene,tris,GL_TRIANGLES,32,188,64);com.tacz.guns.compat.jyvr.ScopeSceneFrame.complete(tex);
     glBindFramebuffer(GL_FRAMEBUFFER,display);var eye=draw(lens,com.tacz.guns.compat.jyvr.ScopeSceneFrame.texture(),1);
     int hits=0;double sum=0;for(int yy=0;yy<H;yy++)for(int xx=0;xx<W;xx++)if(rgb(eye,xx,yy)==0x20bc40){hits++;sum+=xx;}
     require(hits>200,"creeper missing from magnified output");double center=sum/hits;if(last>=0)require(center>last+50,"stale mob position");last=center;
   }
   glDeleteTextures(tex);com.tacz.guns.compat.jyvr.ScopeSceneFrame.begin();System.out.println("PASS: vanilla creeper mesh visible at both positions in completed lens frames.");
 }
 public static void main(String[] a)throws Exception{
   System.load(a[0]);require(ScopeGLRegression.context(),"EGL");GL.createCapabilities();RenderSystem.initRenderThread();
   try(var mc=new FilePackResources("mc-1.20.1",Path.of(a[1]).toFile(),false);var mod=new FilePackResources("tacz-release",Path.of(a[2]).toFile(),false)){
     ResourceProvider res=id->{var pack=id.getNamespace().equals("tacz")?mod:mc;var io=pack.getResource(PackType.CLIENT_RESOURCES,id);return io==null?Optional.empty():Optional.of(new Resource(pack,io));};
     var reg=new ArrayList<Pair<ShaderInstance,Consumer<ShaderInstance>>>();VRScopeShader.register(new RegisterShadersEvent(res,reg));require(reg.size()==1,"registered shader");reg.forEach(s->s.getSecond().accept(s.getFirst()));
     var fixed=VRScopeShader.worldColor();var old=new ShaderInstance(res,new ResourceLocation("minecraft","position_tex_color"),DefaultVertexFormat.POSITION_TEX_COLOR);
     var sceneShader=new ShaderInstance(res,new ResourceLocation("minecraft","position_color"),DefaultVertexFormat.POSITION_COLOR);int src=target();input(sceneShader);var expect=pixels();var copy=new ScopeWorldCopy();require(copy.capture(src,GL_COLOR_ATTACHMENT0,0,0,W,H),"world capture");target();
     var before=draw(old,copy.texture(),1f);var after=draw(fixed,copy.texture(),1f);var half=draw(fixed,copy.texture(),.5f);
     int count=0,wrong=0;
     for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(Math.hypot(x+.5-X,y+.5-Y)<R-2){
       if(c(expect,x,y,3)<26&&rgb(before,x,y)!=rgb(expect,x,y))wrong++;
       if(c(expect,x,y,3)==255)require(rgb(before,x,y)==rgb(expect,x,y),"opaque control");
       require(rgb(after,x,y)==rgb(expect,x,y)&&c(after,x,y,3)==255,"RGB/coverage mismatch at "+x+","+y);
       for(int k=0;k<3;k++)require(Math.abs(c(half,x,y,k)-.5*c(expect,x,y,k))<=1,"pupil tint");count++;
     }
     require(wrong>10000&&rgb(before,241,165)==0x070707&&rgb(before,278,159)!=0x070707,"old band and hole");require(rgb(after,334,168)==0,"true black erased");require(glGetError()==GL_NO_ERROR,"GL error");
     if(a.length>3){save(Path.of(a[3]),"old.png",before);save(Path.of(a[3]),"new.png",after);}
     System.out.println("PASS: "+glGetString(GL_RENDERER)+"; old shader failed "+wrong+" horizon pixels; new shader matched all "+count+" aperture-interior pixels. Targets, true black and 50% exit-pupil shading preserved.");
     movingMob(sceneShader,fixed);sceneShader.close();fixed.close();old.close();copy.close();
   }
 }
}
