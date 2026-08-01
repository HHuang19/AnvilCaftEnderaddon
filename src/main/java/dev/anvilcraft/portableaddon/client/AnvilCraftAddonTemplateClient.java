package dev.anvilcraft.portableaddon.client;

import dev.anvilcraft.portableaddon.AnvilcraftPortableAddon;
import dev.anvilcraft.portableaddon.client.tooltip.EnderPoleTooltipProvider;
import dev.dubhe.anvilcraft.api.tooltip.HudTooltipManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = AnvilcraftPortableAddon.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AnvilcraftPortableAddon.MOD_ID, value = Dist.CLIENT)
public class AnvilCraftAddonTemplateClient {
    public AnvilCraftAddonTemplateClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(KeyBindings::onRegisterKeyMappings);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AnvilcraftPortableAddon.LOGGER.info("HELLO FROM CLIENT SETUP");
        AnvilcraftPortableAddon.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        // 末影输电杆：与原版输电杆一致，持铁砧锤查看时显示电网信息 HUD tooltip
        HudTooltipManager.INSTANCE.registerBlockEntityTooltip(new EnderPoleTooltipProvider());
    }
}
