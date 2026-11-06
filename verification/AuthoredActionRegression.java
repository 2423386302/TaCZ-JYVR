import com.google.gson.GsonBuilder;
import com.tacz.guns.api.client.animation.*;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.pojo.model.*;
import com.tacz.guns.client.resource.pojo.animation.bedrock.*;
import com.tacz.guns.client.resource.serialize.*;
import com.tacz.guns.compat.jyvr.*;
import java.nio.file.*;
public class AuthoredActionRegression {
    public static void main(String[] args)throws Exception{
        var gson=new GsonBuilder().registerTypeAdapter(CubesItem.class,new CubesItem.Deserializer())
            .registerTypeAdapter(AnimationKeyframes.class,new AnimationKeyframesSerializer())
            .registerTypeAdapter(SoundEffectKeyframes.class,new SoundEffectKeyframesSerializer()).create();
        Path root=Path.of(args[0]);int count=0;var missing=new java.util.ArrayList<String>();
        var ids=new java.util.ArrayList<String>();
        try(var stream=Files.list(root.resolve("geo_models/gun"))){stream.filter(f->f.toString().endsWith("_geo.json")).sorted().forEach(f->ids.add(f.getFileName().toString().replace("_geo.json","")));}
        for(String id:ids){
            if(id.equals("minigun")||id.equals("rpg7"))continue;
            var model=new BedrockGunModel(gson.fromJson(Files.readString(root.resolve("geo_models/gun/"+id+"_geo.json")),BedrockModelPOJO.class),BedrockVersion.NEW);
            var file=gson.fromJson(Files.readString(root.resolve("animations/"+id+".animation.json")),BedrockAnimationFile.class);
            var controller=new AnimationController(Animations.createAnimationFromBedrock(file),model);
            var mechanics=WeaponMechanics.of("tacz:"+id,"CLOSED_BOLT","MAGAZINE");
            var path=model.getVRPartPath(mechanics.gated()?mechanics.gateBone():mechanics.actionBone());
            if(path==null)path=model.getVRBoltPath();
            var plan=VRActionAnimation.build(model,controller,path,mechanics.gated());
            if(!plan.available()){missing.add(id);continue;}
            if(mechanics.feed()==WeaponMechanics.Feed.BELT){
                var action=VRActionAnimation.build(model,controller,model.getVRPartPath(mechanics.actionBone()),false);
                if(!action.available())missing.add(id+" charging handle");
            }
            var restore=plan.apply(1,false);
            if(id.equals("ai_awp")){
                var group=model.getVRPartPath("bolt_group").get(model.getVRPartPath("bolt_group").size()-1);
                var handle=model.getVRPartPath("bolt_rotate").get(model.getVRPartPath("bolt_rotate").size()-1);
                if(Math.abs(group.additionalQuaternion.z)>.0001)throw new AssertionError("AWM entire group rotated");
                if(Math.abs(handle.additionalQuaternion.z)<.1)throw new AssertionError("AWM handle did not unlock");
                if(group.offsetZ<.1||group.offsetZ>.5)throw new AssertionError("AWM bolt displacement not pack units: "+group.offsetZ);
            }
            restore.run();
            for(var t:plan.tracks())if(Math.abs(t.part().offsetZ)>.0001||Math.abs(t.part().additionalQuaternion.w-1)>.0001)throw new AssertionError("state leaked: "+id);
            System.out.println(id+": "+plan.clip()+" / "+plan.tracks().stream().map(t->t.part().name).distinct().toList());count++;
        }
        if(!missing.isEmpty())throw new AssertionError("Missing authored tracks: "+missing);
        System.out.println("PASS AuthoredActionRegression: "+count+" real model/animation fixtures; AWM handle isolation and restore.");
    }
}
