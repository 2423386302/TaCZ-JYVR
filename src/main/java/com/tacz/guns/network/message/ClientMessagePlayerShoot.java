package com.tacz.guns.network.message;

import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientMessagePlayerShoot {
    /**
     * 这里的 timestamp 应该是基于 base timestamp 的相对值
     */
    private long timestamp;
    private float chargeProgress;

    public ClientMessagePlayerShoot() {
    }

    public ClientMessagePlayerShoot(long timestamp) {
        this(timestamp, 0f);
    }

    public ClientMessagePlayerShoot(long timestamp, float chargeProgress) {
        this.timestamp = timestamp;
        this.chargeProgress = chargeProgress;
    }

    public static void encode(ClientMessagePlayerShoot message, FriendlyByteBuf buf) {
        buf.writeLong(message.timestamp);
        buf.writeFloat(message.chargeProgress);
    }

    public static ClientMessagePlayerShoot decode(FriendlyByteBuf buf) {
        return new ClientMessagePlayerShoot(buf.readLong(), buf.readFloat());
    }

    public static void handle(ClientMessagePlayerShoot message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer entity = context.getSender();
                if (entity == null) {
                    return;
                }
                var vrPose = com.tacz.guns.compat.jyvr.VivecraftAccess.serverPose(entity);
                if (vrPose != null) {
                    var direction = vrPose.main().direction();
                    float pitch = (float) -Math.toDegrees(Math.asin(net.minecraft.util.Mth.clamp(direction.y, -1, 1)));
                    float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
                    IGunOperator.fromLivingEntity(entity).shoot(() -> pitch, () -> yaw, message.timestamp, message.chargeProgress);
                } else {
                    IGunOperator.fromLivingEntity(entity).shoot(entity::getXRot, entity::getYRot, message.timestamp, message.chargeProgress);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
