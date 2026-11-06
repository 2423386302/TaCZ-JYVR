import com.tacz.guns.compat.jyvr.*;
import org.joml.*;
import java.nio.file.*;
import java.util.*;

public class MeshInsertionRegression {
 record Cube(Matrix4f matrix,Vector3f lo,Vector3f hi) {}
 static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static Cube read(String line){String[] s=line.split(" ");float[] m=new float[16];for(int i=0;i<16;i++)m[i]=Float.parseFloat(s[i]);return new Cube(new Matrix4f().set(m),new Vector3f(Float.parseFloat(s[16]),Float.parseFloat(s[17]),Float.parseFloat(s[18])),new Vector3f(Float.parseFloat(s[19]),Float.parseFloat(s[20]),Float.parseFloat(s[21])));}
 static Vector3f[] bounds(Cube c,Matrix4f space){Vector3f lo=new Vector3f(Float.POSITIVE_INFINITY),hi=new Vector3f(Float.NEGATIVE_INFINITY);Matrix4f m=new Matrix4f(space).mul(c.matrix());for(int n=0;n<8;n++){Vector3f p=m.transformPosition(new Vector3f((n&1)==0?c.lo.x:c.hi.x,(n&2)==0?c.lo.y:c.hi.y,(n&4)==0?c.lo.z:c.hi.z));lo.min(p);hi.max(p);}return new Vector3f[]{lo,hi};}
 public static void main(String[] args)throws Exception{
  var lines=Files.readAllLines(Path.of(args[0]));int at=0,guns=0;
  while(at<lines.size()){
   String[] header=lines.get(at++).split(" ");String name=header[1];Vector3f axis=new Vector3f(Float.parseFloat(header[2]),Float.parseFloat(header[3]),Float.parseFloat(header[4]));int nb=Integer.parseInt(header[5]),nm=Integer.parseInt(header[6]);
   List<Cube> body=new ArrayList<>(),mag=new ArrayList<>();for(int i=0;i<nb;i++)body.add(read(lines.get(at++)));for(int i=0;i<nm;i++)mag.add(read(lines.get(at++)));
   Vector3f a=new Vector3f(Float.POSITIVE_INFINITY),b=new Vector3f(Float.NEGATIVE_INFINITY);float end=Float.NEGATIVE_INFINITY;
   for(Cube c:mag){var v=bounds(c,new Matrix4f());a.min(v[0]);b.max(v[1]);for(int n=0;n<8;n++){Vector3f p=c.matrix.transformPosition(new Vector3f((n&1)==0?c.lo.x:c.hi.x,(n&2)==0?c.lo.y:c.hi.y,(n&4)==0?c.lo.z:c.hi.z));end=java.lang.Math.max(end,p.dot(axis));}}
   Vector3f center=new Vector3f(a).add(b).mul(.5f),tip=new Vector3f(center).fma(end-center.dot(axis),axis);
   Matrix4f frame=new Matrix4f().translation(tip).rotate(new Quaternionf().rotationTo(new Vector3f(0,1,0),axis)),inverse=new Matrix4f(frame).invert();
   Vector3f lo=new Vector3f(Float.POSITIVE_INFINITY),hi=new Vector3f(Float.NEGATIVE_INFINITY);for(Cube c:mag){var v=bounds(c,inverse);lo.min(v[0]);hi.max(v[1]);}
   Vector3f proxyLo=new Vector3f(lo),proxyHi=new Vector3f(hi);lo.add(-.004f,0,-.004f);hi.add(.004f,.004f,.004f);
   float depth=java.lang.Math.max(.02f,java.lang.Math.min(.2f,-lo.y));float entrance=0;boolean found=false;
   for(Cube c:body){var v=bounds(c,inverse);if(v[1].x>lo.x && v[0].x<hi.x && v[1].z>lo.z && v[0].z<hi.z && v[1].y>lo.y && v[0].y<hi.y){entrance=java.lang.Math.min(entrance,v[0].y);found=true;}}
   if(found)depth=java.lang.Math.max(.02f,java.lang.Math.min(depth,-entrance));
   List<MagazineCollision.Box> solids=new ArrayList<>();
   for(Cube c:body)for(var p:SocketCarving.subtract(c.matrix,c.lo,c.hi,frame,new Vector3f(lo.x,lo.y-.30f,lo.z),hi)){
    check(p.lo().x>=c.lo.x-1e-6 && p.lo().y>=c.lo.y-1e-6 && p.lo().z>=c.lo.z-1e-6 && p.hi().x<=c.hi.x+1e-6 && p.hi().y<=c.hi.y+1e-6 && p.hi().z<=c.hi.z+1e-6,name+" clipped solid stays inside original mesh cube");
    solids.add(MagazineCollision.Box.of(c.matrix,p.lo(),p.hi()));
   }
   var moving=List.of(MagazineCollision.Box.of(new Matrix4f().translation(new Vector3f(center).negate()).mul(frame),proxyLo,proxyHi));
   check(MagazineCollision.clear(moving,solids,center,new Quaternionf()),name+" fully seated proxy must fit its own socket");
   var world=new MagazineCollision.World(solids);var state=new MagazineCollision.Pose(new Vector3f(center).fma(-depth-.05f,axis),new Quaternionf(),false);InsertionGate gate=new InsertionGate();MagazineGuide guide=new MagazineGuide();boolean seated=false;
   for(int step=0;step<45;step++){
    float axial=java.lang.Math.min(0,-depth-.05f+step*.008f);
    Vector3f desired=new Vector3f(center).fma(axial,axis);
    var target=guide.target(true,frame,depth,new Vector3f(tip).sub(center),desired,new Quaternionf());
    state=MagazineCollision.move(moving,world,state,target.position(),target.rotation());
    Vector3f error=inverse.transformPosition(new Vector3f(tip).sub(center).rotate(state.rotation()).add(state.position()));
    seated|=gate.update(true,error.y,java.lang.Math.hypot(error.x,error.z),1,depth);
    check(MagazineCollision.lastChecks()<=MagazineCollision.MAX_CHECKS,name+" bounded work");
   }
   check(seated,name+" push through real mesh receiver reaches insertion gate");
   check(state.position().distance(center)<.001f,name+" magazine reaches installed position");
   System.out.println("PASS "+name+": "+nb+" body cubes, "+nm+" magazine cubes");guns++;
  }
  System.out.println("PASS: "+guns+" bundled mesh fixtures; "+checks+" checks. Rest geometry only, not Forge/headset rendering.");
 }
}
