package com.tacz.guns.compat.jyvr;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;
public record VRActionMessage(int slot, UUID gun, int action, int sequence) {
    public VRActionMessage(int slot,UUID gun,int action){this(slot,gun,action,0);}
    public static final int DETACH = 0, TAKE = 1, INSERT = 2, RACK = 3, STORE = 4, TOGGLE = 5, LOAD = 6;
    public static void encode(VRActionMessage m, FriendlyByteBuf b) { b.writeByte(m.slot); b.writeUUID(m.gun); b.writeByte(m.action); b.writeVarInt(m.sequence); }
    public static VRActionMessage decode(FriendlyByteBuf b) { return new VRActionMessage(b.readUnsignedByte(), b.readUUID(), b.readUnsignedByte(), b.readVarInt()); }
    public static void handle(VRActionMessage m, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context c = supplier.get();
        c.enqueueWork(() -> { if (c.getSender() != null) VRServer.handle(c.getSender(), m); });
        c.setPacketHandled(true);
    }
}
