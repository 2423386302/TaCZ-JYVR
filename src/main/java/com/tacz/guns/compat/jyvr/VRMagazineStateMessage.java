package com.tacz.guns.compat.jyvr;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.UUID;
import java.util.function.Supplier;

public record VRMagazineStateMessage(UUID player,ItemStack magazine) {
    public static void encode(VRMagazineStateMessage m,FriendlyByteBuf b){b.writeUUID(m.player);b.writeItem(m.magazine);}
    public static VRMagazineStateMessage decode(FriendlyByteBuf b){return new VRMagazineStateMessage(b.readUUID(),b.readItem());}
    public static void handle(VRMagazineStateMessage m,Supplier<NetworkEvent.Context> supplier){
        var c=supplier.get();
        c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->VRClient.receiveMagazine(m.player,m.magazine)));
        c.setPacketHandled(true);
    }
}
