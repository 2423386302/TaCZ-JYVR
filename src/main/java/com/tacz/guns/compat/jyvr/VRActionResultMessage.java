package com.tacz.guns.compat.jyvr;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.UUID;
import java.util.function.Supplier;
/** Authoritative chamber acknowledgment, scoped to a gun and the current gesture. */
public record VRActionResultMessage(UUID gun,int sequence,int result,int ammo,boolean loaded){
    public static void encode(VRActionResultMessage m,FriendlyByteBuf b){b.writeUUID(m.gun);b.writeVarInt(m.sequence);b.writeByte(m.result);b.writeVarInt(m.ammo);b.writeBoolean(m.loaded);}
    public static VRActionResultMessage decode(FriendlyByteBuf b){return new VRActionResultMessage(b.readUUID(),b.readVarInt(),b.readUnsignedByte(),b.readVarInt(),b.readBoolean());}
    public static void handle(VRActionResultMessage m,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();
        c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->VRClient.rackResult(m)));c.setPacketHandled(true);}
}
