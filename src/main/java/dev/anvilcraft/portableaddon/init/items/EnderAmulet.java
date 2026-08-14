package dev.anvilcraft.portableaddon.init.items;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.portableaddon.init.AddonAmuletTypes;
import dev.dubhe.anvilcraft.item.property.component.amulet.IAmulet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 末影护符
 * 一个没有任何效果的护符，仅作为可挂在末影护符柱上的装饰性护符
 */
public record EnderAmulet() implements IAmulet {
    public static final EnderAmulet INSTANCE = new EnderAmulet();

    @Override
    public Type getType() {
        return AddonAmuletTypes.ENDER.get();
    }

    @Override
    public boolean canActAs(IAmulet other) {
        return other instanceof EnderAmulet;
    }

    public static class Type implements IAmulet.Type<EnderAmulet> {
        public static final MapCodec<EnderAmulet> CODEC = MapCodec.unit(EnderAmulet.INSTANCE);
        public static final StreamCodec<ByteBuf, EnderAmulet> STREAM_CODEC = StreamCodec.unit(EnderAmulet.INSTANCE);

        @Override
        public MapCodec<EnderAmulet> codec() {
            return Type.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, EnderAmulet> streamCodec() {
            return Type.STREAM_CODEC.cast();
        }
    }
}
