package dev.anvilcraft.enderplus.init.blocks.ender_transmission_pole;

import dev.anvilcraft.enderplus.init.AddonBlocks;
import dev.dubhe.anvilcraft.api.IHasMultiBlock;
import dev.dubhe.anvilcraft.api.hammer.IHammerRemovable;
import dev.dubhe.anvilcraft.api.power.IPowerComponent;
import dev.dubhe.anvilcraft.block.multipart.SimpleMultiPartBlock;
import dev.dubhe.anvilcraft.block.state.Vertical3PartHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 末影输电杆方块
 * 行为与普通输电杆一致：作为 TRANSMITTER 接入本地电网，由红石信号控制开关。
 * 继承自 SimpleMultiPartBlock，表现为 3 格高的垂直多部分方块结构。
 */
public class EnderPoleBlock extends SimpleMultiPartBlock<Vertical3PartHalf>
    implements IHammerRemovable, IHasMultiBlock, EntityBlock {

    // --- 方块状态属性定义 (BlockState Properties) ---
    public static final EnumProperty<Vertical3PartHalf> PARTHALF = EnumProperty.create("half", Vertical3PartHalf.class); // 区分垂直方向的哪一段 (BOTTOM/MID/TOP)
    public static final BooleanProperty OVERLOAD = IPowerComponent.OVERLOAD;
    public static final EnumProperty<IPowerComponent.Switch> SWITCH = IPowerComponent.SWITCH; // 电源开关状态 (ON/OFF)

    // --- 碰撞箱 (VoxelShape) 定义 ---
    public static final VoxelShape SHAPE_TOP = Shapes.or(Block.box(3, 5, 3, 13, 16, 13), Block.box(6, 0, 6, 10, 5, 10));
    public static final VoxelShape SHAPE_MID = Block.box(6, 0, 6, 10, 16, 10);
    public static final VoxelShape SHAPE_BOT = Shapes.or(Block.box(3, 4, 3, 13, 10, 13), Block.box(0, 0, 0, 16, 4, 16), Block.box(6, 10, 6, 10, 16, 10));

    public EnderPoleBlock() {
        super(Properties.of()
                .strength(2.0F)
                .noOcclusion()
                // 动态发光：仅顶段通电且未过载时满亮，过载时低亮，其余不发光
                .lightLevel(state -> {
                    if (state.getValue(PARTHALF) != Vertical3PartHalf.TOP) return 0;
                    if (state.getValue(SWITCH) == IPowerComponent.Switch.OFF) return 0;
                    if (state.getValue(OVERLOAD)) return 6;
                    return 15;
                })
        );
        // 注册方块的默认状态属性
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PARTHALF, Vertical3PartHalf.BOTTOM) // 默认表现为底部段
                .setValue(OVERLOAD, true)                     // 默认开启过载检测
                .setValue(SWITCH, IPowerComponent.Switch.ON)  // 默认开关开启
        );
    }

    @Override
    public @NotNull Property<Vertical3PartHalf> getPart() {
        return PARTHALF; // 返回控制方块分段的属性映射
    }

    @Override
    public Vertical3PartHalf @NotNull [] getParts() {
        return Vertical3PartHalf.values(); // 返回全部分段的枚举数组
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL; // 使用常规的 json/obj 模型进行渲染
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        // 根据当前所处的具体段（上、中、下），返回对应的独立碰撞体积箱
        return switch (state.getValue(PARTHALF)) {
            case BOTTOM -> SHAPE_BOT;
            case MID -> SHAPE_MID;
            case TOP -> SHAPE_TOP;
        };
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public @Nullable BlockState getPlacementState(@NotNull BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // 放置时按当前位置红石信号决定开关初始状态
        IPowerComponent.Switch sw = level.hasNeighborSignal(pos) ? IPowerComponent.Switch.OFF : IPowerComponent.Switch.ON;
        return this.defaultBlockState()
                .setValue(PARTHALF, Vertical3PartHalf.BOTTOM)
                .setValue(OVERLOAD, true)
                .setValue(SWITCH, sw);
    }

    @Override
    public BlockState placedState(Vertical3PartHalf part, BlockState state) {
        // 各部件放置时强制开关为开启
        return super.placedState(part, state).setValue(SWITCH, IPowerComponent.Switch.ON);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // 向方块状态构建器注册所有自定义的属性，使其能存入 BlockState
        builder.add(PARTHALF).add(OVERLOAD).add(SWITCH);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        return new EnderPoleBlockEntity(blockPos, blockState); // 绑定专用的方块实体
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) return null; // 过滤客户端，只在服务端注册 tick 逻辑
        return (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof EnderPoleBlockEntity poleEntity) {
                poleEntity.tick(level1, pos); // 每 tick 执行方块实体的具体业务
            }
        };
    }

    @Override
    protected void neighborChanged(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Block neighborBlock, @NotNull BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;
        // 限制：仅由电线杆的“底部段”来响应邻近方块的红石信号更新
        if (state.getValue(PARTHALF) != Vertical3PartHalf.BOTTOM) return;

        // 向上获取两格处的“顶部段”方块状态
        BlockPos topPos = pos.above(2);
        BlockState topState = level.getBlockState(topPos);
        if (!topState.is(AddonBlocks.ENDERPOLE.get())) return;
        if (topState.getValue(PARTHALF) != Vertical3PartHalf.TOP) return;

        // 红石联动逻辑：有信号时强制设为 OFF（切断能源），无信号时恢复 ON（开启能源）
        IPowerComponent.Switch sw = state.getValue(SWITCH);
        boolean bl = sw == IPowerComponent.Switch.ON;
        if (bl == level.hasNeighborSignal(pos)) {
            if (bl) {
                state = state.setValue(SWITCH, IPowerComponent.Switch.OFF);
                topState = topState.setValue(SWITCH, IPowerComponent.Switch.OFF);
            } else {
                state = state.setValue(SWITCH, IPowerComponent.Switch.ON);
                topState = topState.setValue(SWITCH, IPowerComponent.Switch.ON);
            }
            level.setBlockAndUpdate(pos, state);
            level.setBlockAndUpdate(topPos, topState);
        }
    }

    /**
     * 右键交互：潜行 + 空手右键已绑定的杆 → 解除绑定。
     */
    @Override
    public @NotNull InteractionResult use(
        @NotNull BlockState state,
        @NotNull Level level,
        @NotNull BlockPos pos,
        @NotNull Player player,
        @NotNull InteractionHand hand,
        @NotNull BlockHitResult hit
    ) {
        boolean isUnbindAttempt = player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty();
        // 客户端：潜行+空手视为"已消费"，确保服务器收到交互数据包
        if (level.isClientSide) return isUnbindAttempt ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (!isUnbindAttempt) return InteractionResult.PASS;
        BlockPos topPos = this.getMainPartPos(pos, state).above(2);
        if (level.getBlockEntity(topPos) instanceof EnderPoleBlockEntity pole && pole.isBound()) {
            pole.unbind();
            player.displayClientMessage(
                Component.translatable("message.anvilcraft_enderplus.pole.unbound"), true
            );
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public @NotNull BlockState playerWillDestroy(
        @NotNull Level level,
        @NotNull BlockPos pos,
        @NotNull BlockState state,
        @NotNull Player player
    ) {
        if (!level.isClientSide) {
            this.onRemove(level, pos, state);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onPlace(Level level, BlockPos pos, BlockState state) {
    }

    @Override
    public void onRemove(Level level, BlockPos pos, BlockState state) {
        // 拆除多方块结构的任一部分时，清理顶段 BE 的绑定（含远端杆的绑定）
        if (!state.is(AddonBlocks.ENDERPOLE.get())) return;
        BlockPos topPos = this.getMainPartPos(pos, state).above(2);
        if (level.getBlockEntity(topPos) instanceof EnderPoleBlockEntity pole && pole.isBound()) {
            pole.unbind();
        }
    }
}
