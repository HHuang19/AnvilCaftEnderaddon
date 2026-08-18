package dev.anvilcraft.portableaddon.init.items;

import dev.anvilcraft.portableaddon.init.AddonBlocks;
import dev.anvilcraft.portableaddon.init.DataComponents;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 末影护符物品
 * 潜行右键末影护符柱时把护符绑定到该柱子（在护符上记录柱子的维度与主部件坐标）。
 * <p>
 * 原版机制下，潜行 + 手持物品右键方块会跳过方块交互、改而调用物品的 useOn，
 * 因此绑定逻辑放在这里；非潜行右键柱子仍走方块交互（挂载/取下护符）。
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
                Component.translatable("message.anvilcraft_portable_addon.amulet.already_bound"), true
            );
            return InteractionResult.SUCCESS;
        }
        stack.set(DataComponents.AMULET_BOUND_DIMENSION, dim);
        stack.set(DataComponents.AMULET_BOUND_POS, mainPos);
        player.displayClientMessage(
            Component.translatable(
                "message.anvilcraft_portable_addon.amulet.bound",
                mainPos.getX(), mainPos.getY(), mainPos.getZ()
            ),
            true
        );
        return InteractionResult.SUCCESS;
    }
}
