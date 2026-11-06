package com.tacz.guns.compat.jyvr;

import com.tacz.guns.GunMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Optional Vivecraft 1.3 API bridge. No client classes are loaded by dedicated servers. */
public final class VivecraftAccess {
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static boolean warned;
    public record Hand(Vec3 position, Vec3 direction, Quaternionf rotation) {}
    public record Pose(Hand head, Hand main, Hand off) {}
    public static Object call(String owner, String name, Object target, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        String key = owner + "#" + name + java.util.Arrays.toString(types);
        Method method = METHODS.get(key);
        if (method == null) {
            method = Class.forName(owner).getMethod(name, types);
            METHODS.put(key, method);
        }
        return method.invoke(target, args);
    }
    public static Object call(String owner, String name, Object target) throws ReflectiveOperationException {
        return call(owner, name, target, new Class<?>[0]);
    }
    private static Hand hand(Object pose, String method) throws ReflectiveOperationException {
        Object h = call("org.vivecraft.api.data.VRPose", method, pose);
        if (h == null) throw new ReflectiveOperationException("Missing controller pose");
        String api = "org.vivecraft.api.data.VRBodyPartData";
        Vec3 pos = (Vec3) call(api, "getPos", h);
        Vec3 dir = (Vec3) call(api, "getDir", h);
        Quaternionf rot = new Quaternionf((org.joml.Quaternionfc) call(api, "getRotation", h));
        if (!finite(pos) || !finite(dir) || dir.lengthSqr() < 0.5 || !Float.isFinite(rot.x) || !Float.isFinite(rot.y) || !Float.isFinite(rot.z) || !Float.isFinite(rot.w)) throw new ReflectiveOperationException("Invalid tracking pose");
        return new Hand(pos, dir.normalize(), rot);
    }
    public static Pose readPose(Object p) throws ReflectiveOperationException {
        return p == null ? null : new Pose(hand(p, "getHead"), hand(p, "getMainHand"), hand(p, "getOffHand"));
    }
    public static Pose serverPose(Player player) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("vivecraft")) return null;
        try {
            String api = "org.vivecraft.api.VRAPI";
            Object instance = call(api, "instance", null);
            if (!(Boolean) call(api, "isVRPlayer", instance, new Class<?>[]{Player.class}, player)) return null;
            Pose pose = readPose(call(api, "getVRPose", instance, new Class<?>[]{Player.class}, player));
            if (pose == null || pose.main.position.distanceTo(player.getEyePosition()) > 3 ||
                pose.off.position.distanceTo(player.getEyePosition()) > 3) return null;
            return pose;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warn(e); return null;
        }
    }
    public static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    public static void warn(Throwable e) {
        if (!warned) { warned = true; GunMod.LOGGER.warn("JYVR: Vivecraft API unavailable; VR integration disabled", e); }
    }
}
