package dev.anvilcraft.enderplus.client;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.client.tooltip.EnderPoleTooltipProvider;
import dev.anvilcraft.enderplus.init.AddonBlocks;
import dev.anvilcraft.enderplus.init.AddonEntities;
import dev.dubhe.anvilcraft.api.tooltip.HudTooltipManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = AnvilcraftEnderplus.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AnvilcraftEnderplus.MOD_ID, value = Dist.CLIENT)
public class AnvilcraftEnderplusClient {
    public AnvilcraftEnderplusClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(KeyBindings::onRegisterKeyMappings);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AnvilcraftEnderplus.LOGGER.info("HELLO FROM CLIENT SETUP");
        AnvilcraftEnderplus.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        // 末影输电杆：与原版输电杆一致，持铁砧锤查看时显示电网信息 HUD tooltip
        HudTooltipManager.INSTANCE.registerBlockEntityTooltip(new EnderPoleTooltipProvider());
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 末影护符柱：渲染挂在柱子上的护符
        event.registerBlockEntityRenderer(
            AddonBlocks.ENDER_AMULET_PILLAR_ENTITY.get(),
            EnderAmuletPillarBlockEntityRenderer::new
        );
        // 巨型幻灵砧虚影：与巨型铁砧一致，使用原版下落方块渲染器
        event.registerEntityRenderer(
            AddonEntities.FALLING_GIANT_SPECTRAL_ANVIL.get(),
            FallingBlockRenderer::new
        );
    }
}
