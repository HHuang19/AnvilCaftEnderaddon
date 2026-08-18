package dev.anvilcraft.portableaddon.event;

import dev.anvilcraft.portableaddon.AnvilcraftPortableAddon;
import dev.anvilcraft.portableaddon.init.DataComponents;
import dev.anvilcraft.portableaddon.init.blocks.ender_amulet_pillar.EnderAmuletPillarBlockEntity;
import dev.anvilcraft.portableaddon.init.items.EnderAmuletItem;
import dev.dubhe.anvilcraft.api.event.AmuletEvent;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 末影护符桥接：当玩家在主手、副手或 Curios 护身符槽中持有已绑定的末影护符时，
 * 把该护符绑定的护符柱上挂载的护符通过 {@link AmuletEvent.Find} 提供给
 * AnvilCraft 的 AmuletManager，使其效果（药水、伤害免疫、行为判定等）
 * 如同玩家直接携带这些护符一样作用于玩家。
 * <p>
 * 跨维度时若护符柱所在区块未加载，会按配置 {@link AddonConfig#crossDimensionChunkLoad}
 * 强加载该区块（{@link ServerLevel#setChunkForced}），使效果持续生效；
 * 玩家停止持有 / 登出 / 服务器停止时释放强加载，避免区块持久化残留。
 */
@EventBusSubscriber(modid = AnvilcraftPortableAddon.MOD_ID)
public class EnderAmuletEventHandler {

    /** 每个玩家当前强加载的 (维度, 区块) 集合 */
    private static final Map<UUID, Set<ChunkRef>> FORCED_CHUNKS = new HashMap<>();

    private record ChunkRef(ResourceKey<Level> dimension, ChunkPos pos) {
    }

    private record PillarRef(ResourceKey<Level> dimension, BlockPos pos) {
    }

    @SubscribeEvent
    public static void onFind(AmuletEvent.Find event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        Map<String, PillarRef> pillars = new LinkedHashMap<>();
        List<ItemStack> sources = new ArrayList<>();
        sources.add(player.getMainHandItem());
        sources.add(player.getOffhandItem());
        if (ModList.get().isLoaded("curios")) {
            EnderAmuletCuriosCompat.collectEnderAmulets(player, sources);
        }
        for (ItemStack stack : sources) {
            collectBoundPillar(stack, pillars);
        }

        boolean chunkLoad = AnvilcraftPortableAddon.CONFIG.crossDimensionChunkLoad;
        Set<ChunkRef> needed = new HashSet<>();
        for (PillarRef pillar : pillars.values()) {
            ServerLevel level = player.server.getLevel(pillar.dimension());
            if (level == null) continue;
            ChunkRef chunkRef = new ChunkRef(pillar.dimension(), new ChunkPos(pillar.pos()));
            needed.add(chunkRef);
            if (chunkLoad) {
                forceChunk(player, level, chunkRef);
            }
            if (!level.isLoaded(pillar.pos())) continue;
            providePillarCharms(event, level, pillar.pos());
        }
        releaseStale(player, needed, chunkLoad);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        releaseAll(player.server, player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        releaseAll(event.getServer(), null);
    }

    private static void collectBoundPillar(ItemStack stack, Map<String, PillarRef> pillars) {
        if (stack.isEmpty() || !(stack.getItem() instanceof EnderAmuletItem)) return;
        ResourceKey<Level> dim = stack.get(DataComponents.AMULET_BOUND_DIMENSION);
        BlockPos pos = stack.get(DataComponents.AMULET_BOUND_POS);
        if (dim == null || pos == null) return;
        pillars.putIfAbsent(dim.location().toString() + "|" + pos.asLong(), new PillarRef(dim, pos));
    }

    private static void providePillarCharms(AmuletEvent.Find event, Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof EnderAmuletPillarBlockEntity pillar)) return;
        for (List<ItemStack> side : pillar.getAmuletsBySide().values()) {
            for (ItemStack hung : side) {
                if (hung.isEmpty() || !hung.has(ModComponents.AMULET)) continue;
                event.provide(hung.copy());
            }
        }
    }

    private static void forceChunk(ServerPlayer player, ServerLevel level, ChunkRef ref) {
        if (FORCED_CHUNKS.computeIfAbsent(player.getUUID(), key -> new HashSet<>()).add(ref)) {
            level.setChunkForced(ref.pos().x, ref.pos().z, true);
        }
    }

    private static void releaseStale(ServerPlayer player, Set<ChunkRef> needed, boolean chunkLoadEnabled) {
        Set<ChunkRef> owned = FORCED_CHUNKS.get(player.getUUID());
        if (owned == null) return;
        owned.removeIf(ref -> {
            if (chunkLoadEnabled && needed.contains(ref)) return false;
            releaseChunk(player.server, ref);
            return true;
        });
    }

    private static void releaseAll(MinecraftServer server, UUID playerId) {
        if (playerId != null) {
            Set<ChunkRef> owned = FORCED_CHUNKS.remove(playerId);
            if (owned == null) return;
            owned.forEach(ref -> releaseChunk(server, ref));
            return;
        }
        FORCED_CHUNKS.values().forEach(owned -> owned.forEach(ref -> releaseChunk(server, ref)));
        FORCED_CHUNKS.clear();
    }

    private static void releaseChunk(MinecraftServer server, ChunkRef ref) {
        ServerLevel level = server.getLevel(ref.dimension());
        if (level != null) {
            level.setChunkForced(ref.pos().x, ref.pos().z, false);
        }
    }
}
