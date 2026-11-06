package com.tacz.guns.compat.jyvr;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.Map;

/** Optional per-gun calibration; no gun-pack files are changed. Restart to reload. */
public final class VRProfiles {
    public static class Profile {
        public double scale = 1, x, y, z, pitch, yaw, roll;
        public boolean overrideMagazine;
        public double magazineX = 0, magazineY = -0.10, magazineZ = -0.12, lensRadius = 0.018;
    }
    private static Map<String, Profile> profiles;
    public static Profile get(ItemStack gun) {
        if (profiles == null) {
            profiles = new java.util.HashMap<>();
            var path = FMLPaths.CONFIGDIR.get().resolve("tacz-jyvr-profiles.json");
            if (Files.isRegularFile(path)) {
                try (var reader = Files.newBufferedReader(path)) {
                    Map<String, Profile> loaded = new GsonBuilder().create().fromJson(reader,
                        new TypeToken<Map<String, Profile>>(){}.getType());
                    if (loaded != null) loaded.forEach((k,v) -> { if (valid(v)) profiles.put(k,v); });
                } catch (Exception e) { GunMod.LOGGER.warn("JYVR profile file ignored", e); }
            }
        }
        Profile custom = gun.getItem() instanceof IGun g ? profiles.get(g.getGunId(gun).toString()) : null;
        if (custom != null) return custom;
        Profile p = new Profile();
        p.scale = VRConfig.SCALE.get(); p.x = VRConfig.GRIP_X.get(); p.y = VRConfig.GRIP_Y.get(); p.z = VRConfig.GRIP_Z.get();
        p.pitch = VRConfig.PITCH.get(); p.yaw = VRConfig.YAW.get(); p.roll = VRConfig.ROLL.get();
        p.magazineX = VRConfig.MAG_X.get(); p.magazineY = VRConfig.MAG_Y.get(); p.magazineZ = VRConfig.MAG_Z.get(); p.lensRadius = VRConfig.LENS_RADIUS.get();
        return p;
    }
    private static boolean valid(Profile p) {
        if (p == null) return false;
        for (double v : new double[]{p.scale,p.x,p.y,p.z,p.pitch,p.yaw,p.roll,p.magazineX,p.magazineY,p.magazineZ,p.lensRadius})
            if (!Double.isFinite(v)) return false;
        return p.scale >= .25 && p.scale <= 2 && Math.abs(p.x) <= .5 && Math.abs(p.y) <= .5 && Math.abs(p.z) <= .5 &&
            Math.abs(p.pitch) <= 90 && Math.abs(p.yaw) <= 180 && Math.abs(p.roll) <= 180 &&
            Math.abs(p.magazineX) <= .5 && Math.abs(p.magazineY) <= .5 && Math.abs(p.magazineZ) <= .6 && p.lensRadius >= .004 && p.lensRadius <= .1;
    }
}
