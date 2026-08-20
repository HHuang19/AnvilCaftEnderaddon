package dev.anvilcraft.enderplus.network;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.enchantment.EnchantmentEffects;
import dev.anvilcraft.enderplus.init.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DoubleJumpPacket() implements CustomPacketPayload {
    public static final Type<DoubleJumpPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AnvilcraftEnderplus.MOD_ID, "double_jump"));
    public static final StreamCodec<FriendlyByteBuf, DoubleJumpPacket> CODEC = StreamCodec.unit(new DoubleJumpPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void DoubleJump(DoubleJumpPacket packet, IPayloadContext context)
    {
        context.enqueueWork(() -> {//扔到安全环境执行
            ServerPlayer player = (ServerPlayer) context.player();//获取玩家
            var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> doubleJump = enchantments.getOrThrow(EnchantmentEffects.DOUBLE_JUMP);

            ItemStack boots = player.getInventory().armor.getFirst();//获取靴子
            if (boots.isEmpty()) return;//确认靴子
            if (boots.getEnchantmentLevel(doubleJump) <= 0) return;//确认附魔
            Boolean canJump = boots.get(DataComponents.CAN_DOUBLE_JUMP);//确认状态
            //player.displayClientMessage(Component.literal(DataComponents.CAN_DOUBLE_JUMP.toString()+":" + canJump), false);
            if (Boolean.FALSE.equals(canJump)) return;
            boots.set(DataComponents.CAN_DOUBLE_JUMP.get(), false);//改变靴子的状态
            //player.displayClientMessage(Component.literal("Double jump activated!"), false);
            var motion = player.getDeltaMovement();//获取玩家的移动速度
            player.setDeltaMovement(motion.x, 0.42F, motion.z);
            player.fallDistance = 0;
            //player.hurtMarked = true;
        });
    }
}
