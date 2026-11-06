import com.tacz.guns.compat.jyvr.OpticMath;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;
import java.util.Random;

public class OpticRegression {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static void near(float a,float b,String why){check(Math.abs(a-b)<.00002f,why);}
    static OpticMath.Vertex v(float x,float y){return new OpticMath.Vertex(x,y,.25f+x*.02f,.5f+y*.03f);}
    public static void main(String[] args){
        var huge=OpticMath.clip(List.of(v(-100,-100),v(100,-100),v(0,100)));check(huge.size()>=64,"bound the desktop mask");
        float area=0;for(int i=0;i<huge.size();i++){var a=huge.get(i);var b=huge.get((i+1)%huge.size());area+=a.x()*b.y()-a.y()*b.x();}
        check(Math.abs(area/2-(float)Math.PI)<.006f,"aperture area");
        check(OpticMath.clip(List.of(v(2,2),v(3,2),v(2,3))).isEmpty(),"outside discarded");
        var small=List.of(v(-.1f,-.1f),v(.1f,-.1f),v(0,.1f));check(OpticMath.clip(small).equals(small),"inside preserved");
        Random r=new Random(3401);
        for(int i=0;i<1200;i++)for(var p:OpticMath.clip(List.of(v(r.nextFloat()*12-6,r.nextFloat()*12-6),v(r.nextFloat()*12-6,r.nextFloat()*12-6),v(r.nextFloat()*12-6,r.nextFloat()*12-6)))){
            check(p.x()*p.x()+p.y()*p.y()<=1.00001f,"no vertex outside lens");near(p.u(),.25f+p.x()*.02f,"UV X interpolation");near(p.v(),.5f+p.y()*.03f,"UV Y interpolation");
        }
        var eye=new Vector3f(0,0,.2f);
        near(OpticMath.transmission(0,0,eye,.03f,.2f),1,"aligned visible");
        near(OpticMath.transmission(0,0,new Vector3f(.07f,0,.2f),.03f,.2f),0,"other eye blocked");
        near(OpticMath.transmission(0,0,new Vector3f(0,0,-.01f),.03f,.2f),0,"front blocked");
        near(OpticMath.transmission(0,0,new Vector3f(0,0,2),.03f,.2f),0,"too far blocked");
        near(OpticMath.transmission(0,0,new Vector3f(Float.NaN,0,.2f),.03f,.2f),0,"invalid tracking blocked");
        for(int i=0;i<1000;i++){float t=OpticMath.transmission(r.nextFloat()*2-1,r.nextFloat()*2-1,new Vector3f(.015f,-.003f,.2f),.03f,.2f);check(Float.isFinite(t)&&t>=0&&t<=1,"pupil bounded");}
        var p=OpticMath.project(new Vector3f(.3f,.6f,-2.8f),.2f,.4f,eye,0,0,0,.03f);
        near(p.x(),2f/3,"ray X");near(p.y(),4f/3,"ray Y");near(p.u(),.2f,"original UV");near(p.v(),.4f,"original UV");
        check(OpticMath.project(eye,0,0,eye,0,0,0,.03f)==null,"zero ray rejected");
        near(OpticMath.textureU(-1),1,"right edge");near(OpticMath.textureU(1),0,"left edge");near(OpticMath.textureV(-1),1,"top");near(OpticMath.textureV(1),0,"bottom");
        near(OpticMath.focal(8,.2f,.03f),8*.2f/.03f,"physical magnification");
        var anchor=new Matrix4f().translation(.1f,.2f,.3f).rotateY(.7f);var view=new Matrix4f().translation(0,1.6f,.4f);
        var actual=OpticMath.attachmentView(anchor,view).getTranslation(new Vector3f());var expected=anchor.transformPosition(new Vector3f(0,.1f,.4f));
        near(actual.x,expected.x,"attachment X");near(actual.y,expected.y,"attachment origin correction");near(actual.z,expected.z,"attachment Z");
        for(int bits=0;bits<32;bits++)check(OpticMath.hideHud((bits&1)!=0,(bits&2)!=0,(bits&4)!=0,(bits&8)!=0,(bits&16)!=0)==(bits==23),"HUD restores and preserves menus");
        System.out.println("PASS OpticRegression: "+checks+" assertions. No game launch or GL draw.");
    }
}
