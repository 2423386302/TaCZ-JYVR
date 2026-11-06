package com.tacz.guns.compat.jyvr;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import org.joml.Matrix4f;
public final class VRRecoil{
 private static long start;private static String id="";private static float strength;private static boolean unavailable;
 public static void fire(ItemStack s){
  if(!VRClient.active()||!(s.getItem() instanceof IGun g)||Minecraft.getInstance().player==null)return;
  id=g.getGunId(s).toString();String type=TimelessAPI.getClientGunIndex(g.getGunId(s)).map(i->i.getType()).orElse("rifle");
  strength=switch(type){case "pistol"->.31f;case "smg"->.23f;case "mg"->.64f;case "sniper"->.89f;case "shotgun"->.81f;case "rpg"->1f;default->.5f;};start=System.nanoTime();
  if(VRConfig.HAPTICS.get()&&!unavailable)try{String api="org.vivecraft.api.client.VRClientAPI";Class<?> part=Class.forName("org.vivecraft.api.data.VRBodyPart");VivecraftAccess.call(api,"triggerHapticPulse",VivecraftAccess.call(api,"instance",null),new Class<?>[]{part,float.class,float.class,float.class,float.class},part.getField("MAIN_HAND").get(null),.018f+.052f*strength,160f-65f*strength,strength,0f);}catch(ReflectiveOperationException|LinkageError e){unavailable=true;com.tacz.guns.GunMod.LOGGER.warn("VR controller recoil unavailable",e);}
 }
 public static Matrix4f offset(ItemStack s){Matrix4f m=new Matrix4f();float t=(System.nanoTime()-start)/1e9f;if(t<0||t>.17f||!VRConfig.KICK.get()||!(s.getItem() instanceof IGun g)||!id.equals(g.getGunId(s).toString()))return m;float a=Math.min(1,t/.012f)*Math.max(0,1-t/.17f);a*=a*strength;return m.translate(0,0,.022f*a).rotateX(.058f*a);}
}
