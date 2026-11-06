import com.tacz.guns.compat.jyvr.*;
import org.joml.*;
import java.util.*;

public class SpatialRegression {
    static int checks;
    static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    static void close(Vector3f a,Vector3f b,String m){check(a.distance(b)<.00002f,m+": "+a+" != "+b);}
    public static void main(String[] args){
         java.util.Random random=new java.util.Random(7713);
        for(int i=0;i<300;i++){
            Quaternionf eye=new Quaternionf().rotationXYZ(random.nextFloat()*3,random.nextFloat()*6,random.nextFloat()*2);
            Quaternionf hand=new Quaternionf().rotationXYZ(random.nextFloat()*3,random.nextFloat()*6,random.nextFloat());
            Vector3f handWorld=new Vector3f(.2f,1.1f,-.5f),eyeWorld=new Vector3f(.03f,1.7f,.04f);
            float ws=new float[]{.5f,1f,2f}[i%3];
            Matrix4f handOnly=new Matrix4f().translation(new Vector3f(handWorld).sub(eyeWorld)).rotate(hand).scale(ws);
            Vector3f point=new Vector3f(.01f,-.04f,-.13f);
            Matrix4f view=VRSpatialMath.viewAtHand(new Matrix4f().rotate(eye),handOnly);
            Vector3f recovered=eye.transform(view.transformPosition(new Vector3f(point))).add(eyeWorld);
            close(recovered,hand.transform(new Vector3f(point).mul(ws)).add(handWorld),"head turn must not move the gun in world space");
            Vector3f offLocal=new Vector3f(.2f,-.15f,.03f);
            Vector3f offWorld=hand.transform(new Vector3f(offLocal).mul(ws));
            close(VRSpatialMath.relative(offWorld,hand,ws),offLocal,"controller touch space independent of head and world scale");
            Matrix4f grip=new Matrix4f().translation(.03f,1.2f,.06f).rotateY(.4f);
            Vector3f offset=new Vector3f(.02f,-.01f,0);
            Matrix4f model=VRSpatialMath.modelToHand(grip,new Vector3f(.6f),offset,new Quaternionf());
            close(model.transformPosition(grip.getTranslation(new Vector3f())),offset,"pack scale keeps grip anchored");
            check(java.lang.Math.abs(model.transformDirection(new Vector3f(1,0,0)).length()-.6)<1e-6,"author's .6 scale applied once");
            Quaternionf off=new Quaternionf().rotationXYZ(.4f,.8f,-.3f),hold=new Quaternionf(hand).conjugate().mul(off);
            Quaternionf carry=new Quaternionf(hold).conjugate();
            check(java.lang.Math.abs(hold.mul(carry).w-1)<1e-6,"detached magazine preserves installed orientation");
        }
        for(int i=0;i<100;i++){
            Matrix4f cube=new Matrix4f().translation(.01f,0,.01f).rotateY(i*.031f).rotateZ(i*.015f);
            Vector3f lo=new Vector3f(-.03f,-.12f,-.04f),hi=new Vector3f(.03f,.02f,.04f);
            var pieces=SocketCarving.subtract(cube,lo,hi,new Matrix4f(),new Vector3f(-.014f,-.4f,-.02f),new Vector3f(.014f,.004f,.02f));
            for(var p:pieces)for(int n=0;n<8;n++){
                Vector3f v=new Vector3f((n&1)==0?p.lo().x:p.hi().x,(n&2)==0?p.lo().y:p.hi().y,(n&4)==0?p.lo().z:p.hi().z);
                check(v.x>=lo.x-1e-6 && v.x<=hi.x+1e-6 && v.y>=lo.y-1e-6 && v.y<=hi.y+1e-6 && v.z>=lo.z-1e-6 && v.z<=hi.z+1e-6,"carving never adds invisible solid outside rotated mesh");
            }
        }
        MagazineGuide guide=new MagazineGuide();Vector3f tip=new Vector3f(0,.06f,0);
        var requested=new Vector3f(.015f,-.195f,0);var rotated=new Quaternionf().rotateZ(.2f);
        var t=guide.target(true,new Matrix4f(),.1f,tip,requested,rotated);
        check(guide.engaged(),"near-mouth approach captures guide");
        close(t.position(),new Vector3f(0,new Quaternionf(rotated).transform(new Vector3f(tip)).add(requested).y-.06f,0),"guide centers without consuming push depth");
        check(t.rotation().equals(new Quaternionf()),"guide corrects tilt and twist");
        guide.target(true,new Matrix4f(),.1f,tip,new Vector3f(.012f,-.12f,0),new Quaternionf());
        check(guide.engaged(),"guide persists while pushing");
        guide.target(true,new Matrix4f(),.1f,tip,new Vector3f(.08f,-.12f,0),new Quaternionf());
        check(!guide.engaged(),"pulling sideways releases guide");
        guide.target(true,new Matrix4f(),.1f,tip,requested,new Quaternionf().rotateY((float)java.lang.Math.PI));
        check(!guide.engaged(),"backwards magazine cannot snap in");
        guide.target(false,new Matrix4f(),.1f,tip,requested,rotated);
        check(!guide.engaged(),"incompatible magazine cannot engage");
        // The guide never bypasses the solver: an additional closed wall must still stop it.
        var moving=List.of(MagazineCollision.Box.of(new Matrix4f(),new Vector3f(-.01f,-.06f,-.015f),new Vector3f(.01f,.06f,.015f)));
        var wall=List.of(MagazineCollision.Box.of(new Matrix4f(),new Vector3f(-.1f,-.11f,-.1f),new Vector3f(.1f,-.10f,.1f)));
        var stopped=MagazineCollision.move(moving,wall,new MagazineCollision.Pose(new Vector3f(0,-.20f,0),new Quaternionf(),false),new Vector3f(0,-.06f,0),new Quaternionf());
        check(stopped.position().y<-.169f && stopped.blocked(),"guided insertion cannot bypass a solid obstruction");
        System.out.println("PASS: "+checks+" spatial/scale/carving/guide checks. No headset integration covered.");
    }
}
