package dev.anvilcraft.enderplus.enchantment;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.enchantment.Enchantment;

public class EnchantmentEffects {
    public static final ResourceKey<Enchantment> DOUBLE_JUMP = ResourceKey.create(
            Registries.ENCHANTMENT,
            AnvilcraftEnderplus.of("double_jump")
    );
    public static final ResourceKey<Enchantment> DOUBLE_WALK = ResourceKey.create(
            Registries.ENCHANTMENT,
            AnvilcraftEnderplus.of("double_walk")
    );

    public static void bootstrap(BootstrapContext<Enchantment> context) {
        var items = context.lookup(Registries.ITEM);

        context.register(DOUBLE_JUMP, Enchantment.enchantment(
                Enchantment.definition(
                        items.getOrThrow(ItemTags.FOOT_ARMOR),
                        5, 2,
                        Enchantment.constantCost(15),
                        Enchantment.constantCost(35),
                        1,
                        EquipmentSlotGroup.FEET
                )
        ).build(DOUBLE_JUMP.location()));

        context.register(DOUBLE_WALK, Enchantment.enchantment(
                Enchantment.definition(
                        items.getOrThrow(ItemTags.LEG_ARMOR),
                        5, 3,
                        Enchantment.constantCost(15),
                        Enchantment.constantCost(35),
                        1,
                        EquipmentSlotGroup.LEGS
                )
        ).build(DOUBLE_WALK.location()));
    }
}
