package dev.anvilcraft.enderplus.network;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.init.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

//一个网络包，用来重置二段跳
public record ResetDoubleJumpPacket() implements CustomPacketPayload {
    public static final Type<ResetDoubleJumpPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AnvilcraftEnderplus.MOD_ID, "reset_double_jump")
    );//注册一个名为reset_double_jump的网络包
    public static final StreamCodec<FriendlyByteBuf, ResetDoubleJumpPacket> CODEC =
            StreamCodec.unit(new ResetDoubleJumpPacket());
    //一个不带参数的包

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ResetDoubleJumpPacket packet, IPayloadContext context) {//接受到包时发生的事情
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ItemStack boots = player.getInventory().armor.getFirst();

            if (boots.isEmpty()) return;

            //player.displayClientMessage(Component.literal("Resetting double jump for " + player.getName().getString()), false);

            Boolean canJump = boots.get(DataComponents.CAN_DOUBLE_JUMP);
            if (canJump != null && canJump) return;  // 已经是 true 就不处理

            //player.displayClientMessage(Component.literal("Double jump reset!"), false);

            boots.set(DataComponents.CAN_DOUBLE_JUMP.get(), true);
        });
    }
}