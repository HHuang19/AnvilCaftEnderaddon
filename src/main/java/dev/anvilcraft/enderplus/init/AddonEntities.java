package dev.anvilcraft.enderplus.init;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.init.blocks.giant_spectral_anvil.FallingGiantSpectralAnvilEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AddonEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AnvilcraftEnderplus.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<FallingGiantSpectralAnvilEntity>>
            FALLING_GIANT_SPECTRAL_ANVIL = ENTITY_TYPES.register(
                "falling_giant_spectral_anvil",
                () -> EntityType.Builder.<FallingGiantSpectralAnvilEntity>of(
                        FallingGiantSpectralAnvilEntity::new,
                        MobCategory.MISC
                    )
                    .sized(0.98F, 0.98F)
                    .clientTrackingRange(80)
                    .updateInterval(1)
                    .build("falling_giant_spectral_anvil")
            );

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
