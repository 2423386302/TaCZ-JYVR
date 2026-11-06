package com.tacz.guns.compat.jyvr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.fml.ModList;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/** Uses Vivecraft's official held interaction API, so native grip works without global key stealing. */
public final class VRInput {
    public static boolean gripping;
    public static void install() {
        if (!ModList.get().isLoaded("vivecraft")) return;
        try {
            Class<?> held = Class.forName("org.vivecraft.api.client.HeldInteractModule");
            Object module = Proxy.newProxyInstance(held.getClassLoader(),new Class<?>[]{held},(proxy,method,args) -> {
                boolean off = args != null && args.length > 1 && args[1] == InteractionHand.OFF_HAND;
                return switch (method.getName()) {
                    case "getId" -> new ResourceLocation("tacz","jyvr_magazine");
                    case "getPriority" -> 100;
                    case "isActive" -> off && VRClient.canInteract(true);
                    case "onPress" -> { gripping = off && VRClient.canInteract(false); yield gripping; }
                    case "onHoldTick" -> { gripping = off && VRClient.canInteract(false); yield gripping; }
                    case "reset", "onRelease" -> { if (off) gripping = false; yield null; }
                    case "swingsArm" -> false;
                    case "toString" -> "JYVR magazine interaction";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                };
            });
            Consumer<Object> handler = event -> {
                try {
                    Class<?> moduleClass=Class.forName("org.vivecraft.api.client.InteractModule");
                    Object modules=Array.newInstance(moduleClass,1); Array.set(modules,0,module);
                    VivecraftAccess.call("org.vivecraft.api.client.event.VivecraftClientRegistrationEvent","registerInteractModules",event,
                        new Class<?>[]{modules.getClass()},modules);
                } catch (ReflectiveOperationException e) { VivecraftAccess.warn(e); }
            };
            String api="org.vivecraft.api.client.VRClientAPI";
            VivecraftAccess.call(api,"addClientRegistrationHandler",VivecraftAccess.call(api,"instance",null),
                new Class<?>[]{Consumer.class},handler);
        } catch (ReflectiveOperationException | LinkageError e) { VivecraftAccess.warn(e); }
    }
}
