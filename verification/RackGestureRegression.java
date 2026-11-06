import com.tacz.guns.compat.jyvr.*;
public class RackGestureRegression {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        for(float travel:new float[]{.055f,.075f}){
            RackGesture g=new RackGesture();
            check(!g.update(travel,0,travel,true,false),"rear is not chambered");
            check(!g.update(travel,0,travel,true,true),"release at rear remains open");
            check(g.position()==travel,"no fake spring closure");
            check(g.update(travel*.25f,.03f,travel,true,false),"close while gripping completes");
            check(!g.update(0,0,travel,true,true),"release cannot chamber a second round");
            g.reset();check(!g.update(travel*.4f,0,travel,true,false),"short stroke");
            check(!g.update(0,0,travel,true,true),"short stroke cannot load");
            g.reset();check(!g.update(travel,.2f,travel,true,false),"sideways cannot latch");
            check(!g.update(0,0,travel,true,false),"invalid trajectory cannot chamber");
            g.reset();g.update(travel,0,travel,false,false);
            check(g.update(travel,0,travel,false,true),"spring returned slide chambers on release");
        }
        var chamber=MagazineMath.rack(5,false);
        check(chamber.loaded()&&chamber.magazine()==4,"one round moved into chamber");
        var repeat=MagazineMath.rack(chamber.magazine(),chamber.loaded());
        check(repeat.loaded()&&repeat.magazine()==4,"retry does not consume twice");
        System.out.println("PASS RackGestureRegression: held closure, released open action, regrip, short/side strokes, single completion, ammo conservation.");
    }
}
