package com.tacz.guns.compat.jyvr;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Tick-only continuous OBB collision. Cached broad phase and a hard narrow-phase budget. */
public final class MagazineCollision {
    public static final int MAX_CHECKS=2048;
    private static int lastChecks;
    private static boolean lastLimited;
    public static int lastChecks(){return lastChecks;}
    public static boolean lastLimited(){return lastLimited;}
    public record Box(Vector3f center,Vector3f half,Vector3f x,Vector3f y,Vector3f z) {
        public static Box of(Matrix4f matrix,Vector3f lo,Vector3f hi) {
            Vector3f x=matrix.transformDirection(new Vector3f(1,0,0)),y=matrix.transformDirection(new Vector3f(0,1,0)),z=matrix.transformDirection(new Vector3f(0,0,1));
            Vector3f half=new Vector3f(hi).sub(lo).mul(.5f).mul(x.length(),y.length(),z.length());
            return new Box(matrix.transformPosition(new Vector3f(lo).add(hi).mul(.5f)),half,x.normalize(),y.normalize(),z.normalize());
        }
        public Box transform(Vector3f p,Quaternionf q){return new Box(q.transform(new Vector3f(center)).add(p),half,q.transform(new Vector3f(x)),q.transform(new Vector3f(y)),q.transform(new Vector3f(z)));}
        private float radius(float a,float b,float c){return Math.abs(x.x*a+x.y*b+x.z*c)*half.x+Math.abs(y.x*a+y.y*b+y.z*c)*half.y+Math.abs(z.x*a+z.y*b+z.z*c)*half.z;}
    }
    public record Pose(Vector3f position,Quaternionf rotation,boolean blocked) {}
    private record Hit(float fraction,Vector3f normal) {}
    /** Immutable after construction; never rebuild this during eye rendering. */
    public static final class World {
        private final Node root;
        public World(List<Box> boxes){root=boxes.isEmpty()?null:new Node(new ArrayList<>(boxes));}
        private List<Box> nearby(Vector3f from,Vector3f to,float radius){
            List<Box> out=new ArrayList<>();if(root!=null)root.query(Math.min(from.x,to.x)-radius,Math.min(from.y,to.y)-radius,Math.min(from.z,to.z)-radius,Math.max(from.x,to.x)+radius,Math.max(from.y,to.y)+radius,Math.max(from.z,to.z)+radius,out);return out;
        }
        private Vector3f outside(Vector3f p,float radius){
            if(root==null)return new Vector3f(p);
            Vector3f best=new Vector3f(p);float distance=Float.POSITIVE_INFINITY;
            for(int a=0;a<3;a++)for(float edge:new float[]{root.lo.get(a)-radius-.001f,root.hi.get(a)+radius+.001f}){
                float d=Math.abs(edge-p.get(a));if(d<distance){distance=d;best.set(p).setComponent(a,edge);}
            }return best;
        }
    }
    private static final class Node {
        final Vector3f lo=new Vector3f(Float.POSITIVE_INFINITY),hi=new Vector3f(Float.NEGATIVE_INFINITY);
        Node left,right;List<Box> leaf;
        Node(List<Box> boxes){
            for(Box b:boxes){float x=b.radius(1,0,0),y=b.radius(0,1,0),z=b.radius(0,0,1);lo.min(new Vector3f(b.center).sub(x,y,z));hi.max(new Vector3f(b.center).add(x,y,z));}
            if(boxes.size()<=8){leaf=List.copyOf(boxes);return;}
            Vector3f extent=new Vector3f(hi).sub(lo);int axis=extent.x>extent.y?(extent.x>extent.z?0:2):(extent.y>extent.z?1:2);
            boxes.sort(Comparator.comparingDouble(b->b.center.get(axis)));int mid=boxes.size()/2;
            left=new Node(new ArrayList<>(boxes.subList(0,mid)));right=new Node(new ArrayList<>(boxes.subList(mid,boxes.size())));
        }
        void query(float x0,float y0,float z0,float x1,float y1,float z1,List<Box> out){
            if(hi.x<x0||lo.x>x1||hi.y<y0||lo.y>y1||hi.z<z0||lo.z>z1)return;
            if(leaf!=null){out.addAll(leaf);return;}left.query(x0,y0,z0,x1,y1,z1,out);right.query(x0,y0,z0,x1,y1,z1,out);
        }
    }
    private static final class Budget {
        int checks;boolean limited;
        boolean take(){if(checks>=MAX_CHECKS){limited=true;return false;}checks++;return true;}
    }
    // Primitive SAT axes: no 15-vector collection per box pair.
    private static Hit sat(Box a,Box b,Vector3f delta,boolean sweep){
        float dx=a.center.x-b.center.x,dy=a.center.y-b.center.y,dz=a.center.z-b.center.z;
        float enter=0,exit=1,depth=Float.POSITIVE_INFINITY,nx=0,ny=0,nz=0;boolean normal=false;
        for(int i=0;i<15;i++){
            float x,y,z;
            if(i<6){Vector3f v=i<3?(i==0?a.x:i==1?a.y:a.z):(i==3?b.x:i==4?b.y:b.z);x=v.x;y=v.y;z=v.z;}
            else{int n=i-6;Vector3f u=n/3==0?a.x:n/3==1?a.y:a.z,v=n%3==0?b.x:n%3==1?b.y:b.z;
                x=u.y*v.z-u.z*v.y;y=u.z*v.x-u.x*v.z;z=u.x*v.y-u.y*v.x;
                float length=(float)Math.sqrt(x*x+y*y+z*z);if(length<1e-5f)continue;x/=length;y/=length;z/=length;}
            float radius=a.radius(x,y,z)+b.radius(x,y,z),d=dx*x+dy*y+dz*z;
            if(!sweep){float overlap=radius-Math.abs(d);if(overlap<=.00001f)return null;
                if(overlap<depth){depth=overlap;float sign=d>=0?1:-1;nx=x*sign;ny=y*sign;nz=z*sign;normal=true;}}
            else{float speed=delta.x*x+delta.y*y+delta.z*z;
                if(Math.abs(speed)<1e-9f){if(Math.abs(d)>=radius)return null;continue;}
                float t0=(-radius-d)/speed,t1=(radius-d)/speed,first=Math.min(t0,t1),last=Math.max(t0,t1);
                if(first>=enter){enter=first;float sign=speed>0?-1:1;nx=x*sign;ny=y*sign;nz=z*sign;normal=true;}
                exit=Math.min(exit,last);if(enter>exit)return null;}
        }
        if(!normal || (sweep && (enter<0||enter>1||exit<=0)))return null;
        return new Hit(sweep?enter:depth+.0002f,new Vector3f(nx,ny,nz));
    }
    public static Vector3f penetration(Box a,Box b){Hit h=sat(a,b,null,false);return h==null?null:h.normal.mul(h.fraction);}
    public static boolean clear(List<Box> moving,List<Box> body,Vector3f p,Quaternionf q){
        for(Box a:moving){Box t=a.transform(p,q);for(Box b:body)if(penetration(t,b)!=null)return false;}return true;
    }
    private static boolean clear(List<Box> moving,List<Box> nearby,Vector3f p,Quaternionf q,Budget budget){
        for(Box a:moving){Box t=a.transform(p,q);for(Box b:nearby){if(!budget.take()||penetration(t,b)!=null)return false;}}return true;
    }
    private static boolean rotationClear(List<Box> moving,List<Box> nearby,Vector3f p,Quaternionf from,Quaternionf to,Budget budget){
        if(!clear(moving,nearby,p,to,budget))return false;
        float angle=2*(float)Math.acos(Math.min(1,Math.abs(from.dot(to))));if(angle<1e-5f)return true;
        Quaternionf mid=new Quaternionf(from).slerp(to,.5f);
        for(Box a:moving){Box m=a.transform(p,mid);float margin=(a.center.length()+a.half.length())*(float)Math.sin(angle*.5f);
            Box envelope=new Box(m.center,new Vector3f(m.half).add(margin,margin,margin),m.x,m.y,m.z);
            for(Box b:nearby){if(!budget.take()||penetration(envelope,b)!=null)return false;}}
        return true;
    }
    public static Pose move(List<Box> moving,List<Box> body,Pose previous,Vector3f desired,Quaternionf requested){return move(moving,new World(body),previous,desired,requested);}
    public static Pose move(List<Box> moving,World world,Pose previous,Vector3f desired,Quaternionf requested){
        Budget budget=new Budget();try{return solve(moving,world,previous,desired,requested,budget);}finally{lastChecks=budget.checks;lastLimited=budget.limited;}
    }
    private static Pose solve(List<Box> moving,World world,Pose previous,Vector3f desired,Quaternionf requested,Budget budget){
        if(!desired.isFinite()||!requested.isFinite()||requested.lengthSquared()<1e-10f)return previous;
        Quaternionf target=new Quaternionf(requested).normalize(),q=previous==null?new Quaternionf(target):new Quaternionf(previous.rotation);
        Vector3f p=previous==null?new Vector3f(desired):new Vector3f(previous.position);
        float radius=0;for(Box a:moving)radius=Math.max(radius,a.center.length()+a.half.length());
        boolean blocked=false;
        // New spawn/changed receiver: bounded depenetration, then a guaranteed exterior pose.
        for(int attempt=0;attempt<5;attempt++){
            List<Box> nearby=world.nearby(p,p,radius);Vector3f correction=null;
            for(Box a:moving){Box t=a.transform(p,q);for(Box b:nearby){
                if(!budget.take())return new Pose(previous==null?world.outside(p,radius):new Vector3f(previous.position),q,true);
                Vector3f d=penetration(t,b);if(d!=null&&(correction==null||d.lengthSquared()>correction.lengthSquared()))correction=d;}}
            if(correction==null)break;blocked=true;if(attempt==4){p=world.outside(p,radius);break;}p.add(correction);
        }
        Vector3f delta=new Vector3f(desired).sub(p);if(delta.length()>.35f)delta.normalize().mul(.35f);
        List<Box> nearby=world.nearby(p,new Vector3f(p).add(delta),radius*1.25f);
        float angle=2*(float)Math.acos(Math.min(1,Math.abs(q.dot(target))));
        if(angle>.349066f){target.set(q).slerp(requested,.349066f/angle).normalize();angle=.349066f;}
        Quaternionf start=new Quaternionf(q);int steps=Math.max(1,Math.min(4,(int)Math.ceil(angle/.087267f)));
        if(angle>1e-5f)for(int n=1;n<=steps;n++){
            Quaternionf next=new Quaternionf(start).slerp(target,(float)n/steps);
            if(!rotationClear(moving,nearby,p,q,next,budget)){blocked=true;break;}q=next;}
        if(budget.limited)return new Pose(p,q,true);
        for(int slide=0;slide<3 && delta.lengthSquared()>1e-12f;slide++){
            nearby=world.nearby(p,new Vector3f(p).add(delta),radius);
            Hit nearest=null;
            for(Box a:moving){Box t=a.transform(p,q);for(Box b:nearby){
                if(!budget.take())return new Pose(p,q,true);
                Hit h=sat(t,b,delta,true);if(h!=null&&(nearest==null||h.fraction<nearest.fraction))nearest=h;}}
            if(nearest==null){p.add(delta);break;}
            blocked=true;float fraction=Math.max(0,nearest.fraction-.0002f/Math.max(.0002f,delta.length()));
            p.fma(fraction,delta);delta.mul(1-fraction);float into=delta.dot(nearest.normal);if(into<0)delta.fma(-into,nearest.normal);
        }
        return new Pose(p,q,blocked);
    }
}
