package dev.anvilcraft.enderplus.network;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.enchantment.EnchantmentEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record DoubleWalk() implements CustomPacketPayload {
    public static final Type<DoubleWalk> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AnvilcraftEnderplus.MOD_ID, "double_walk"));
    public static final StreamCodec<FriendlyByteBuf, DoubleWalk> CODEC = StreamCodec.unit(new DoubleWalk());

    // 服务端冷却追踪（玩家UUID → 最后一次使用时的游戏刻）
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();

    /** 冷却随附魔等级递减：等级 1 = 60 tick(3 秒)，每级再减 15 tick，最低 20 tick(1 秒) */
    private static long cooldownTicks(int level) {
        return Math.max(20, 60 - (level - 1) * 15);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void Double_Walk(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> doubleWalk = enchantments.getOrThrow(EnchantmentEffects.DOUBLE_WALK);

            ItemStack legs = player.getInventory().armor.get(1);
            int level = legs.getEnchantmentLevel(doubleWalk);
            if (legs.isEmpty()) return;
            if (level <= 0) return;
            if (player.hurtTime > 0) return;

            // 服务端冷却检查（冷却时间随附魔等级递减）
            final long COOLDOWN = cooldownTicks(level);
            UUID uuid = player.getUUID();
            long gameTime = player.level().getGameTime();
            Long lastUsed = COOLDOWNS.get(uuid);
            if (lastUsed != null && gameTime - lastUsed < COOLDOWN) {
                return;
            }

            final double DISTANCE = AnvilcraftEnderplus.CONFIG.doubleWalkDistance;
            final double MAX_PITCH = 60.0;     // 最大俯仰角（度），超过则无法传送
            final double MAX_VERTICAL = 3.0;    // 最大垂直位移

            // 获取玩家视角方向
            Vec3 lookAngle = player.getLookAngle();
            float pitch = player.getXRot(); // -90 = 向上，+90 = 向下

            // 不能直上直下
            if (Math.abs(pitch) > MAX_PITCH) {
                return;
            }

            // 水平方向（XZ）始终为 DISTANCE
            double horizontalMag = Math.sqrt(lookAngle.x * lookAngle.x + lookAngle.z * lookAngle.z);
            if (horizontalMag < 0.001) {
                return; // 几乎没有水平分量，防止除零
            }

            double scale = DISTANCE / horizontalMag;
            double dx = lookAngle.x * scale;
            double dz = lookAngle.z * scale;

            // 垂直分量：根据视角俯仰成比例变化
            double dy = (-pitch / MAX_PITCH) * MAX_VERTICAL;

            Vec3 targetPos = player.position().add(dx, dy, dz);

            // 沿实际路径检测方块（带台阶跨越能力）
            Vec3 eyePos = player.getEyePosition();
            Vec3 eyeTarget = eyePos.add(dx, dy, dz);
            Vec3 delta = eyeTarget.subtract(eyePos);
            double actualDist = delta.length();
            int steps = Math.max(1, (int) Math.ceil(actualDist * 4));
            Vec3 step = delta.scale(1.0 / steps);

            double yStepUp = 0; // 跨越障碍物时的抬升量
            boolean steppedUp = false;

            for (int i = 1; i <= steps; i++) {
                Vec3 checkPos = eyePos.add(step.x * i, step.y * i + yStepUp, step.z * i);
                BlockPos blockPos = BlockPos.containing(checkPos);
                BlockState blockState = player.level().getBlockState(blockPos);

                if (blockState.isSolidRender(player.level(), blockPos)) {
                    // 尝试台阶跨越：仅当未抬升过且玩家仰视时
                    if (!steppedUp && pitch < 0) {
                        BlockPos abovePos = blockPos.above();
                        if (!player.level().getBlockState(abovePos).isSolidRender(player.level(), abovePos)) {
                            yStepUp = 1;
                            steppedUp = true;
                            // 用抬升后的 Y 重检当前步
                            Vec3 raisedPos = eyePos.add(step.x * i, step.y * i + yStepUp, step.z * i);
                            BlockPos raisedBP = BlockPos.containing(raisedPos);
                            if (!player.level().getBlockState(raisedBP).isSolidRender(player.level(), raisedBP)) {
                                continue;
                            }
                        }
                    }
                    // 无法通过
                    return;
                }
            }

            // 应用台阶抬升
            if (steppedUp) {
                targetPos = targetPos.add(0, yStepUp, 0);
            }

            // 在目标位置寻找可站立的表面
            BlockPos landingSearch = BlockPos.containing(targetPos.x, targetPos.y, targetPos.z);
            for (int y = 0; y < 5; y++) {
                BlockPos below = landingSearch.below(y);
                if (player.level().getBlockState(below).isSolidRender(player.level(), below)) {
                    targetPos = new Vec3(targetPos.x, below.getY() + 1, targetPos.z);
                    break;
                }
            }

            // 检查目标位置是否安全（脚部和头部不能卡在方块里）
            BlockPos feetPos = BlockPos.containing(targetPos);
            BlockPos headPos = feetPos.above();
            if (player.level().getBlockState(feetPos).isSolidRender(player.level(), feetPos) ||
                player.level().getBlockState(headPos).isSolidRender(player.level(), headPos)) {
                return;
            }

            // 执行传送
            player.teleportTo(targetPos.x, targetPos.y, targetPos.z);
            player.fallDistance = 0;

            // 闪现成功后更新冷却
            COOLDOWNS.put(uuid, gameTime);
            // 通知客户端开始冷却倒数
            PacketDistributor.sendToPlayer(player, new DoubleWalkCooldownS2CPacket(gameTime + COOLDOWN));
        });
    }
}
