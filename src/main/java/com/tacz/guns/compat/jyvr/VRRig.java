package com.tacz.guns.compat.jyvr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Shared mesh transforms keep detached parts, contact and animation at the same scale. */
public final class VRRig {
    public static Matrix4f restPartMatrix(BedrockPart part) {
        return new Matrix4f().translation(part.x/16f,part.y/16f,part.z/16f)
            .rotateZ(part.getInitRotZ()).rotateY(part.getInitRotY()).rotateX(part.getInitRotX());
    }
    private static void neutral(BedrockPart part) {
        part.offsetX=part.offsetY=part.offsetZ=0;
        part.xRot=part.getInitRotX();part.yRot=part.getInitRotY();part.zRot=part.getInitRotZ();
        part.additionalQuaternion.identity();part.xScale=part.yScale=part.zScale=1;
    }
    private static void neutralTree(BedrockPart part) {
        neutral(part);for(BedrockPart child:part.children)neutralTree(child);
    }
    public static void stabilize(BedrockGunModel model) {
        var grip=model.getThirdPersonHandOriginPath();
        if(grip!=null)for(BedrockPart part:grip)neutral(part);
        var magazine=model.getVRMagazinePath();
        if(magazine!=null && !magazine.isEmpty()){
            for(BedrockPart part:magazine)neutral(part);
            neutralTree(magazine.get(magazine.size()-1));
        }
        // Bolt/slide firing animation is retained; manual offsets are applied after this step.
    }
    public record Box(Matrix4f matrix, Vector3f min, Vector3f max) {
        boolean touches(Vec3 point, float margin) {
            if (Math.abs(matrix.determinant()) < 1e-10) return false;
            Vector3f q = new Matrix4f(matrix).invert().transformPosition(point.toVector3f());
            if (ContactMath.inside(q.x,q.y,q.z,min.x,min.y,min.z,max.x,max.y,max.z,0)) return true;
            Vector3f closest=new Vector3f(Math.max(min.x,Math.min(max.x,q.x)),
                Math.max(min.y,Math.min(max.y,q.y)),Math.max(min.z,Math.min(max.z,q.z)));
            matrix.transformPosition(closest);
            return closest.distanceSquared(point.toVector3f()) <= margin*margin;
        }
    }
    public record Shape(List<Box> boxes, Vector3f center) {
        public boolean touches(Vec3 point) { return boxes.stream().anyMatch(b -> b.touches(point,.008f)); }
        public boolean present() { return !boxes.isEmpty(); }
    }
    public static int magazineLevel(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun g)) return 0;
        var id=g.getAttachmentId(gun,AttachmentType.EXTENDED_MAG);
        if (com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(id)) id=g.getBuiltInAttachmentId(gun,AttachmentType.EXTENDED_MAG);
        return TimelessAPI.getClientAttachmentIndex(id).map(i -> i.getData().getExtendedMagLevel()).orElse(0);
    }
    public static Shape shape(List<BedrockPart> path, Matrix4f base, ItemStack gun) {
        return shapeGeometry(path,base,magazineLevel(gun));
    }
    public static Shape shapeGeometry(List<BedrockPart> path,Matrix4f base,int magazineLevel){
        if (path == null || path.isEmpty()) return new Shape(List.of(),new Vector3f());
        List<Box> boxes = new ArrayList<>();
        Matrix4f frame=new Matrix4f(base);for(var part:path)frame.mul(restPartMatrix(part));
        collect(path.get(path.size()-1),frame,magazineLevel,boxes,0);
        Vector3f lo=new Vector3f(Float.POSITIVE_INFINITY),hi=new Vector3f(Float.NEGATIVE_INFINITY);
        for(Box b:boxes) for(int n=0;n<8;n++) {
            Vector3f p=new Vector3f((n&1)==0?b.min.x:b.max.x,(n&2)==0?b.min.y:b.max.y,(n&4)==0?b.min.z:b.max.z);
            b.matrix.transformPosition(p);lo.min(p);hi.max(p);
        }
        return new Shape(boxes,boxes.isEmpty()?new Vector3f():lo.add(hi).mul(.5f));
    }
    private static void collect(BedrockPart part, Matrix4f matrix, int level, List<Box> boxes, int depth) {
        collect(part,matrix,level,boxes,depth,null);
    }
    private static void collect(BedrockPart part, Matrix4f matrix, int level, List<Box> boxes, int depth, BedrockPart excluded) {
        if(part==excluded)return;
        if(depth>64 || !matrix.isFinite() || Math.abs(matrix.determinant())<1e-10f) return;
        String name=part.name==null?"":part.name;
        if(name.equals("additional_magazine") || (name.startsWith("bullet")&&!name.equals("bullet_and_bolt")) || name.equals("lefthand") || name.equals("righthand") || name.endsWith("hand_pos")) return;
        if(name.equals("mag_standard") && level!=0) return;
        for(int n=1;n<=3;n++) if(name.equals("mag_extended_"+n) && level!=n) return;
        for(BedrockCube cube:part.cubes) {
            Vector3f lo,hi;
            if(cube instanceof BedrockCubeBox c) { lo=new Vector3f(c.minX-c.inflate,c.minY-c.inflate,c.minZ-c.inflate);hi=new Vector3f(c.maxX+c.inflate,c.maxY+c.inflate,c.maxZ+c.inflate); }
            else if(cube instanceof BedrockCubePerFace c) { lo=new Vector3f(c.minX-c.inflate,c.minY-c.inflate,c.minZ-c.inflate);hi=new Vector3f(c.maxX+c.inflate,c.maxY+c.inflate,c.maxZ+c.inflate); }
            else continue;
            boxes.add(new Box(new Matrix4f(matrix),new Vector3f(lo).min(hi).div(16),new Vector3f(lo).max(hi).div(16)));
        }
        for(BedrockPart child:part.children) {
            collect(child,new Matrix4f(matrix).mul(restPartMatrix(child)),level,boxes,depth+1,excluded);
        }
    }
    public static Shape magazine(BedrockGunModel model, ItemStack gun) {
        return shape(model.getVRMagazinePath(),VRClient.modelToHand(model,gun),gun);
    }
    public static Shape bolt(BedrockGunModel model, ItemStack gun) {
        return shape(model.getVRActionPath(gun),VRClient.modelToHand(model,gun),gun);
    }
    /** Feed end and insertion axis in the same hand space as the rendered magazine. */
    public record MagazineMouth(Vector3f point, Vector3f axis, float depth) {}
    public static MagazineMouth magazineMouth(BedrockGunModel model, ItemStack gun, Shape shape) {
        if (!shape.present()) return null;
        Vector3f axis=VRClient.modelToHand(model,gun).transformDirection(new Vector3f(0,-1,0));
        if (!axis.isFinite() || axis.lengthSquared()<1e-10f) return null;
        axis.normalize();
        float end=Float.NEGATIVE_INFINITY,start=Float.POSITIVE_INFINITY;
        for (Box b:shape.boxes()) for (int n=0;n<8;n++) {
            Vector3f corner=new Vector3f((n&1)==0?b.min.x:b.max.x,(n&2)==0?b.min.y:b.max.y,(n&4)==0?b.min.z:b.max.z);
            float projection=b.matrix.transformPosition(corner).dot(axis);
            end=Math.max(end,projection);start=Math.min(start,projection);
        }
        Vector3f tip=new Vector3f(shape.center()).fma(end-shape.center().dot(axis),axis);
        return new MagazineMouth(tip,axis,Math.max(.02f,Math.min(.12f,end-start)));
    }
    public record Socket(Matrix4f frame,Vector3f lo,Vector3f hi,float depth) {
        public Vector3f error(Vector3f tip){return new Matrix4f(frame).invert().transformPosition(new Vector3f(tip));}
        public Vector3f axis(){return frame.transformDirection(new Vector3f(0,1,0));}
    }
    public record Geometry(List<MagazineCollision.Box> body,List<Box> debugBody,Socket socket,MagazineCollision.World world) {
        public Geometry(List<MagazineCollision.Box> body,List<Box> debugBody,Socket socket){this(body,debugBody,socket,new MagazineCollision.World(body));}
    }
    private static Vector3f[] bounds(Box box,Matrix4f space) {
        Vector3f lo=new Vector3f(Float.POSITIVE_INFINITY),hi=new Vector3f(Float.NEGATIVE_INFINITY);
        Matrix4f m=new Matrix4f(space).mul(box.matrix());
        for(int n=0;n<8;n++){
            Vector3f p=m.transformPosition(new Vector3f((n&1)==0?box.min.x:box.max.x,(n&2)==0?box.min.y:box.max.y,(n&4)==0?box.min.z:box.max.z));lo.min(p);hi.max(p);
        }return new Vector3f[]{lo,hi};
    }
    /** Cut a narrow magazine-shaped channel through receiver cubes; do not disable receiver collision. */
    public static Geometry geometry(BedrockGunModel model,ItemStack gun,Shape mag,boolean open) {
        List<Box> source=new ArrayList<>();var root=model.getRootNode();var path=model.getVRMagazinePath();
        if(root!=null){
            collect(root,VRClient.modelToHand(model,gun).mul(restPartMatrix(root)),magazineLevel(gun),source,0,path==null?null:path.get(path.size()-1));}
        MagazineMouth mouth=magazineMouth(model,gun,mag);
        if(mouth==null)return new Geometry(source.stream().map(b->MagazineCollision.Box.of(b.matrix,b.min,b.max)).toList(),source,null);
        Matrix4f frame=new Matrix4f().translation(mouth.point()).rotate(new org.joml.Quaternionf().rotationTo(new Vector3f(0,1,0),mouth.axis()));
        Matrix4f inverse=new Matrix4f(frame).invert();Vector3f lo=new Vector3f(Float.POSITIVE_INFINITY),hi=new Vector3f(Float.NEGATIVE_INFINITY);
        for(Box b:mag.boxes()){var bounds=bounds(b,inverse);lo.min(bounds[0]);hi.max(bounds[1]);}
        lo.x-=.004f;lo.z-=.004f;hi.x+=.004f;hi.z+=.004f;hi.y+=.004f;
        float depth=Math.max(.02f,Math.min(.20f,-lo.y));
        float entrance=0;boolean found=false;
        for(Box b:source){var v=bounds(b,inverse);if(v[1].x>lo.x && v[0].x<hi.x && v[1].z>lo.z && v[0].z<hi.z && v[1].y>lo.y && v[0].y<hi.y){entrance=Math.min(entrance,v[0].y);found=true;}}
        if(found)depth=Math.max(.02f,Math.min(depth,-entrance));
        Socket socket=new Socket(frame,new Vector3f(lo),new Vector3f(hi),depth);
        List<Box> solid=new ArrayList<>();
        if(!open) {solid.addAll(source);solid.addAll(mag.boxes());}
        else {
            Vector3f cutLo=new Vector3f(lo.x,lo.y-.30f,lo.z),cutHi=new Vector3f(hi);
            for(Box b:source)for(var slab:SocketCarving.subtract(b.matrix(),b.min(),b.max(),frame,cutLo,cutHi))
                solid.add(new Box(new Matrix4f(b.matrix()),slab.lo(),slab.hi()));
        }
        return new Geometry(solid.stream().map(b->MagazineCollision.Box.of(b.matrix,b.min,b.max)).toList(),solid,socket);
    }
    public static List<MagazineCollision.Box> carriedBoxes(Shape mag){
        Matrix4f center=new Matrix4f().translation(new Vector3f(mag.center()).negate());
        return mag.boxes().stream().map(b->MagazineCollision.Box.of(new Matrix4f(center).mul(b.matrix),b.min,b.max)).toList();
    }
    /** One conservative collider aligned with the insertion channel, enclosing the actual magazine mesh. */
    public static List<MagazineCollision.Box> carriedProxy(Shape mag,MagazineMouth mouth){
        if(!mag.present() || mouth==null)return List.of();
        Matrix4f frame=new Matrix4f().translation(mag.center()).rotate(new org.joml.Quaternionf().rotationTo(new Vector3f(0,1,0),mouth.axis()));
        Matrix4f inverse=new Matrix4f(frame).invert();Vector3f lo=new Vector3f(Float.POSITIVE_INFINITY),hi=new Vector3f(Float.NEGATIVE_INFINITY);
        for(Box b:mag.boxes()){var v=bounds(b,inverse);lo.min(v[0]);hi.max(v[1]);}
        Matrix4f local=new Matrix4f().translation(new Vector3f(mag.center()).negate()).mul(frame);
        return List.of(MagazineCollision.Box.of(local,lo,hi));
    }
    public static Matrix4f carry(BedrockGunModel model, ItemStack gun) {
        Shape shape=magazine(model,gun);
        return new Matrix4f().translate(new Vector3f(shape.center).negate())
            .mul(VRClient.modelToHand(model,gun)).mul(VRClient.restPathMatrix(model.getVRMagazinePath()));
    }
    public static Runnable move(BedrockGunModel model, ItemStack gun, List<BedrockPart> path, Vec3 handDelta) {
        if(path==null || path.isEmpty()) return () -> {};
        BedrockPart part=path.get(path.size()-1);
        Matrix4f parent=VRClient.modelToHand(model,gun).mul(VRClient.restPathMatrix(path.subList(0,path.size()-1)));
        if(Math.abs(parent.determinant())<1e-10) return () -> {};
        Vector3f d=parent.invert().transformDirection(handDelta.toVector3f());
        float x=part.offsetX,y=part.offsetY,z=part.offsetZ;
        part.offsetX+=d.x;part.offsetY+=d.y;part.offsetZ+=d.z;
        return () -> { part.offsetX=x;part.offsetY=y;part.offsetZ=z; };
    }
}
