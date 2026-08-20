package dev.anvilcraft.enderplus.network;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.event.EventBus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record DoubleWalkCooldownS2CPacket(long cooldownEndTime) implements CustomPacketPayload {
    public static final Type<DoubleWalkCooldownS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AnvilcraftEnderplus.MOD_ID, "double_walk_cooldown")
    );

    public static final StreamCodec<FriendlyByteBuf, DoubleWalkCooldownS2CPacket> CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeLong(packet.cooldownEndTime),
            buf -> new DoubleWalkCooldownS2CPacket(buf.readLong())
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DoubleWalkCooldownS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> EventBus.onDoubleWalkCooldownUpdate(packet.cooldownEndTime));
    }
}
