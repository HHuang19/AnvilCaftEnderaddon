package dev.anvilcraft.enderplus.mixin;

import dev.anvilcraft.enderplus.event.GiantSpectralPortalListener;
import dev.dubhe.anvilcraft.api.event.EntityThroughPortalEvent;
import dev.dubhe.anvilcraft.api.portal.PortalType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 巨型铁砧下落实体补全末地传送门机制。
 *
 * <p>原版传送门链路（{@code baseTick → checkInsideBlocks → entityInside →
 * {@code setAsInsidePortal} → PortalProcessor 停留计时 → {@code handlePortal}）依赖
 * 实体在门内连续停留数个 tick，且要求实体走 {@code baseTick}。anvilcraft 的
 * {@code FallingGiantAnvilEntity#tick()} 完全自写、下落物也不可能在传送门内停留，
 * 因此巨型铁砧原样永远无法穿过末地传送门。
 *
 * <p>此处改为主动式传送：在落地判定之前扫描 3x3 碰撞盒（含向下 1 格扩展）
 * 覆盖的末地传送门方块，命中后手动发布 {@link EntityThroughPortalEvent}
 * （与 anvilcraft {@code EntityMixin} 在 {@code handlePortal} 中拦截
 * {@code changeDimension} 时发布的事件等价，走同一套监听：概率转化在
 * {@link GiantSpectralPortalListener} 中完成，其余默认行为一致），
 * 随后携带转化后的方块状态真实跨维度传送（主世界要塞门 → 末地，
 * 末地返回门 → 主世界），落地后按转化结果铺块。
 *
 * <p>mixin 类 extends {@link Entity}（目标类的父类）以直接访问 protected 成员，
 * 不能 extends 目标类自身。
 */
@Mixin(targets = "dev.dubhe.anvilcraft.entity.FallingGiantAnvilEntity")
public abstract class FallingGiantAnvilEntityMixin extends Entity {

    protected FallingGiantAnvilEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Ldev/dubhe/anvilcraft/entity/FallingGiantAnvilEntity;anvilcraft$hasBlockCollision(Lnet/minecraft/core/Direction;)Z",
            shift = At.Shift.BEFORE
        ),
        cancellable = true
    )
    private void anvilcraftEnderplus$teleportThroughEndPortal(CallbackInfo ci) {
        Level world = this.level();
        if (world.isClientSide || !(world instanceof ServerLevel serverLevel)) return;
        if (this.isRemoved()) return;
        if (this.isOnPortalCooldown()) return;
        if (!this.canUsePortal(false)) return;

        // 用当前位置现算 3x3 结构盒（与 anvilcraft makeBoundingBox 一致：盒底 = position.y-1、
        // 盒顶 = position.y+2，覆盖整座 3x3 巨砧结构），并向下扩展 1 格覆盖下一 tick 落点。
        // 不用 getBoundingBox()：巨砧实体每 tick 自写移动（move 不刷新 boundingBox 字段），
        // 缓存的盒可能陈旧。
        Vec3 pos = this.position();
        AABB box = new AABB(
            pos.x - 1.5, pos.y - 1.0, pos.z - 1.5,
            pos.x + 1.5, pos.y + 2.0, pos.z + 1.5
        ).expandTowards(0.0, -1.0, 0.0);
        BlockPos min = BlockPos.containing(box.minX + 1.0E-7, box.minY + 1.0E-7, box.minZ + 1.0E-7);
        BlockPos max = BlockPos.containing(box.maxX - 1.0E-7, box.maxY - 1.0E-7, box.maxZ - 1.0E-7);
        for (BlockPos portalPos : BlockPos.betweenClosed(min, max)) {
            BlockState state = serverLevel.getBlockState(portalPos);
            if (!state.is(Blocks.END_PORTAL)) continue;
            if (!(state.getBlock() instanceof Portal portal)) continue;

            DimensionTransition transition = portal.getPortalDestination(serverLevel, this, portalPos);
            // 前置检查失败（目标维度不可达/不可跨维）不置冷却：不发布事件就不掷转化骰，
            // 每 tick 重试无害（如服务器正在加载目标维度）。只在真正发布过门事件后置冷却，
            // 防止实体在门格内停留多个 tick 时重复发布事件导致 20/40/40 被多次采样。
            if (transition == null) return;
            ServerLevel destination = transition.newLevel();
            if (!serverLevel.getServer().isLevelEnabled(destination)) return;
            if (destination.dimension() != serverLevel.dimension()
                && !this.canChangeDimensions(serverLevel, destination)) {
                return;
            }
            // 复刻 anvilcraft 的过门事件（概率转化监听器在此修改 blockState），随后真传。
            // 事件已发布即视为一次完整的过门尝试：置冷却避免重复采样。
            this.setPortalCooldown();
            EntityThroughPortalEvent event = new EntityThroughPortalEvent(
                serverLevel, this, PortalType.END_PORTAL);
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) return;
            // 触发真实跨维度传送，随后截断本 tick 剩余落地逻辑
            this.changeDimension(transition);
            ci.cancel();
            return;
        }
    }
}
