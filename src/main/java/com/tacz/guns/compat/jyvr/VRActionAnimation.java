package com.tacz.guns.compat.jyvr;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.*;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import java.util.*;

/** Samples the installed gun pack's mechanical channels. Never animates the controller, camera or avatar hands. */
public final class VRActionAnimation {
    private static final Map<AnimationController,Map<String,Plan>> CACHE=new WeakHashMap<>();
    public record Track(BedrockPart part,ObjectAnimationChannel channel,AnimationListener listener) {}
    public record Plan(List<Track> tracks,float start,float peak,float end,String clip) {
        public boolean available(){return !tracks.isEmpty();}
        public Runnable apply(float progress,boolean returning){
            float p=Math.max(0,Math.min(1,progress));
            float time=returning?end-(end-peak)*p:start+(peak-start)*p;
            List<Runnable> restore=new ArrayList<>();
            for(Track t:tracks){
                BedrockPart b=t.part();float x=b.offsetX,y=b.offsetY,z=b.offsetZ,sx=b.xScale,sy=b.yScale,sz=b.zScale;
                Quaternionf q=new Quaternionf(b.additionalQuaternion);
                restore.add(()->{b.offsetX=x;b.offsetY=y;b.offsetZ=z;b.xScale=sx;b.yScale=sy;b.zScale=sz;b.additionalQuaternion.set(q);});
                float[] v=t.channel().getResult(time).clone();
                // Some empty-reload clips begin with a locked-back slide: there is no authored opening segment.
                if(!returning&&peak<=start+.0001f){
                    if(t.channel().type==ObjectAnimationChannel.ChannelType.ROTATION&&v.length==4){
                        Quaternionf a=new Quaternionf().slerp(new Quaternionf(v[0],v[1],v[2],v[3]),p);v=new float[]{a.x,a.y,a.z,a.w};
                    }else for(int i=0;i<v.length;i++)v[i]*=p;
                }
                t.listener().update(v,false);
            }
            return ()->{for(int i=restore.size()-1;i>=0;i--)restore.get(i).run();};
        }
    }
    private static final Plan EMPTY=new Plan(List.of(),0,0,0,"");
    public static Plan get(BedrockGunModel model,ItemStack gun,boolean gate){
        var display=TimelessAPI.getGunDisplay(gun);if(display.isEmpty())return EMPTY;
        var machine=display.get().getAnimationStateMachine();if(machine==null)return EMPTY;
        var controller=machine.getAnimationController();var m=VRWeapon.get(gun);
        String key=(gate?"gate:":"action:")+m.actionBone()+":"+m.gateBone();
        return CACHE.computeIfAbsent(controller,k->new HashMap<>()).computeIfAbsent(key,k->build(model,controller,gate?model.getVRPartPath(m.gateBone()):model.getVRActionPath(gun),gate));
    }
    public static Plan build(BedrockGunModel model,AnimationController controller,List<BedrockPart> path,boolean gate){
        if(path==null||path.isEmpty())return EMPTY;
        BedrockPart root=path.get(path.size()-1);
        String[] candidates=gate?new String[]{"reload_empty","reload_empty_all","reload_intro_empty","reload_tactical","reload_intro"}:
            new String[]{"bolt","reload_empty","reload_empty_all","reload_end","reload_intro_empty","shoot"};
        for(String name:candidates){
            var clip=controller.copyPrototype(name);if(clip==null)continue;
            List<Track> tracks=new ArrayList<>();ObjectAnimationChannel driver=null,rootDriver=null;float maxScore=.00001f,rootScore=.00001f;
            for(var entry:clip.getChannels().entrySet()){
                var nodePath=model.getVRPartPath(entry.getKey());
                if(nodePath==null)continue;
                boolean companion=(root.name.equals("aug_charge")&&entry.getKey().equals("aug_bolt"))
                    ||(root.name.equals("pull")&&entry.getKey().equals("G3_bolt"));
                if(!nodePath.contains(root)&&!companion)continue;
                // Shell/round visibility and animation-only hands are handled independently.
                String lower=entry.getKey().toLowerCase(Locale.ROOT);
                if(lower.contains("hand")&&!lower.contains("handle")||(lower.contains("bullet")&&!lower.equals("bullet_and_bolt"))||lower.contains("shell")||lower.contains("round")||lower.contains("loader")||lower.matches("head[0-9]*"))continue;
                for(var channel:entry.getValue()){
                    if(channel.type==ObjectAnimationChannel.ChannelType.SCALE||channel.content.keyframeTimeS.length==0)continue;
                    var listener=model.supplyListeners(entry.getKey(),channel.type);if(listener==null)continue;
                    tracks.add(new Track(nodePath.get(nodePath.size()-1),channel,listener));
                    float score=0;for(float t:channel.content.keyframeTimeS)score=Math.max(score,magnitude(channel,t));
                    // Use the root translation to time a bolt/pump; child handle rotation follows the same clip.
                    if(channel.type==ObjectAnimationChannel.ChannelType.TRANSLATION)score*=10;
                    if(nodePath.get(nodePath.size()-1)==root&&score>rootScore){rootScore=score;rootDriver=channel;}
                    if(score>maxScore){maxScore=score;driver=channel;}
                }
            }
            if(rootDriver!=null)driver=rootDriver;
            if(driver==null||tracks.isEmpty())continue;
            float peak=0,max=-1;for(float t:driver.content.keyframeTimeS){float v=magnitude(driver,t);if(v>max+.00001f){max=v;peak=t;}}
            float start=0,end=clip.getMaxEndTimeS();
            // Include the handle unlock before the bolt translation begins.
            for(Track t:tracks)for(float time:t.channel().content.keyframeTimeS){
                if(time<peak&&magnitude(t.channel(),time)<.00001f)start=Math.max(start,time);
            }
            start=Math.min(start,peak);
            for(Track t:tracks){
                float first=peak,last=peak;
                for(float time:t.channel().content.keyframeTimeS){if(time<=peak&&magnitude(t.channel(),time)<.00001f)first=time;if(time>=peak&&magnitude(t.channel(),time)>.00001f)last=time;}
                start=Math.min(start,first);end=Math.max(end,last);
            }
            return new Plan(List.copyOf(tracks),start,peak,end,name);
        }
        return EMPTY;
    }
    private static float magnitude(ObjectAnimationChannel c,float time){
        float[] v=c.getResult(time);if(c.type==ObjectAnimationChannel.ChannelType.ROTATION&&v.length==4)return 1-Math.abs(v[3]);
        float s=0;for(float x:v)s+=x*x;return (float)Math.sqrt(s);
    }
}
