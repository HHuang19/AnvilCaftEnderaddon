package dev.anvilcraft.enderplus.init.blocks.ender_transmission_pole;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 末影输电杆的跨维度区块强加载器。
 * <p>
 * 当玩家把两根（或多根）末影输电杆互绑成一个共享电网时，按配置
 * {@link AddonConfig#crossDimensionChunkLoad}（默认开启）把绑定两端所在的区块
 * 强加载，从而在没有玩家靠近的前提下也能实现真正的跨维度输电。
 * <p>
 * 使用引用计数：同一区块可能被多条绑定同时加载，只有所有引用都释放后才会取消强加载，
 * 避免提前卸载导致另一位持绑定的杆断联。
 */
@EventBusSubscriber(modid = AnvilcraftEnderplus.MOD_ID)
public final class EnderPoleChunkLoader {

    private EnderPoleChunkLoader() {
    }

    private record Ref(ResourceKey<Level> dimension, long chunkPos) {
    }

    private static final Map<Ref, Integer> FORCED = new HashMap<>();

    /** 强加载 (dimension, chunkPos) 对应的区块；引用计数 +1。 */
    public static void force(MinecraftServer server, ResourceKey<Level> dimension, ChunkPos pos) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return;
        Ref ref = new Ref(dimension, pos.toLong());
        int n = FORCED.getOrDefault(ref, 0);
        if (n == 0) {
            level.setChunkForced(pos.x, pos.z, true);
        }
        FORCED.put(ref, n + 1);
    }

    /** 释放 (dimension, chunkPos) 对应区块的一处引用；引用归零后取消强加载。 */
    public static void release(MinecraftServer server, ResourceKey<Level> dimension, ChunkPos pos) {
        Ref ref = new Ref(dimension, pos.toLong());
        int n = FORCED.getOrDefault(ref, 0);
        if (n <= 1) {
            if (n == 1) {
                ServerLevel level = server.getLevel(dimension);
                if (level != null) {
                    level.setChunkForced(pos.x, pos.z, false);
                }
            }
            FORCED.remove(ref);
        } else {
            FORCED.put(ref, n - 1);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (Ref ref : FORCED.keySet()) {
            ServerLevel level = server.getLevel(ref.dimension());
            if (level == null) continue;
            ChunkPos pos = new ChunkPos(ref.chunkPos());
            level.setChunkForced(pos.x, pos.z, false);
        }
        FORCED.clear();
    }

    /** GameTest 观察用：当前仍未被释放的强加载引用总数（用于断言解绑后无泄漏）。 */
    static int totalRefs() {
        int total = 0;
        for (int n : FORCED.values()) {
            total += n;
        }
        return total;
    }
}
