import com.google.gson.GsonBuilder;
import com.tacz.guns.api.client.animation.*;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.pojo.model.*;
import com.tacz.guns.client.resource.pojo.animation.bedrock.*;
import com.tacz.guns.client.resource.serialize.*;
import com.tacz.guns.compat.jyvr.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.*;

public class VRInteractionRegression {
    public static void main(String[] args)throws Exception{
        var gson=new GsonBuilder().registerTypeAdapter(CubesItem.class,new CubesItem.Deserializer())
            .registerTypeAdapter(AnimationKeyframes.class,new AnimationKeyframesSerializer())
            .registerTypeAdapter(SoundEffectKeyframes.class,new SoundEffectKeyframesSerializer()).create();
        Path root=Path.of(args[0]);int n=0;
        for(String id:new String[]{"m700","m870","spas_12","db_long","db_short","m1014","springfield1873"}){
            var model=new BedrockGunModel(gson.fromJson(Files.readString(root.resolve("geo_models/gun/"+id+"_geo.json")),BedrockModelPOJO.class),BedrockVersion.NEW);
            var mechanics=WeaponMechanics.of("tacz:"+id,"CLOSED_BOLT","MAGAZINE");
            var path=model.getVRPartPath(mechanics.actionBone());
            var shape=VRRig.shapeGeometry(path,new Matrix4f(),0);
            if(!shape.present())throw new AssertionError(id+" has animation but no interaction mesh");
            if(!shape.center().isFinite())throw new AssertionError(id+" invalid contact position");
            var file=gson.fromJson(Files.readString(root.resolve("animations/"+id+".animation.json")),BedrockAnimationFile.class);
            var controller=new AnimationController(Animations.createAnimationFromBedrock(file),model);
            var plan=VRActionAnimation.build(model,controller,path,mechanics.gated());
            if(!plan.available())throw new AssertionError(id+" missing movement");
            if(mechanics.returnStroke()){
                var part=path.get(path.size()-1);var restore=plan.apply(1,false);
                float distance=new Vector3f(part.offsetX,part.offsetY,part.offsetZ).length();restore.run();
                if(distance<.01f)throw new AssertionError(id+" no rearward travel");
                restore=plan.apply(0,true);
                if(new Vector3f(part.offsetX,part.offsetY,part.offsetZ).length()>.005f)throw new AssertionError(id+" return clip does not close");
                restore.run();
            }
            if(mechanics.loose()){
                var ports=VRLoadingPorts.ports("tacz:"+id);if(ports.isEmpty())throw new AssertionError(id+" no explicit feed mouth");
                for(var p:ports){
                    if(model.getVRPartPath(p.bone())==null||!VRLoadingPorts.modelPoint(p).isFinite())throw new AssertionError(id+" invalid port anchor");
                    if(id.startsWith("db_")&&p.z()<2)throw new AssertionError("double barrel port in middle of barrel");
                }
            }
            System.out.println("PASS "+id+": "+shape.boxes().size()+" interaction mesh boxes; feed anchor and action checked");n++;
        }
        System.out.println("PASS VRInteractionRegression: "+n+" reported gun fixtures. Geometry/animation only, not headset or server integration.");
    }
}
