import com.tacz.guns.compat.jyvr.MagazineCollision;
import org.joml.*;
import java.util.List;
public class CollisionRegression {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static MagazineCollision.Box box(float x,float y,float z,float hx,float hy,float hz){return MagazineCollision.Box.of(new Matrix4f().translation(x,y,z),new Vector3f(-hx,-hy,-hz),new Vector3f(hx,hy,hz));}
 public static void main(String[] args){
  var moving=List.of(box(0,0,0,.01f,.025f,.01f));var wall=List.of(box(0,0,0,.0001f,.2f,.2f));
  var start=new MagazineCollision.Pose(new Vector3f(-.12f,0,0),new Quaternionf(),false);
  var stopped=MagazineCollision.move(moving,wall,start,new Vector3f(.12f,0,0),new Quaternionf());
  check(stopped.position().x<-.01009f,"cannot tunnel through 0.2mm wall");check(stopped.blocked(),"contact reported");
  check(MagazineCollision.clear(moving,wall,stopped.position(),stopped.rotation()),"resolved pose is outside receiver");
  var slid=MagazineCollision.move(moving,wall,stopped,new Vector3f(.05f,.1f,0),new Quaternionf());
  check(slid.position().x<-.01009f && slid.position().y>.09f,"slide along receiver");
  var retreat=MagazineCollision.move(moving,wall,slid,new Vector3f(-.12f,.1f,0),new Quaternionf());
  check(retreat.position().x<-.119f,"can withdraw after contact");
  var spawned=MagazineCollision.move(moving,wall,null,new Vector3f(),new Quaternionf());
  check(MagazineCollision.clear(moving,wall,spawned.position(),spawned.rotation()),"depenetrates initial overlap");
  var receiver=List.of(box(-.022f,-.06f,0,.007f,.08f,.04f),box(.022f,-.06f,0,.007f,.08f,.04f),box(0,.04f,0,.04f,.01f,.04f));
  var entry=new MagazineCollision.Pose(new Vector3f(0,-.21f,0),new Quaternionf(),false);
  var inserted=MagazineCollision.move(moving,receiver,entry,new Vector3f(0,-.03f,0),new Quaternionf());
  check(inserted.position().y>-.031f,"narrow open channel accepts aligned magazine");
  check(MagazineCollision.clear(moving,receiver,inserted.position(),inserted.rotation()),"seated position remains collision-free");
  var cap=MagazineCollision.move(moving,receiver,inserted,new Vector3f(0,.09f,0),new Quaternionf());
  check(cap.position().y<.0051f,"receiver cap prevents over-insertion");
  var tilted=MagazineCollision.move(moving,receiver,inserted,inserted.position(),new Quaternionf().rotateZ(1.2f));
  check(MagazineCollision.clear(moving,receiver,tilted.position(),tilted.rotation()),"rotation constrained in narrow channel");
  for(int i=0;i<80;i++){
   float angle=i*.071f;var q=new Quaternionf().rotateZ(angle);
   var rotatedWall=List.of(MagazineCollision.Box.of(new Matrix4f().rotate(q),new Vector3f(-.0001f,-.2f,-.2f),new Vector3f(.0001f,.2f,.2f)));
   var from=q.transform(new Vector3f(-.12f,0,0));var to=q.transform(new Vector3f(.12f,0,0));
   var result=MagazineCollision.move(moving,rotatedWall,new MagazineCollision.Pose(from,q,false),to,q);
   check(new Quaternionf(q).conjugate().transform(new Vector3f(result.position())).x<-.01f,"rotated wall sweep "+i);
   check(MagazineCollision.clear(moving,rotatedWall,result.position(),result.rotation()),"rotated wall nonpenetration "+i);
  }
  System.out.println("PASS: "+checks+" collision checks. Synthetic geometry; no headset integration covered.");
 }
}
