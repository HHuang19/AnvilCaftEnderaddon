package dev.anvilcraft.portableaddon.init;

import com.mojang.serialization.Codec;
import dev.anvilcraft.portableaddon.AnvilcraftPortableAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class DataComponents {//用于注册数据类型

    //注册Anvil_state用于在物品里存放铁砧
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AnvilcraftPortableAddon.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockState>> ANVIL_STATE =
            DATA_COMPONENTS.register("anvil_state", () -> DataComponentType.<BlockState>builder()
                    .persistent(BlockState.CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> CAN_DOUBLE_JUMP =
            DATA_COMPONENTS.register("can_double_jump", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .build());

    // 末影输电杆物品：记录绑定目标杆的维度与顶段坐标（相同目标可堆叠）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceKey<Level>>> ENDER_TARGET_DIMENSION =
            DATA_COMPONENTS.register("ender_target_dimension", () -> DataComponentType.<ResourceKey<Level>>builder()
                    .persistent(ResourceKey.codec(Registries.DIMENSION))
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> ENDER_TARGET_POS =
            DATA_COMPONENTS.register("ender_target_pos", () -> DataComponentType.<BlockPos>builder()
                    .persistent(BlockPos.CODEC)
                    .build());

    // 末影护符物品：记录其绑定的末影护符柱的维度与主部件坐标
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceKey<Level>>> AMULET_BOUND_DIMENSION =
            DATA_COMPONENTS.register("amulet_bound_dimension", () -> DataComponentType.<ResourceKey<Level>>builder()
                    .persistent(ResourceKey.codec(Registries.DIMENSION))
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> AMULET_BOUND_POS =
            DATA_COMPONENTS.register("amulet_bound_pos", () -> DataComponentType.<BlockPos>builder()
                    .persistent(BlockPos.CODEC)
                    .build());

    public static void register(IEventBus modEventBus) {
        DataComponents.DATA_COMPONENTS.register(modEventBus);
    }
}
