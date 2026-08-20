package dev.anvilcraft.enderplus.init;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AddonItemGroups {
    // 1. 创建物品栏延迟注册器
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AnvilcraftEnderplus.MOD_ID);

    // 2. 注册创造物品栏标签页
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ADDON_TAB =
            CREATIVE_MODE_TABS.register("addon_tab", () -> CreativeModeTab.builder()
                    // 这里直接用系统的铁砧（Items.ANVIL）做图标
                    .icon(() -> new ItemStack(net.minecraft.world.item.Items.ANVIL))
                    .title(Component.translatable("creativetab.addon"))
                    .build()
            );
}