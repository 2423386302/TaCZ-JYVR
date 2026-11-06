package com.tacz.guns.compat.jyvr;

import net.minecraftforge.common.ForgeConfigSpec;
public final class VRConfig {
    public static ForgeConfigSpec.BooleanValue ENABLED, SCOPES, DEBUG_BOXES, HIDE_AIMING_HUD, CASINGS;
    public static ForgeConfigSpec.DoubleValue SCALE, GRIP_X, GRIP_Y, GRIP_Z, PITCH, YAW, ROLL, MAG_X, MAG_Y, MAG_Z, LENS_RADIUS, AIM_DISTANCE;
    public static ForgeConfigSpec.DoubleValue SECONDS;
    public static ForgeConfigSpec.BooleanValue TIMED, HAPTICS, KICK;
    public static void init(ForgeConfigSpec.Builder b) {
        b.push("jyvr");
        ENABLED = b.comment("Original JYVR integration. Requires Vivecraft 1.3.15. Restart after changing.").define("enabled", true);
        SCOPES = b.comment("Use Vivecraft's extra scope render pass; turn off for a 1x optical fallback.").define("magnified_scopes", true);
        HIDE_AIMING_HUD=b.comment("Hide the wrist HUD while aiming a gun; preserve menus and restore on lowering.").define("hide_hud_while_aiming",true);
        CASINGS=b.comment("VR world-space casing effects; client only, capped at 256. /jyvr casings on|off").define("shell_ejection",true);
        SECONDS=b.defineInRange("shell_lifetime_seconds",5.0,0.1,3600.0);
        TIMED=b.define("shell_timed_removal",true);
        HAPTICS=b.define("controller_recoil",true);
        KICK=b.define("visual_weapon_recoil",true);
        DEBUG_BOXES = b.comment("Show translucent gun, magazine and socket colliders. Toggle with F8 or a bound VR key.").define("debug_collision_boxes", false);
        SCALE = b.defineInRange("gun_scale", 1.0, 0.25, 2.0);
        GRIP_X = b.defineInRange("grip_x", 0.0, -0.5, 0.5);
        GRIP_Y = b.defineInRange("grip_y", 0.0, -0.5, 0.5);
        GRIP_Z = b.defineInRange("grip_z", 0.0, -0.5, 0.5);
        PITCH = b.defineInRange("pitch_degrees", 0.0, -90.0, 90.0);
        YAW = b.defineInRange("yaw_degrees", 0.0, -180.0, 180.0);
        ROLL = b.defineInRange("roll_degrees", 0.0, -180.0, 180.0);
        MAG_X = b.comment("Controller-local magazine well fallback (metres); override per gun in config/tacz-jyvr-profiles.json.").defineInRange("magazine_x", 0.0, -0.5, 0.5);
        MAG_Y = b.defineInRange("magazine_y", -0.10, -0.5, 0.5);
        MAG_Z = b.defineInRange("magazine_z", -0.12, -0.6, 0.3);
        LENS_RADIUS = b.defineInRange("lens_radius", 0.018, 0.004, 0.1);
        AIM_DISTANCE = b.defineInRange("aim_enter_distance", 0.16, 0.05, 0.6);
        b.pop();
    }
}
