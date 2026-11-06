import com.tacz.guns.compat.jyvr.VRHandRenderGate;
/** Regression for Vivecraft 1.20.1's actual first/third-person hand transform branches. */
public class HandRenderRegression {
    static int count;
    static void check(boolean result,String name){count++;if(!result)throw new AssertionError(name);}
    public static void main(String[] args){
        String[] hands={"FIRST_PERSON_RIGHT_HAND","FIRST_PERSON_LEFT_HAND","THIRD_PERSON_RIGHT_HAND","THIRD_PERSON_LEFT_HAND"};
        String[] views={"LEFT","RIGHT","CENTER","THIRD","CAMERA","SCOPER","SCOPEL"};
        for(String hand:hands)for(String view:views){
            check(VRHandRenderGate.accepts(true,true,true,false,hand,view),"Vivecraft local main hand "+hand+" / "+view);
            check(!VRHandRenderGate.accepts(false,true,true,false,hand,view),"desktop unchanged");
            check(!VRHandRenderGate.accepts(true,false,true,false,hand,view),"other players excluded");
            check(!VRHandRenderGate.accepts(true,true,false,false,hand,view),"offhand/other gun excluded");
            check(!VRHandRenderGate.accepts(true,true,true,true,hand,view),"nested rendering excluded");
        }
        for(String context:new String[]{"NONE","HEAD","GUI","GROUND","FIXED"})check(!VRHandRenderGate.accepts(true,true,true,false,context,"LEFT"),"non-hand context "+context);
        for(String view:new String[]{"VANILLA","GUI","MIRROR"})for(String hand:hands)check(!VRHandRenderGate.accepts(true,true,true,false,hand,view),"non-world pass "+view);
        System.out.println("PASS: "+count+" hand-render routing assertions. Includes Vivecraft third-person item transforms and reversed-hand transforms; not a headset rendering test.");
    }
}
