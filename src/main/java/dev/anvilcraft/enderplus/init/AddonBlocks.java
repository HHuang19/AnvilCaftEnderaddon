package dev.anvilcraft.enderplus.init;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.init.blocks.PowerBlock.PowerBlock;
import dev.anvilcraft.enderplus.init.blocks.PowerBlock.PowerBlockEntity;
import dev.anvilcraft.enderplus.init.blocks.ender_amulet_pillar.EnderAmuletPillarBlock;
import dev.anvilcraft.enderplus.init.blocks.ender_amulet_pillar.EnderAmuletPillarBlockEntity;
import dev.anvilcraft.enderplus.init.blocks.ender_transmission_pole.EnderPoleBlock;
import dev.anvilcraft.enderplus.init.blocks.ender_transmission_pole.EnderPoleBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AddonBlocks {

    // 1. 声明你自己的方块注册器
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AnvilcraftEnderplus.MOD_ID);

    // 2. 声明你自己的方块实体注册器
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AnvilcraftEnderplus.MOD_ID);

    // 3. 注册方块
    public static final DeferredHolder<Block, PowerBlock> POWER_BLOCK =
            BLOCKS.register("power_block", PowerBlock::new);

    public static final DeferredHolder<Block, EnderPoleBlock> ENDERPOLE =
            BLOCKS.register("ender_pole", EnderPoleBlock::new);

    public static final DeferredHolder<Block, EnderAmuletPillarBlock> ENDER_AMULET_PILLAR =
            BLOCKS.register("ender_amulet_pillar", EnderAmuletPillarBlock::new);

    // 4. 注册对应的方块实体
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerBlockEntity>> APOWER_BE =
            BLOCK_ENTITIES.register("power_block_entity", () ->
                    BlockEntityType.Builder.of(PowerBlockEntity::new, POWER_BLOCK.get()).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnderPoleBlockEntity>> ENDER_POLE_ENTITY =
            BLOCK_ENTITIES.register("ender_pole_entity", () ->
                    BlockEntityType.Builder.of(EnderPoleBlockEntity::new, ENDERPOLE.get()).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnderAmuletPillarBlockEntity>> ENDER_AMULET_PILLAR_ENTITY =
            BLOCK_ENTITIES.register("ender_amulet_pillar_entity", () ->
                    BlockEntityType.Builder.of(EnderAmuletPillarBlockEntity::new, ENDER_AMULET_PILLAR.get()).build(null)
            );

    // 5. 核心：总线事件绑定，必须绑定你自己的 BLOCKS 和 BLOCK_ENTITIES
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}