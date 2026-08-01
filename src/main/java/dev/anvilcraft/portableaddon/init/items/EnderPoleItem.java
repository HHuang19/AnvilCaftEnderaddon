package dev.anvilcraft.portableaddon.init.items;

import dev.anvilcraft.portableaddon.init.AddonBlocks;
import dev.anvilcraft.portableaddon.init.DataComponents;
import dev.anvilcraft.portableaddon.init.blocks.ender_transmission_pole.EnderPoleBlock;
import dev.anvilcraft.portableaddon.init.blocks.ender_transmission_pole.EnderPoleBlockEntity;
import dev.dubhe.anvilcraft.block.state.Vertical3PartHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 末影输电杆方块物品：既可用于正常放置，也负责跨维度绑定。
 * <ul>
 *   <li>右键已放置的末影输电杆：未记录目标 → 记录该杆为绑定目标（物品附魔发光、
 *       tooltip 显示目标坐标）；已记录目标 → 把当前杆与记录目标互绑成一条输电链路；</li>
 *   <li>右键非输电杆 → 正常放置方块；</li>
 *   <li>潜行 + 右键空气 → 清除记录的绑定目标；</li>
 *   <li>绑定目标相同的物品可堆叠（DataComponent 一致）。</li>
 * </ul>
 */
public class EnderPoleItem extends BlockItem {

    public EnderPoleItem() {
        super(AddonBlocks.ENDERPOLE.get(), new Item.Properties());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState state = level.getBlockState(clickedPos);
        if (!state.is(AddonBlocks.ENDERPOLE.get())) {
            // 非末影输电杆：正常放置方块
            return super.useOn(context);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();

        ResourceKey<Level> myDim = level.dimension();
        BlockPos topPos = AddonBlocks.ENDERPOLE.get().getMainPartPos(clickedPos, state).above(2);
        ResourceKey<Level> targetDim = stack.get(DataComponents.ENDER_TARGET_DIMENSION);
        BlockPos targetPos = stack.get(DataComponents.ENDER_TARGET_POS);

        if (targetDim == null || targetPos == null) {
            // 未记录：把当前杆记为绑定目标
            stack.set(DataComponents.ENDER_TARGET_DIMENSION, myDim);
            stack.set(DataComponents.ENDER_TARGET_POS, topPos);
            player.displayClientMessage(
                Component.translatable(
                    "message.anvilcraft_portable_addon.pole.bound",
                    topPos.getX(), topPos.getY(), topPos.getZ()
                ),
                true
            );
            return InteractionResult.SUCCESS;
        }

        // 已记录目标：把当前杆与记录目标互绑（getBlockState 会加载目标区块）
        MinecraftServer server = level.getServer();
        Level targetLevel = server.getLevel(targetDim);
        if (targetLevel == null) {
            player.displayClientMessage(
                Component.translatable("message.anvilcraft_portable_addon.pole.unknown_dimension"), true
            );
            return InteractionResult.SUCCESS;
        }
        BlockState targetState = targetLevel.getBlockState(targetPos);
        if (!targetState.is(AddonBlocks.ENDERPOLE.get())
            || targetState.getValue(EnderPoleBlock.PARTHALF) != Vertical3PartHalf.TOP) {
            player.displayClientMessage(
                Component.translatable("message.anvilcraft_portable_addon.pole.invalid_target"), true
            );
            return InteractionResult.SUCCESS;
        }
        if (targetDim.equals(myDim) && targetPos.equals(topPos)) {
            player.displayClientMessage(
                Component.translatable("message.anvilcraft_portable_addon.pole.self"), true
            );
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(topPos) instanceof EnderPoleBlockEntity localPole
            && targetLevel.getBlockEntity(targetPos) instanceof EnderPoleBlockEntity targetPole) {
            // 先解除各自现有绑定，再建立互绑；物品保留绑定目标（可继续用于其它杆）
            localPole.unbind();
            targetPole.unbind();
            localPole.setBound(targetDim, targetPos);
            targetPole.setBound(myDim, topPos);
            player.displayClientMessage(
                Component.translatable("message.anvilcraft_portable_addon.pole.linked"), true
            );
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(
            Component.translatable("message.anvilcraft_portable_addon.pole.invalid_target"), true
        );
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        // 潜行 + 右键空气 → 清除绑定目标
        if (player.isShiftKeyDown()) {
            if (stack.get(DataComponents.ENDER_TARGET_DIMENSION) != null
                || stack.get(DataComponents.ENDER_TARGET_POS) != null) {
                stack.remove(DataComponents.ENDER_TARGET_DIMENSION);
                stack.remove(DataComponents.ENDER_TARGET_POS);
                player.displayClientMessage(
                    Component.translatable("message.anvilcraft_portable_addon.pole.cleared"), true
                );
                return InteractionResultHolder.success(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.get(DataComponents.ENDER_TARGET_DIMENSION) != null
            && stack.get(DataComponents.ENDER_TARGET_POS) != null;
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltipComponents,
        TooltipFlag tooltipFlag
    ) {
        ResourceKey<Level> dim = stack.get(DataComponents.ENDER_TARGET_DIMENSION);
        BlockPos pos = stack.get(DataComponents.ENDER_TARGET_POS);
        if (dim != null && pos != null) {
            tooltipComponents.add(Component.translatable(
                "message.anvilcraft_portable_addon.pole.tooltip",
                pos.getX(), pos.getY(), pos.getZ(), dim.location().toString()
            ));
        } else {
            tooltipComponents.add(
                Component.translatable("message.anvilcraft_portable_addon.pole.tooltip_empty")
            );
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
