package dev.anvilcraft.enderplus.event;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.init.AddonBlocks;
import dev.anvilcraft.enderplus.init.blocks.giant_spectral_anvil.GiantSpectralAnvilBlock;
import dev.dubhe.anvilcraft.api.event.EntityThroughPortalEvent;
import dev.dubhe.anvilcraft.api.portal.PortalType;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.block.state.GiantAnvilCube;
import dev.dubhe.anvilcraft.entity.FallingGiantAnvilEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 巨型铁砧过末地传送门的概率转化：
 * <ul>
 *     <li>20% 概率变成巨型幻灵砧（下落实体携带其主部件方块状态穿越，落地后铺成完整结构）；</li>
 *     <li>否则再掷一次：50% 概率变成末影尘埃（anvilcraft 的 END_DUST），50% 保持巨型铁砧原样；</li>
 * </ul>
 * 先于 anvilcraft 的 PortalEventListener（默认会将未豁免方块转为对应尘埃）执行。
 * “保持原样”与“巨型幻灵砧”两个分支能免于被默认逻辑再次覆盖，依赖 datapack 对
 * {@code anvilcraft:end_portal_unable_change} 的追加（见 data/anvilcraft/tags/block/）。
 */
@EventBusSubscriber(modid = AnvilcraftEnderplus.MOD_ID)
public class GiantSpectralPortalListener {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onThroughPortal(EntityThroughPortalEvent event) {
        if (!(event.getEntity() instanceof FallingGiantAnvilEntity entity)) return;
        if (entity.anvilcraft$isSpectral()) return;
        if (event.getType() != PortalType.END_PORTAL) return;
        if (!entity.blockState.is(ModBlocks.GIANT_ANVIL.get())) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        float random = level.random.nextFloat();
        if (random < 0.2F) {
            // 20%：变成巨型幻灵砧（主部件 MID_CENTER + 完整模型 CUBE=CENTER）
            entity.blockState = AddonBlocks.GIANT_SPECTRAL_ANVIL.get()
                .defaultBlockState()
                .setValue(GiantSpectralAnvilBlock.HALF, Cube3x3PartHalf.MID_CENTER)
                .setValue(GiantSpectralAnvilBlock.CUBE, GiantAnvilCube.CENTER);
        } else if (random < 0.6F) {
            // (1-20%)*50% = 40%：变成末影尘埃
            entity.blockState = ModBlocks.END_DUST.get().defaultBlockState();
        }
        // 其余 40%：保持巨型铁砧原样（由 end_portal_unable_change 豁免默认尘埃化）
    }
}
