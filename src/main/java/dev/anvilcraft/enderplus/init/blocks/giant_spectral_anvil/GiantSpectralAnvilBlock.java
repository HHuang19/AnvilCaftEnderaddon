package dev.anvilcraft.enderplus.init.blocks.giant_spectral_anvil;

import dev.dubhe.anvilcraft.api.power.IPowerComponent;
import dev.dubhe.anvilcraft.block.AccelerationRingBlock;
import dev.dubhe.anvilcraft.block.DeflectionRingBlock;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.MagnetBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.block.state.DirectionCube3x3PartHalf;
import dev.dubhe.anvilcraft.block.state.GiantAnvilCube;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;

/**
 * 巨型幻灵砧：3x3x3 巨型多方块砧，模型/材质直接复用巨型铁砧（anvilcraft:giant_anvil）。
 *
 * <p>继承巨型铁砧的全部语义：铁砧 GUI 与铁砧配方、不可被活塞推动（与巨型铁砧相同，
 * pushReaction=BLOCK）、不可含水、落地铺 27 部件。区别：
 * <ul>
 *     <li>固定不落：覆写 {@link #tick}，永不因失去支撑而自落；</li>
 *     <li>悬浮检测：锚格（BOTTOM_CENTER）上方第 3 格（结构正上方第一格，与巨型铁砧自落检测
 *         环位置一致）有磁铁（有磁力）或工作中的加速环/偏导环时视为“被托举”；
 *         POWERED=true 表示当前处于托举中；</li>
 *     <li>消磁沿：托举消失（磁铁消磁 / 环停转）时从结构主部件（MID_CENTER 格）释放 3x3
 *         巨型虚影向下砸，虚影等效 10 格高的巨型铁砧落地（触发巨砧全套落地事件/配方/冲击），
 *         本体保持不动。tick 内持续自调度以实现轮询检测。</li>
 * </ul>
 */
public class GiantSpectralAnvilBlock extends GiantAnvilBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** 轮询间隔：与巨型铁砧自落检测相同的 2 tick。 */
    private static final int POLL_DELAY = 2;

    public GiantSpectralAnvilBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(HALF, Cube3x3PartHalf.BOTTOM_CENTER)
            .setValue(CUBE, GiantAnvilCube.CORNER)
            .setValue(POWERED, false));
    }

    @Override
    public BlockState placedState(Cube3x3PartHalf part, BlockState state) {
        return super.placedState(part, state).setValue(POWERED, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    /**
     * 结构锚格：BOTTOM_CENTER 所在格。所有属性/调度都以它为基准。
     */
    private static BlockPos anchorPos(BlockPos pos, BlockState state) {
        return pos.subtract(state.getValue(HALF).getOffset());
    }

    /**
     * 托举检测格 = 结构正上方第一格（锚格上方 3 格，与巨型铁砧自落检测环的位置一致）。
     */
    private static BlockPos levitationCheckPos(BlockPos anchor) {
        return anchor.above(3);
    }

    private static boolean isLevitated(Level level, BlockPos anchor) {
        BlockState state = level.getBlockState(levitationCheckPos(anchor));
        if ((state.is(ModBlockTags.MAGNET) || state.getBlock() instanceof MagnetBlock)
            && state.hasProperty(MagnetBlock.LIT)
            && !state.getValue(MagnetBlock.LIT)) {
            return true;
        }
        if (state.getBlock() instanceof AccelerationRingBlock
            && state.hasProperty(AccelerationRingBlock.HALF)
            && state.getValue(AccelerationRingBlock.HALF) == DirectionCube3x3PartHalf.BOTTOM_CENTER
            && state.hasProperty(AccelerationRingBlock.SWITCH)
            && state.getValue(AccelerationRingBlock.SWITCH) == IPowerComponent.Switch.ON
            && state.hasProperty(AccelerationRingBlock.OVERLOAD)
            && !state.getValue(AccelerationRingBlock.OVERLOAD)
            && state.hasProperty(AccelerationRingBlock.FACING)
            && state.getValue(AccelerationRingBlock.FACING) == Direction.UP) {
            return true;
        }
        if (state.getBlock() instanceof DeflectionRingBlock
            && state.hasProperty(DeflectionRingBlock.HALF)
            && state.getValue(DeflectionRingBlock.HALF) == DirectionCube3x3PartHalf.BOTTOM_CENTER
            && state.hasProperty(DeflectionRingBlock.SWITCH)
            && state.getValue(DeflectionRingBlock.SWITCH) == IPowerComponent.Switch.ON
            && state.hasProperty(DeflectionRingBlock.OVERLOAD)
            && !state.getValue(DeflectionRingBlock.OVERLOAD)
            && state.hasProperty(DeflectionRingBlock.FACING)
            && state.getValue(DeflectionRingBlock.FACING).getAxis() == Direction.Axis.Y) {
            return true;
        }
        return false;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 巨型幻灵砧固定不落；仅轮询托举状态并处理“消磁沿”释放虚影。
        BlockPos anchor = anchorPos(pos, state);
        BlockState anchorState = level.getBlockState(anchor);
        if (!anchorState.is(this)) return;
        boolean powered = anchorState.getValue(POWERED);
        boolean levitated = isLevitated(level, anchor);
        if (levitated) {
            if (!powered) {
                level.setBlockAndUpdate(anchor, anchorState.setValue(POWERED, true));
            }
        } else if (powered) {
            // 消磁沿：解除托举标记并释放虚影。
            level.setBlockAndUpdate(anchor, anchorState.setValue(POWERED, false));
            if (!hasGhostNearby(level, anchor)) {
                BlockPos centerPos = anchor.above(1);
                BlockState centerState = level.getBlockState(centerPos);
                if (centerState.is(this)) {
                    FallingGiantSpectralAnvilEntity.fall(level, centerPos, centerState.setValue(POWERED, false), false);
                }
            }
        }
        // 持续自调度轮询，直到结构被移除（Level 会跳过不属于该方块状态的排队 tick）。
        level.scheduleTick(anchor, this, POLL_DELAY);
    }

    /**
     * 结构附近（含下落路径）是否已有虚影在途，用于防止重复释放。
     */
    private static boolean hasGhostNearby(Level level, BlockPos anchor) {
        AABB box = new AABB(
            anchor.getX() - 2.0,
            anchor.getY() - 12.0,
            anchor.getZ() - 2.0,
            anchor.getX() + 3.0,
            anchor.getY() + 3.0,
            anchor.getZ() + 3.0
        );
        return !level.getEntitiesOfClass(FallingGiantSpectralAnvilEntity.class, box).isEmpty();
    }
}
