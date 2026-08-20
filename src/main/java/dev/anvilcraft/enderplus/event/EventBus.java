package dev.anvilcraft.enderplus.event;

import dev.anvilcraft.enderplus.client.KeyBindings;
import dev.anvilcraft.enderplus.enchantment.EnchantmentEffects;
import dev.anvilcraft.enderplus.init.DataComponents;
import dev.anvilcraft.enderplus.network.DoubleJumpPacket;
import dev.anvilcraft.enderplus.network.DoubleWalk;
import dev.anvilcraft.enderplus.network.ResetDoubleJumpPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(value = Dist.CLIENT)
public class EventBus {
    // 记住上一 tick 的空格按键状态
    private static boolean prevJumpDown = false;
    // 记住上一 tick 的闪现按键状态
    private static boolean prevBlinkDown = false;
    // 记住上一 tick 是否在地面
    private static boolean wasOnGround = true;
    private static boolean wasOnFly = false;
    // 缓存的附魔 Holder
    private static Holder<Enchantment> doubleJumpHolder = null;
    private static Holder<Enchantment> doubleWalkHolder = null;

    // Double_Walk 客户端冷却追踪（服务端同步）
    private static long doubleWalkCooldownEnd = 0;
    private static final long COOLDOWN_DISPLAY_EXTRA = 4; // 冷却结束后额外显示 0.2 秒（4 tick）

    private static void ensureHolders() {
        if (doubleJumpHolder != null) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        doubleJumpHolder = enchantments.getOrThrow(EnchantmentEffects.DOUBLE_JUMP);
        doubleWalkHolder = enchantments.getOrThrow(EnchantmentEffects.DOUBLE_WALK);
    }

    /**
     * 由 {@link dev.anvilcraft.enderplus.network.DoubleWalkCooldownS2CPacket} 调用，同步服务端冷却结束时间
     */
    public static void onDoubleWalkCooldownUpdate(long endTime) {
        doubleWalkCooldownEnd = endTime;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {//客户端每帧
        Minecraft mc = Minecraft.getInstance();//mc实例
        LocalPlayer player = mc.player;//玩家
        if (player == null) return;//如果不存在玩家，直接取消
        ensureHolders();
        ItemStack boots = player.getInventory().armor.get(0);//获取靴子
        ItemStack legs = player.getInventory().armor.get(1);//获取护腿
        Boolean canJump = boots.get(DataComponents.CAN_DOUBLE_JUMP);//获取物品组件状态

        boolean isJumpDown = mc.options.keyJump.isDown();//获取空格键的状态
        boolean isBlinkDown = KeyBindings.BLINK_KEY.isDown();//获取闪现按键的状态
        boolean isOnGround = player.onGround();
        boolean isOnFly = player.getAbilities().flying;

        //player.displayClientMessage(Component.literal("Jump key is " + (isJumpDown ? "down" : "up")), false);

        TryJump(isJumpDown,isOnFly, boots, player, canJump);
        if (isOnGround && !wasOnGround) {
            ResetDoubleJump(isOnGround, boots, canJump);
        }
        TryBlink(isBlinkDown, legs, player);

        // 更新记录状态（为下一 tick 准备）
        prevJumpDown = isJumpDown;
        prevBlinkDown = isBlinkDown;
        wasOnGround = isOnGround;
        wasOnFly = isOnFly;

        // Double_Walk 冷却动态倒数显示
        long gameTime = player.level().getGameTime();
        if (doubleWalkCooldownEnd > 0 && gameTime <= doubleWalkCooldownEnd + COOLDOWN_DISPLAY_EXTRA) {
            long remaining = doubleWalkCooldownEnd - gameTime;
            float remainingSeconds = Math.max(0, remaining) / 20.0f;
            player.displayClientMessage(
                    Component.translatable("cooldown.anvilcraftenderplus.blink", String.format("%.1f", remainingSeconds)),
                    true
            );
        } else if (doubleWalkCooldownEnd > 0) {
            doubleWalkCooldownEnd = 0; // 冷却显示结束，清除状态
        }
    }

    private static void TryJump(boolean isJumpDown, boolean isOnFly, ItemStack boots, LocalPlayer player, Boolean canJump) { //尝试进行跳跃
        if (isJumpDown && !prevJumpDown) {//按下空格且上一秒空格键没有按下时
            //player.displayClientMessage(Component.literal("Double jump activated!"),false);

            if (!boots.isEmpty()//鞋子不为空
                    && !player.onGround()//玩家不在地面
                    && !isOnFly//玩家没在飞
                    && !player.isSwimming()//玩家没在潜水
                    && boots.getEnchantmentLevel(doubleJumpHolder) > 0//具有附魔
            ) {
                //player.displayClientMessage(Component.literal(DataComponents.CAN_DOUBLE_JUMP.toString()+":" + canJump), false);
                if (canJump != null && canJump && !wasOnGround && !wasOnFly) {
                    var motion = player.getDeltaMovement();
                    player.setDeltaMovement(motion.x, 0.42F, motion.z);
                    player.fallDistance = 0;

                    //通知服务端
                    PacketDistributor.sendToServer(new DoubleJumpPacket());
                }
            }
        }
    }

    private static void ResetDoubleJump(boolean isOnGround, ItemStack boots, Boolean canJump){
        if (isOnGround && (canJump == null || !canJump)) {
            if (!boots.isEmpty() && boots.getEnchantmentLevel(doubleJumpHolder) > 0) {
                // 通知服务端重置
                PacketDistributor.sendToServer(new ResetDoubleJumpPacket());
            }
        }
    }

    private static void TryBlink(boolean isBlinkDown, ItemStack legs, LocalPlayer player){
        if (isBlinkDown && !prevBlinkDown) {//按下闪现按键且上一tick没有按下时

            if (!legs.isEmpty()//护腿不为空
                    && legs.getEnchantmentLevel(doubleWalkHolder) > 0//具有附魔
            ) {
                if (player.hurtTime > 0) return;

                // 客户端侧冷却检查，避免发送多余的数据包
                if (doubleWalkCooldownEnd > 0 && player.level().getGameTime() < doubleWalkCooldownEnd) {
                    return;
                }

                // 发送闪现数据包到服务端，由服务端管理冷却
                PacketDistributor.sendToServer(new DoubleWalk());
            }
        }
    }
}
