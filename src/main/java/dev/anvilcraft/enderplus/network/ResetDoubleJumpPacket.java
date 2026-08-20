package dev.anvilcraft.enderplus.network;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.enchantment.EnchantmentEffects;
import dev.anvilcraft.enderplus.init.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
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

            var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> doubleJump = enchantments.getOrThrow(EnchantmentEffects.DOUBLE_JUMP);
            int level = boots.getEnchantmentLevel(doubleJump);

            Integer left = boots.get(DataComponents.DOUBLE_JUMP_LEFT.get());
            if (left != null && left == level) return;  // 已经是最新次数就不处理

            // 落地后把剩余跳跃次数重置为附魔等级（每级提供一次空中跳跃）
            boots.set(DataComponents.DOUBLE_JUMP_LEFT.get(), level);
        });
    }
}