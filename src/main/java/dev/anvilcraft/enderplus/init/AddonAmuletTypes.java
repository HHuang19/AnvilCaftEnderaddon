package dev.anvilcraft.enderplus.init;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.init.items.EnderAmulet;
import dev.dubhe.anvilcraft.init.registry.ModRegistryKeys;
import dev.dubhe.anvilcraft.item.property.component.amulet.IAmulet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 自定义护符类型注册类
 * 向 AnvilCraft 的护符类型注册表 (AMULET_TYPE_KEY) 注册本模组新增的护符类型
 */
public class AddonAmuletTypes {
    private static final DeferredRegister<IAmulet.Type<?>> REGISTER = DeferredRegister.create(
            ModRegistryKeys.AMULET_TYPE,
            AnvilcraftEnderplus.MOD_ID
    );

    // 末影护符类型（无效果）
    public static final DeferredHolder<IAmulet.Type<?>, EnderAmulet.Type> ENDER = REGISTER.register(
            "ender",
            EnderAmulet.Type::new
    );

    /**
     * 初始化护符类型注册
     *
     * @param modEventBus 模组专用的 FML/NeoForge 事件总线
     */
    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }
}
