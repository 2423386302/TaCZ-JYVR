import com.tacz.guns.compat.jyvr.*;
public class WeaponMechanicsRegression {
    static int checks;
    static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
    static WeaponMechanics get(String id){return WeaponMechanics.of("tacz:"+id,"CLOSED_BOLT","MAGAZINE");}
    public static void main(String[] args){
        check(get("m870").motion()==WeaponMechanics.Motion.PUMP);
        check(get("m870").loose());check(get("m1014").motion()==WeaponMechanics.Motion.CHARGE);
        check(get("m1014").loose());check(!get("m1014").returnStroke());
        check(get("glock_17").motion()==WeaponMechanics.Motion.SLIDE);check(!get("glock_17").retainsCases());
        check(get("ai_awp").motion()==WeaponMechanics.Motion.BOLT);check(get("ai_awp").retainsCases());
        check(get("taurus500").gated());check(get("db_long").gated());check(get("springfield1873").gated());
        check(get("m249").feed()==WeaponMechanics.Feed.BELT);check(!get("m249").loose());
        check(get("rpg7").motion()==WeaponMechanics.Motion.NONE);check(!get("rpg7").gated());
        check(WeaponMechanics.of("other:custom","MANUAL_ACTION","MANUAL").loose());
        check(WeaponMechanics.of("other:m870","CLOSED_BOLT","MAGAZINE").motion()!=WeaponMechanics.Motion.PUMP);
        for(var motion:WeaponMechanics.Motion.values())for(int k=0;k<100;k++){
            float travel=.035f+k*.0004f;
            check(!WeaponMechanics.completed(motion,false,0,0,travel));
            check(!WeaponMechanics.completed(motion,true,0,.08f,travel));
            check(!WeaponMechanics.completed(motion,true,Float.NaN,0,travel));
            check(WeaponMechanics.completed(motion,true,0,0,travel));
            if(motion==WeaponMechanics.Motion.BOLT||motion==WeaponMechanics.Motion.PUMP)check(!WeaponMechanics.completed(motion,true,travel,0,travel));
        }
        for(String id:new String[]{"m870","m1014","spas_12","kar98","taurus500","taurus943","rhino357","db_long","db_short","m320","lonetrail","springfield1873","rpg7"}){
            var m=get(id);check(m.loose());
            for(int cap=1;cap<=100;cap++)for(int ammo=0;ammo<=cap;ammo++){
                check(WeaponMechanics.mayLoad(m,true,ammo,cap)==(ammo<cap));
                check(WeaponMechanics.mayLoad(m,false,ammo,cap)==(!m.gated()&&ammo<cap));
                if(ammo<cap){var chamber=MagazineMath.rack(ammo+1,false);check(chamber.magazine()+(chamber.loaded()?1:0)==ammo+1);}
            }
        }
        System.out.println("PASS WeaponMechanicsRegression "+checks+" checks");
    }
}
