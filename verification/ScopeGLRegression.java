import com.tacz.guns.compat.jyvr.ScopeWorldCopy;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
public class ScopeGLRegression{
 static native boolean context();
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static void box(int x,int y,int w,int h,float r,float g,float b){glEnable(GL_SCISSOR_TEST);glScissor(x,y,w,h);glClearColor(r,g,b,1);glClear(GL_COLOR_BUFFER_BIT);}
 public static void main(String[] args){
  System.load(args[0]);check(context());GL.createCapabilities();System.out.println(glGetString(GL_RENDERER));
  var snapshot=new ScopeWorldCopy();
  for(int samples:new int[]{1,4}){
   int f=glGenFramebuffers(),r=glGenRenderbuffers();glBindFramebuffer(GL_FRAMEBUFFER,f);glBindRenderbuffer(GL_RENDERBUFFER,r);glRenderbufferStorageMultisample(GL_RENDERBUFFER,samples,GL_RGBA8,64,64);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_RENDERBUFFER,r);
   check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE);
   box(0,0,64,64,0,1,0);box(0,32,64,32,0,0,1);glScissor(0,0,1,1);
   check(snapshot.capture(f,GL_COLOR_ATTACHMENT0,4,4,56,56));check(glIsEnabled(GL_SCISSOR_TEST)&&glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==f);
   box(0,26,64,13,0,0,0);for(int i=-4;i<=4;i++){int n=(int)Math.sqrt(16-i*i);box(32-n,32+i,2*n+1,1,0,i<0?1:0,i<0?0:1);}
   var pixels=org.lwjgl.BufferUtils.createByteBuffer(56*56*4);glBindTexture(GL_TEXTURE_2D,snapshot.texture());glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
   for(int i=0;i<56*56;i++){boolean sky=i/56>=28;check(pixels.get(i*4)==0&&(pixels.get(i*4+1)&255)==(sky?0:255)&&(pixels.get(i*4+2)&255)==(sky?255:0));}
   check(glGetError()==GL_NO_ERROR);glDeleteRenderbuffers(r);glDeleteFramebuffers(f);
  }snapshot.close();System.out.println("PASS: 6272 exact RGB pixels; scissor, viewport crop, multisample resolve, post-capture black band isolation.");
 }
}
