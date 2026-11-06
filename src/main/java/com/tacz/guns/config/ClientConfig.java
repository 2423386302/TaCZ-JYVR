package com.tacz.guns.config;

import com.tacz.guns.config.client.*;
import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {
    public static ForgeConfigSpec init() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        KeyConfig.init(builder);
        com.tacz.guns.compat.jyvr.VRConfig.init(builder);
        RenderConfig.init(builder);
        ResourceConfig.init(builder);
        SoundConfig.init(builder);
        ZoomConfig.init(builder);
        return builder.build();
    }
}
