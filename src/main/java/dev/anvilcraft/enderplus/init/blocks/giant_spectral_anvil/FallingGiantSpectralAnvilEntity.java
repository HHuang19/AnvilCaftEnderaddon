package dev.anvilcraft.enderplus.init.blocks.giant_spectral_anvil;

import com.google.common.collect.ImmutableList;
import dev.anvilcraft.enderplus.init.AddonEntities;
import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.SpectralAnvilBlock;
import dev.dubhe.anvilcraft.entity.FallingGiantAnvilEntity;
import dev.dubhe.anvilcraft.init.ModSoundEvents;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import dev.dubhe.anvilcraft.init.entity.ModDamageTypes;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Unmodifiable;

import javax.annotation.Nullable;

import java.util.List;

/**
 * 巨型幻灵砧的虚影：3x3x3 巨型铁砧外形的下落虚影。
 *
 * <p>是 {@link FallingGiantAnvilEntity} 的子类，因此可以直接参与 anvilcraft 的
 * {@link AnvilEvent.GiantOnLand} 事件链（巨砧多方块配方 / 重型铁砧冲击等）。
 * 与幻灵虚影一致：不铺块、不掉落、可穿过透明/可替换方块与本体结构。
 * 区别：
 * <ul>
 *     <li>等效下落高度固定为 10 格（需求：下落高度变 10 格），
 *         落地事件一律按 10 格巨型铁砧处理；</li>
 *     <li>不穿过实体：实体碰撞会挡住虚影并触发落地（砸伤实体后消失）。</li>
 * </ul>
 */
public class FallingGiantSpectralAnvilEntity extends FallingGiantAnvilEntity {
    /** 虚影事件统一使用的等效下落高度。 */
    public static final float EQUIVALENT_FALL_DISTANCE = 10.0F;

    private boolean isGhostEntity;

    public FallingGiantSpectralAnvilEntity(
        EntityType<? extends FallingGiantSpectralAnvilEntity> entityType,
        Level level
    ) {
        super(entityType, level);
    }

    private FallingGiantSpectralAnvilEntity(Level level, double x, double y, double z, BlockState state) {
        this(AddonEntities.FALLING_GIANT_SPECTRAL_ANVIL.get(), level);
        this.blockState = state;
        this.blocksBuilding = true;
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.setStartPos(this.blockPosition());
        this.isGhostEntity = true;
        // 虚影不铺块、不掉落。
        this.dropItem = false;
        this.cancelDrop = true;
    }

    /**
     * 生成虚影。{@code updateBlock=false} 表示不移除原方块（本体保留原位，固定不落）。
     */
    public static FallingGiantSpectralAnvilEntity fall(
        Level level,
        BlockPos pos,
        BlockState blockState,
        boolean updateBlock
    ) {
        FallingGiantSpectralAnvilEntity entity = new FallingGiantSpectralAnvilEntity(
            level,
            pos.getX() + 0.5,
            pos.getY(),
            pos.getZ() + 0.5,
            blockState.hasProperty(BlockStateProperties.WATERLOGGED)
                ? blockState.setValue(BlockStateProperties.WATERLOGGED, false)
                : blockState
        );
        if (updateBlock) {
            level.setBlock(pos, blockState.getFluidState().createLegacyBlock(), 3);
        }
        level.addFreshEntity(entity);
        return entity;
    }

    @Override
    public boolean anvilcraft$isSpectral() {
        return true;
    }

    @Override
    public float anvilcraft$getFallDistance() {
        return EQUIVALENT_FALL_DISTANCE;
    }

    @Override
    public void callOnBrokenAfterFall(Block block, BlockPos pos) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("Ghost", this.isGhostEntity);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.isGhostEntity = compound.contains("Ghost") && compound.getBoolean("Ghost");
    }

    @Override
    public void tick() {
        if (this.blockState.isAir()) {
            this.discard();
            return;
        }
        this.time++;
        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.handlePortal();
        if (!this.level().isClientSide && this.isAlive()) {
            if (this.onGround()) {
                this.land();
                return;
            }
            if (this.time > 100
                && (this.blockPosition().getY() <= this.level().getMinBuildHeight()
                || this.blockPosition().getY() > this.level().getMaxBuildHeight())
                || this.time > 600) {
                // 掉出世界/超时清理（虚影不掉落物）
                this.discard();
                return;
            }
        }
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
    }

    /**
     * 虚影落地：等效 10 格巨型铁砧砸下。不铺块，只触发事件链/配方/冲击/伤害后消失。
     */
    private void land() {
        Level level = this.level();
        // 与真实巨型铁砧落地几何一致：主部件格 = 实体所在格，底层部件格 = 其下一格。
        BlockPos mainPos = this.blockPosition();
        BlockPos belowPos = mainPos.below();
        float fallDistance = EQUIVALENT_FALL_DISTANCE;

        this.hurtEntitiesInside();

        NeoForge.EVENT_BUS.post(new AnvilEvent.GiantOnLand(level, mainPos, this, fallDistance));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                NeoForge.EVENT_BUS.post(new AnvilEvent.OnLand(
                    level,
                    belowPos.offset(dx, 0, dz),
                    this,
                    fallDistance
                ));
            }
        }
        level.playSound(
            null,
            belowPos,
            ModSoundEvents.GIANT_ANVIL_LAND.get(),
            SoundSource.BLOCKS,
            0.55F,
            level.random.nextFloat() * 0.1F + 0.55F
        );
        this.discard();
    }

    /**
     * 伤害虚影体积内的生物，与真实巨型铁砧（{@link GiantAnvilBlock#falling} 语义）一致。
     */
    private void hurtEntitiesInside() {
        Level level = this.level();
        if (level.isClientSide) return;
        float damage = AnvilCraft.CONFIG.giantAnvilFallDamageMax;
        var predicate = EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(EntitySelector.LIVING_ENTITY_STILL_ALIVE);
        List<Entity> entities = level.getEntities(this, this.getBoundingBox(), predicate);
        for (Entity entity : entities) {
            entity.hurt(ModDamageTypes.fallingGiantAnvil(level, this), damage);
            NeoForge.EVENT_BUS.post(new AnvilEvent.HurtEntity(this, this.getOnPos(), level, entity, damage));
        }
    }

    @Override
    public Vec3 collide(Vec3 vec) {
        AABB aabb = this.getBoundingBox();
        List<VoxelShape> list = this.level().getEntityCollisions(this, aabb.expandTowards(vec));
        Vec3 vec3 = vec.lengthSqr() == 0.0 ? vec : collideBoundingBox(this, vec, aabb, this.level(), list);
        boolean flag = vec.x != vec3.x;
        boolean flag1 = vec.y != vec3.y;
        boolean flag2 = vec.z != vec3.z;
        boolean flag3 = flag1 && vec.y < 0.0;
        if (this.maxUpStep() > 0.0F && (flag3 || this.onGround()) && (flag || flag2)) {
            AABB aabb1 = flag3 ? aabb.move(0.0, vec3.y, 0.0) : aabb;
            AABB aabb2 = aabb1.expandTowards(vec.x, this.maxUpStep(), vec.z);
            if (!flag3) {
                aabb2 = aabb2.expandTowards(0.0, -1.0E-5F, 0.0);
            }

            List<VoxelShape> list1 = collectColliders(this, this.level(), list, aabb2);
            float f = (float) vec3.y;
            float[] afloat = collectCandidateStepUpHeights(aabb1, list1, this.maxUpStep(), f);

            for (float f1 : afloat) {
                Vec3 vec31 = collideWithShapes(new Vec3(vec.x, f1, vec.z), aabb1, list1);
                if (vec31.horizontalDistanceSqr() > vec3.horizontalDistanceSqr()) {
                    double d0 = aabb.minY - aabb1.minY;
                    return vec31.add(0.0, -d0, 0.0);
                }
            }
        }
        return vec3;
    }

    public static Vec3 collideBoundingBox(
        @Nullable Entity entity,
        Vec3 vec,
        AABB collisionBox,
        Level level,
        List<VoxelShape> potentialHits
    ) {
        List<VoxelShape> list = collectColliders(entity, level, potentialHits, collisionBox.expandTowards(vec));
        return collideWithShapes(vec, collisionBox, list);
    }

    private static @Unmodifiable List<VoxelShape> collectColliders(
        @Nullable Entity entity,
        Level level,
        List<VoxelShape> collisions,
        AABB boundingBox
    ) {
        ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(collisions.size() + 1);
        if (!collisions.isEmpty()) {
            builder.addAll(collisions);
        }

        WorldBorder worldborder = level.getWorldBorder();
        boolean flag = entity != null && worldborder.isInsideCloseToBorder(entity, boundingBox);
        if (flag) {
            builder.add(worldborder.getCollisionShape());
        }

        builder.addAll(getBlockCollisions(level, entity, boundingBox));
        return builder.build();
    }

    private static float[] collectCandidateStepUpHeights(
        AABB box,
        List<VoxelShape> colliders,
        float deltaY,
        float maxUpStep
    ) {
        FloatSet floatset = new FloatArraySet(4);

        for (VoxelShape voxelshape : colliders) {
            for (double d0 : voxelshape.getCoords(Direction.Axis.Y)) {
                float f = (float) (d0 - box.minY);
                if (!(f < 0.0F) && f != maxUpStep) {
                    if (f > deltaY) {
                        break;
                    }

                    floatset.add(f);
                }
            }
        }

        float[] afloat = floatset.toFloatArray();
        FloatArrays.unstableSort(afloat);
        return afloat;
    }

    private static Iterable<VoxelShape> getBlockCollisions(Level level, @Nullable Entity entity, AABB collisionBox) {
        return () -> new BlockCollisions<>(
            level,
            entity,
            collisionBox,
            false,
            (pos, shape) -> {
                BlockState state = level.getBlockState(pos);
                if (shouldIgnoreBlockInMovement(state)) {
                    return Shapes.empty();
                }
                return shape;
            }
        );
    }

    private static boolean shouldIgnoreBlockInMovement(BlockState state) {
        // noinspection deprecation
        return state.isAir()
            || state.is(BlockTags.FIRE)
            || state.liquid()
            || state.is(ModBlockTags.SPECTRAL_CAN_THROUGH)
            || state.getBlock() instanceof TransparentBlock
            || state.canBeReplaced()
            || (
                !state.getBlock().properties().hasCollision
                && !state.is(Blocks.SCAFFOLDING)
            )
            || state.getBlock() instanceof SpectralAnvilBlock
            || state.getBlock() instanceof GiantSpectralAnvilBlock;
    }
}
