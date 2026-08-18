package dev.anvilcraft.portableaddon.event;

import dev.anvilcraft.portableaddon.AnvilcraftPortableAddon;
import dev.anvilcraft.portableaddon.init.DataComponents;
import dev.anvilcraft.portableaddon.init.blocks.ender_amulet_pillar.EnderAmuletPillarBlockEntity;
import dev.anvilcraft.portableaddon.init.items.EnderAmuletItem;
import dev.dubhe.anvilcraft.api.event.AmuletEvent;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 末影护符桥接：当玩家在背包任意位置携带已绑定的末影护符时，
 * 把该护符绑定的护符柱上挂载的护符通过 {@link AmuletEvent.Find} 提供给
 * AnvilCraft 的 AmuletManager，使其效果（药水、伤害免疫、行为判定等）
 * 如同玩家直接携带这些护符一样作用于玩家。
 */
@EventBusSubscriber(modid = AnvilcraftPortableAddon.MOD_ID)
public class EnderAmuletEventHandler {

    @SubscribeEvent
    public static void onFind(AmuletEvent.Find event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        Set<String> seen = new HashSet<>();
        for (ItemStack stack : player.getInventory().items) {
            provideBoundPillarCharms(event, player, stack, seen);
        }
        for (ItemStack stack : player.getInventory().armor) {
            provideBoundPillarCharms(event, player, stack, seen);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            provideBoundPillarCharms(event, player, stack, seen);
        }
    }

    private static void provideBoundPillarCharms(
        AmuletEvent.Find event,
        ServerPlayer player,
        ItemStack stack,
        Set<String> seen
    ) {
        if (stack.isEmpty() || !(stack.getItem() instanceof EnderAmuletItem)) return;
        ResourceKey<Level> dim = stack.get(DataComponents.AMULET_BOUND_DIMENSION);
        BlockPos pos = stack.get(DataComponents.AMULET_BOUND_POS);
        if (dim == null || pos == null) return;
        String key = dim.location().toString() + "|" + pos.asLong();
        if (!seen.add(key)) return;
        Level level = player.server.getLevel(dim);
        if (level == null) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof EnderAmuletPillarBlockEntity pillar)) return;
        for (List<ItemStack> side : pillar.getAmuletsBySide().values()) {
            for (ItemStack hung : side) {
                if (hung.isEmpty() || !hung.has(ModComponents.AMULET)) continue;
                event.provide(hung.copy());
            }
        }
    }
}
