import com.tacz.guns.compat.jyvr.InteractionGate;
import com.tacz.guns.compat.jyvr.MagazineMath;
import java.util.Random;
import com.tacz.guns.compat.jyvr.ContactMath;

/** Run without Forge: java -ea CoreRegression. These are state/accounting tests, NOT VR integration tests. */
public final class CoreRegression {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static void rejected(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(ContactMath.inside(0,0,0,-.02,-.05,-.01,.02,.05,.01,.008),"inside magazine");
        check(!ContactMath.inside(.04,0,0,-.02,-.05,-.01,.02,.05,.01,.008),"2cm outside mesh must not grab");
        check(!ContactMath.inside(.12,0,0,-.02,-.05,-.01,.02,.05,.01,.008),"old broad radius must not grab");
        check(!ContactMath.inside(Double.NaN,0,0,-1,-1,-1,1,1,1,.008),"invalid contact rejected");
        check(ContactMath.rackComplete(.05,.01),"rearward rack completes");
        check(!ContactMath.rackComplete(-.05,.01),"forward pull rejected");
        check(!ContactMath.rackComplete(.02,0),"small wiggle rejected");
        check(!ContactMath.rackComplete(.05,.06),"sideways pull rejected");
        check(!ContactMath.rackComplete(Double.NaN,0),"invalid rack rejected");
        for(int rounds=0;rounds<=500;rounds++) for(boolean loaded:new boolean[]{false,true}) {
            var c=MagazineMath.rack(rounds,loaded);
            check(c.magazine()+(c.loaded()?1:0)==rounds+(loaded?1:0),"rack conserves ammunition");
            check(MagazineMath.rack(c.magazine(),c.loaded()).equals(c),"repeated rack cannot duplicate rounds");
            check(c.loaded()==(loaded||rounds>0),"chamber requires an available round");
        }
        var gate = new InteractionGate();
        check(!gate.update(true,true,true,false,.15),"grip away from well must not detach");
        check(!gate.update(true,true,true,true,0),"grip alone must not detach");
        check(!gate.update(true,true,false,true,.05),"small movement must not detach");
        check(gate.update(true,true,false,false,.12),"one outward pull must detach");
        for(int i=0;i<30;i++) check(!gate.update(true,true,false,false,.2),"held grip must not repeat detach");
        gate.update(true,false,false,false,0);
        check(gate.stage()==InteractionGate.Stage.IDLE,"release resets transaction");
        gate.update(true,true,true,true,0);
        gate.update(false,true,false,true,.2);
        check(gate.stage()==InteractionGate.Stage.IDLE,"GUI/death/weapon switch cancels pending pull");
        check(!gate.update(true,true,false,true,.2),"tracking reacquisition needs new press");
        gate.update(true,true,true,true,0);
        check(!gate.update(true,true,false,false,Double.NaN),"invalid tracking never detaches");
        gate.reset(); gate.update(true,true,true,true,0);
        for(int i=0;i<61;i++) gate.update(true,true,false,true,0);
        check(gate.stage()==InteractionGate.Stage.IDLE,"pull expires");
        check(InteractionGate.aiming(false,.30,.95,.32),"raise aligned gun enters aim");
        check(InteractionGate.aiming(true,.36,.86,.32),"exit hysteresis prevents flicker");
        check(!InteractionGate.aiming(false,.36,.86,.32),"same pose cannot enter from inactive");
        check(!InteractionGate.aiming(true,.40,.99,.32),"lowering gun exits");
        check(!InteractionGate.aiming(true,Double.NaN,1,.32),"NaN pose cannot aim");
        rejected(()->MagazineMath.insert(1,20,30),"cannot overwrite live rounds");
        rejected(()->MagazineMath.insert(0,31,30),"cannot truncate oversized magazine");
        rejected(()->MagazineMath.insert(0,-1,30),"cannot insert negative rounds");
        Random random=new Random(1337);
        for(int i=0;i<10000;i++) {
            int capacity=1+random.nextInt(500), gun=random.nextInt(capacity+1), loose=random.nextInt(2000);
            int original=gun+loose;
            int oldMag=gun; gun=0; // detach conserves chamber-excluded magazine rounds
            int newMag=MagazineMath.take(loose,capacity); loose-=newMag;
            var result=MagazineMath.insert(gun,newMag,capacity);
            check(result.gun()+result.magazine()+oldMag+loose==original,"reload creates or destroys ammo");
            check(loose>=0 && result.gun()<=capacity,"reload exceeds available/capacity");
        }
        System.out.println("PASS: "+checks+" core state/ammunition checks. No Forge or headset integration covered.");
    }
}
