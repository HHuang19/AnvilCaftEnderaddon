package dev.anvilcraft.enderplus.event;

import dev.anvilcraft.enderplus.init.items.EnderAmuletItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.List;

/**
 * Curios 可选集成：把玩家 Curios 槽中的末影护符收集到给定列表。
 * 本类引用了 Curios API，只有在检测到 Curios 已加载时才会被调用，
 * 因此未安装 Curios 时不会触发类加载错误（软依赖隔离）。
 */
public final class EnderAmuletCuriosCompat {

    private EnderAmuletCuriosCompat() {
    }

    public static void collectEnderAmulets(Player player, List<ItemStack> out) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (SlotResult result : handler.findCurios(stack -> stack.getItem() instanceof EnderAmuletItem)) {
                out.add(result.stack());
            }
        });
    }
}
