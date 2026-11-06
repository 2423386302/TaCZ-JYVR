import com.google.gson.GsonBuilder;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.pojo.model.*;
import com.tacz.guns.compat.jyvr.WeaponMechanics;
import java.nio.file.*;

/** Load real gun geometry and validate declared moving parts and loading ports. No GL rendering. */
public class WeaponModelRegression {
    public static void main(String[] args)throws Exception {
        var gson=new GsonBuilder().registerTypeAdapter(CubesItem.class,new CubesItem.Deserializer()).create();
        int count=0;
        try(var files=Files.list(Path.of(args[0]))){
            for(var file:files.filter(p->p.toString().endsWith("_geo.json")).sorted().toList()){
                String id=file.getFileName().toString().replace("_geo.json","");
                var pojo=gson.fromJson(Files.readString(file),BedrockModelPOJO.class);
                var model=new BedrockGunModel(pojo,BedrockVersion.NEW);
                var m=WeaponMechanics.of("tacz:"+id,"CLOSED_BOLT","MAGAZINE");
                if(m.motion()!=WeaponMechanics.Motion.NONE&&!id.equals("minigun")){
                    var action=model.getVRPartPath(m.actionBone());
                    if(action==null)action=model.getVRBoltPath();
                    if(action==null)throw new AssertionError(id+" missing action part");
                }
                if(m.loose()&&model.getVRPartPath(m.portBone())==null)throw new AssertionError(id+" missing loading port");
                if(m.gated()&&model.getVRPartPath(m.gateBone())==null)throw new AssertionError(id+" missing gate");
                if(!m.loose()&&!id.equals("minigun")&&model.getVRMagazinePath()==null)throw new AssertionError(id+" missing magazine");
                count++;
            }
        }
        if(count<50)throw new AssertionError("insufficient fixtures: "+count);
        System.out.println("PASS WeaponModelRegression: "+count+" real gun models; no VR runtime draw.");
    }
}
