package dev.anvilcraft.enderplus.data;

import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.enderplus.data.lang.AddonLangHandler;
import dev.anvilcraft.enderplus.enchantment.EnchantmentEffects;
import net.minecraft.core.registries.Registries;

import static dev.anvilcraft.enderplus.AnvilcraftEnderplus.REGISTRATE;

public class AddonDatagen {
    /**
     * 初始化生成器
     */
    public static void init() {
        REGISTRATE.addDataGenerator(ProviderType.LANG, AddonLangHandler::init);
        REGISTRATE.getDataGenInitializer().add(
                Registries.ENCHANTMENT,
                EnchantmentEffects::bootstrap
        );
    }
}
