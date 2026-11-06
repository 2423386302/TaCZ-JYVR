package com.tacz.guns.compat.jyvr;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;
/** One server-accepted ejection, never triggered by an eye render or a speculative client shot. */
public record VRCasingMessage(ItemStack gun,int count){
    public static void encode(VRCasingMessage m,FriendlyByteBuf b){b.writeItem(m.gun);b.writeVarInt(m.count);}
    public static VRCasingMessage decode(FriendlyByteBuf b){return new VRCasingMessage(b.readItem(),b.readVarInt());}
    public static void handle(VRCasingMessage m,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();
        c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->VRCasings.spawn(m.gun,m.count)));c.setPacketHandled(true);}
}
