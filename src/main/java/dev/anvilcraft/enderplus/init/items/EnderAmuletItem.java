package dev.anvilcraft.enderplus.init.items;

import dev.anvilcraft.enderplus.init.AddonBlocks;
import dev.anvilcraft.enderplus.init.DataComponents;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 末影护符物品
 * 潜行右键末影护符柱时把护符绑定到该柱子（在护符上记录柱子的维度与主部件坐标）。
 * <p>
 * 原版机制下，潜行 + 手持物品右键方块会跳过方块交互、改而调用物品的 useOn，
 * 因此绑定逻辑放在这里；非潜行右键柱子仍走方块交互（挂载/取下护符）。
 * <p>
 * 对空气右键（不瞄准方块）会解除当前绑定，用于换绑或归还柱子。
 */
public class EnderAmuletItem extends Item {

    public EnderAmuletItem() {
        super(new Item.Properties().stacksTo(1)
                .component(ModComponents.AMULET, EnderAmulet.INSTANCE));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        if (!state.is(AddonBlocks.ENDER_AMULET_PILLAR.get())) {
            return super.useOn(context);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        BlockPos mainPos = AddonBlocks.ENDER_AMULET_PILLAR.get().getMainPartPos(context.getClickedPos(), state);

        ResourceKey<Level> dim = level.dimension();
        if (dim.equals(stack.get(DataComponents.AMULET_BOUND_DIMENSION))
            && mainPos.equals(stack.get(DataComponents.AMULET_BOUND_POS))) {
            player.displayClientMessage(
                Component.translatable("message.anvilcraft_enderplus.amulet.already_bound"), true
            );
            return InteractionResult.SUCCESS;
        }
        stack.set(DataComponents.AMULET_BOUND_DIMENSION, dim);
        stack.set(DataComponents.AMULET_BOUND_POS, mainPos);
        player.displayClientMessage(
            Component.translatable(
                "message.anvilcraft_enderplus.amulet.bound",
                mainPos.getX(), mainPos.getY(), mainPos.getZ()
            ),
            true
        );
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.get(DataComponents.AMULET_BOUND_DIMENSION) == null
            || stack.get(DataComponents.AMULET_BOUND_POS) == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        stack.remove(DataComponents.AMULET_BOUND_DIMENSION);
        stack.remove(DataComponents.AMULET_BOUND_POS);
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            serverPlayer.displayClientMessage(
                Component.translatable("message.anvilcraft_enderplus.amulet.unbound"), true
            );
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.get(DataComponents.AMULET_BOUND_DIMENSION) != null
            && stack.get(DataComponents.AMULET_BOUND_POS) != null;
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltipComponents,
        TooltipFlag tooltipFlag
    ) {
        ResourceKey<Level> dim = stack.get(DataComponents.AMULET_BOUND_DIMENSION);
        BlockPos pos = stack.get(DataComponents.AMULET_BOUND_POS);
        if (dim != null && pos != null) {
            tooltipComponents.add(Component.translatable(
                "message.anvilcraft_enderplus.amulet.tooltip",
                pos.getX(), pos.getY(), pos.getZ(), dim.location().toString()
            ));
        } else {
            tooltipComponents.add(
                Component.translatable("message.anvilcraft_enderplus.amulet.tooltip_empty")
            );
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
