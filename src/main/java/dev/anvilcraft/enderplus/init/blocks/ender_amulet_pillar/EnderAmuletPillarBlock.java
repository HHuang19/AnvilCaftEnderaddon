package dev.anvilcraft.enderplus.init.blocks.ender_amulet_pillar;

import dev.dubhe.anvilcraft.block.multipart.SimpleMultiPartBlock;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 末影护符柱方块
 * 两格高的垂直多方块结构，底部段与顶部段上下排列。
 * 玩家可把 AnvilCraft 护符挂载到柱子上（仅存储与展示），空手右键可取下准星所指的护符。
 * 手持末影护符潜行右键柱子可把护符绑定到该柱子（绑定逻辑在护符物品的 useOn 中）。
 * 护符挂在玩家放置时面对的那一侧，从上往下排列。
 */
public class EnderAmuletPillarBlock extends SimpleMultiPartBlock<Vertical2PartHalf> implements EntityBlock {

    public static final EnumProperty<Vertical2PartHalf> PARTHALF =
            EnumProperty.create("half", Vertical2PartHalf.class); // 区分垂直方向的哪一段 (TOP/BOTTOM)
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING; // 护符悬挂朝向

    public static final VoxelShape SHAPE_TOP = Shapes.or(
            Block.box(3, 0, 3, 13, 15, 13),
            Block.box(1, 14, 1, 15, 15, 15),
            Block.box(0, 15, 0, 16, 16, 16)
    );
    public static final VoxelShape SHAPE_BOTTOM = Shapes.or(
            Block.box(0, 0, 0, 16, 1, 16),
            Block.box(1, 1, 1, 15, 2, 15),
            Block.box(3, 1, 3, 13, 16, 13)
    );

    public EnderAmuletPillarBlock() {
        super(Properties.of()
                .strength(2.0F)
                .noOcclusion()
                // 自发光：亮度 15，保证旁边有方块遮挡时柱子也不会出现阴影
                .lightLevel(state -> 15)
        );
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PARTHALF, Vertical2PartHalf.BOTTOM)
                .setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    public @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        // 放置时按玩家面对方向决定护符悬挂侧
        return this.defaultBlockState()
                .setValue(PARTHALF, Vertical2PartHalf.BOTTOM)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @NotNull Property<Vertical2PartHalf> getPart() {
        return PARTHALF;
    }

    @Override
    public Vertical2PartHalf @NotNull [] getParts() {
        return Vertical2PartHalf.values();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PARTHALF).add(FACING);
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        // 根据当前所处的具体段（上、下），返回对应的独立碰撞体积箱
        return switch (state.getValue(PARTHALF)) {
            case TOP -> SHAPE_TOP;
            case BOTTOM -> SHAPE_BOTTOM;
        };
    }

    // ==================== 方块实体 ====================

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        // 仅在底部主部件持有方块实体，顶部段不创建
        if (blockState.getValue(PARTHALF) != Vertical2PartHalf.BOTTOM) return null;
        return new EnderAmuletPillarBlockEntity(blockPos, blockState);
    }

    // ==================== 交互 ====================

    @Override
    public @NotNull InteractionResult use(
        @NotNull BlockState state,
        @NotNull Level level,
        @NotNull BlockPos pos,
        @NotNull Player player,
        @NotNull InteractionHand hand,
        @NotNull BlockHitResult hit
    ) {
        ItemStack stack = player.getItemInHand(hand);
        boolean isRemoveAttempt = stack.isEmpty();
        boolean isHangAttempt = !player.isShiftKeyDown() && stack.has(ModComponents.AMULET);

        // 客户端：对这两种交互返回 SUCCESS，确保服务端收到交互数据包
        if (level.isClientSide) {
            return (isRemoveAttempt || isHangAttempt) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        BlockPos mainPos = this.getMainPartPos(pos, state);
        if (level.getBlockEntity(mainPos) instanceof EnderAmuletPillarBlockEntity be) {
            if (isRemoveAttempt) {
                // 空手右键：取下准星所指的护符（按命中面与命中高度定位槽位）
                Direction side = hit.getDirection();
                if (!side.getAxis().isHorizontal()) return InteractionResult.PASS;
                int index = this.hitAmuletSlot(mainPos, hit);
                if (index < 0) return InteractionResult.PASS;
                ItemStack removed = be.removeAmulet(side, index);
                if (removed.isEmpty()) return InteractionResult.PASS;
                player.getInventory().placeItemBackInInventory(removed);
                player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + level.getRandom().nextFloat() * 0.4F);
                player.displayClientMessage(
                    Component.translatable("message.anvilcraft_enderplus.talisman.removed"), true
                );
                return InteractionResult.SUCCESS;
            }
            // 玩家面对的柱面：护符挂在玩家所在一侧
            Direction side = player.getDirection().getOpposite();
            if (isHangAttempt) {
                if (be.tryHangAmulet(side, stack)) {
                    stack.shrink(1);
                    player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + level.getRandom().nextFloat() * 0.4F);
                    player.displayClientMessage(
                        Component.translatable("message.anvilcraft_enderplus.talisman.hung"), true
                    );
                    return InteractionResult.SUCCESS;
                }
                player.displayClientMessage(
                    Component.translatable("message.anvilcraft_enderplus.talisman.full"), true
                );
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    /** 根据右键命中点计算准星所指护符的槽位（自上而下从 0 开始）；未指向任何护符时返回 -1。 */
    private int hitAmuletSlot(BlockPos mainPos, BlockHitResult hit) {
        double relY = hit.getLocation().y - mainPos.getY();
        double slot = (EnderAmuletPillarBlockEntity.AMULET_Y_TOP - relY) / EnderAmuletPillarBlockEntity.AMULET_Y_SPACING;
        int index = (int) Math.round(slot);
        return index >= 0 && index < EnderAmuletPillarBlockEntity.CAPACITY_PER_SIDE ? index : -1;
    }

    // ==================== 破坏掉落 ====================

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean movedByPiston) {
        // 仅底部主部件持有护符，顶部段无需处理
        if (state.getValue(PARTHALF) != Vertical2PartHalf.BOTTOM) return;
        // 方块未被真正移除（如同方块状态刷新）时不掉落
        if (state.is(newState.getBlock())) return;
        if (level.getBlockEntity(pos) instanceof EnderAmuletPillarBlockEntity be) {
            for (List<ItemStack> sideList : be.getAmuletsBySide().values()) {
                for (ItemStack stack : sideList) {
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
