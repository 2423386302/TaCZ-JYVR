import com.tacz.guns.compat.jyvr.MagazineCollision;
import org.joml.*;
import java.util.*;
public class CollisionPerformance {
 static MagazineCollision.Box box(float x,float y,float z,float h){return MagazineCollision.Box.of(new Matrix4f().translation(x,y,z),new Vector3f(-h),new Vector3f(h));}
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  var moving=List.of(box(0,0,0,.01f));var body=new ArrayList<MagazineCollision.Box>();
  for(int i=0;i<3000;i++)body.add(box((i%30)*.015f,(i/30%10)*.015f,(i/300)*.015f,.004f));
  var world=new MagazineCollision.World(body);long[] times=new long[1000];int max=0,stops=0;
  for(int i=-100;i<times.length;i++){
   var previous=new MagazineCollision.Pose(new Vector3f(-.1f,.065f,.065f),new Quaternionf(),false);
   long start=System.nanoTime();var result=MagazineCollision.move(moving,world,previous,new Vector3f(.12f,.07f,.07f),new Quaternionf().rotateY(.15f));long elapsed=System.nanoTime()-start;
   if(i>=0){times[i]=elapsed;max=java.lang.Math.max(max,MagazineCollision.lastChecks());if(MagazineCollision.lastLimited())stops++;}
   check(result.position().isFinite(),"finite output");check(MagazineCollision.lastChecks()<=MagazineCollision.MAX_CHECKS,"hard budget");
  }
  Arrays.sort(times);System.out.printf(Locale.ROOT,"3000 cached boxes: median %.3f ms, p95 %.3f ms, p99 %.3f ms; max checks %d; budget stops %d/1000%n",times[500]/1e6,times[950]/1e6,times[990]/1e6,max,stops);
  body.clear();for(int i=0;i<20000;i++)body.add(box(10+i*.1f,0,0,.01f));body.add(box(0,0,0,.01f));
  var start=new MagazineCollision.Pose(new Vector3f(-.1f,0,0),new Quaternionf(),false);
  var result=MagazineCollision.move(moving,new MagazineCollision.World(body),start,new Vector3f(.1f,0,0),new Quaternionf());
  check(result.position().x<-.019f,"broad phase preserves near wall");check(MagazineCollision.lastChecks()<64,"far boxes pruned");System.out.println("20000 far boxes + wall: "+MagazineCollision.lastChecks()+" narrow-phase checks");
  body.clear();for(int i=0;i<10000;i++)body.add(box(0,0,0,.01f));
  result=MagazineCollision.move(moving,new MagazineCollision.World(body),start,new Vector3f(.1f,0,0),new Quaternionf());
  check(MagazineCollision.lastLimited(),"adversarial mesh hits limit");check(result.position().x<-.019f,"budget exhaustion preserves verified position");
  System.out.println("10000 overlapping boxes: capped at "+MagazineCollision.lastChecks()+" checks; safe stop. Synthetic CPU benchmark, not in-game FPS.");
 }
}
