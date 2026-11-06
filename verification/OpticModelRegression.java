import com.google.gson.GsonBuilder;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.pojo.model.*;
import java.nio.file.*;

/** Actual production model loader and mesh builder, without starting Minecraft or drawing. */
public class OpticModelRegression {
    public static void main(String[] args)throws Exception{
        var gson=new GsonBuilder().registerTypeAdapter(CubesItem.class,new CubesItem.Deserializer()).create();int models=0,lenses=0,vertices=0;
        try(var paths=Files.list(Path.of(args[0]))){
            for(var file:paths.filter(p->p.getFileName().toString().startsWith("scope_")&&p.toString().endsWith("_geo.json")).sorted().toList()){
                var pojo=gson.fromJson(Files.readString(file),BedrockModelPOJO.class);var model=new BedrockAttachmentModel(pojo,BedrockVersion.NEW);models++;int count=0;
                for(int i=0;i<16;i++){
                    var mesh=model.vrOptic(i);if(mesh==null)break;
                    if(!(mesh.lens().radius()>0&&mesh.relief()>0)||mesh!=model.vrOptic(i)||mesh.reticle().size()%3!=0)throw new AssertionError(file+" invalid/cached mesh");
                    for(var v:mesh.reticle()){
                        if(!Float.isFinite(v.x())||!Float.isFinite(v.y())||!Float.isFinite(v.u())||!Float.isFinite(v.v())||v.x()*v.x()+v.y()*v.y()>1.00001f)throw new AssertionError("reticle escaped lens "+file);
                        vertices++;
                    }
                    System.out.printf("%s lens %d: radius %.5f, relief %.5f, reticle vertices %d%n",file.getFileName(),i,mesh.lens().radius(),mesh.relief(),mesh.reticle().size());
                    if(file.getFileName().toString().equals("scope_standard_8x_geo.json")&&mesh.reticle().isEmpty())throw new AssertionError("8x reticle missing");
                    lenses++;count++;
                }
                if(count==0)throw new AssertionError("no aperture "+file);
            }
        }
        if(models<5||lenses<5||vertices<100)throw new AssertionError("insufficient fixture coverage");
        System.out.println("PASS OpticModelRegression: "+models+" actual models, "+lenses+" lenses, "+vertices+" bounded vertices. No game launch or GL draw.");
    }
}
