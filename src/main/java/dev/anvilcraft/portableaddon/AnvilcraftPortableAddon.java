package dev.anvilcraft.portableaddon;

import com.mojang.logging.LogUtils;
import dev.anvilcraft.lib.v2.registrum.Registrum;
import dev.anvilcraft.lib.v2.config.ConfigManager;
import dev.anvilcraft.portableaddon.data.AddonDatagen;
import dev.anvilcraft.portableaddon.enchantment.EnchantmentEffects;
import dev.anvilcraft.portableaddon.init.AddonAmuletTypes;
import dev.anvilcraft.portableaddon.init.AddonBlocks;
import dev.anvilcraft.portableaddon.init.AddonItemGroups;
import dev.anvilcraft.portableaddon.init.AddonItems;
import dev.anvilcraft.portableaddon.init.DataComponents;
import dev.anvilcraft.portableaddon.network.Packets;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(AnvilcraftPortableAddon.MOD_ID)
public class AnvilcraftPortableAddon {
    public static final String MOD_ID = "anvilcraft_portable_addon";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final AddonConfig CONFIG = ConfigManager.register(AnvilcraftPortableAddon.MOD_ID, AddonConfig::new);
    public static final Registrum REGISTRATE = Registrum.create(MOD_ID);

    public AnvilcraftPortableAddon(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        LOGGER.info("Ciallo～(∠・ω< )⌒★");

        AddonDatagen.init();
        AddonAmuletTypes.register(modEventBus);
        AddonItems.register(modEventBus);
        DataComponents.register(modEventBus);
        Packets.init(modEventBus);
        AddonBlocks.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);

        AddonItemGroups.CREATIVE_MODE_TABS.register(modEventBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");

    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == AddonItemGroups.ADDON_TAB.getKey()) {
            event.accept(AddonItems.Magnet);
            event.accept(AddonItems.POWER_BLOCK_ITEM);
            event.accept(AddonItems.ENDERPOLE_ITEM);
            event.accept(AddonItems.ENDER_AMULET_PILLAR_ITEM);
            event.accept(AddonItems.ENDER_AMULET);

            // 雷跃附魔书（可附魔在靴子上，实现二段跳）
            var holders = event.getParameters().holders();
            var enchantments = holders.lookupOrThrow(Registries.ENCHANTMENT);

            ItemStack jumpBook = new ItemStack(Items.ENCHANTED_BOOK);
            jumpBook.enchant(enchantments.getOrThrow(EnchantmentEffects.DOUBLE_JUMP), 1);
            event.accept(jumpBook);

            // 雷行附魔书（可附魔在护腿上，实现闪现）
            ItemStack walkBook = new ItemStack(Items.ENCHANTED_BOOK);
            walkBook.enchant(enchantments.getOrThrow(EnchantmentEffects.DOUBLE_WALK), 1);
            event.accept(walkBook);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
    }

    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}