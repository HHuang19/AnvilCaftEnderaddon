package dev.anvilcraft.portableaddon.gametest;

import com.mojang.authlib.GameProfile;
import dev.anvilcraft.portableaddon.AnvilcraftPortableAddon;
import dev.anvilcraft.portableaddon.init.AddonBlocks;
import dev.anvilcraft.portableaddon.init.AddonItems;
import dev.anvilcraft.portableaddon.init.DataComponents;
import dev.anvilcraft.portableaddon.init.blocks.ender_amulet_pillar.EnderAmuletPillarBlock;
import dev.anvilcraft.portableaddon.init.blocks.ender_amulet_pillar.EnderAmuletPillarBlockEntity;
import dev.anvilcraft.portableaddon.init.blocks.ender_amulet_pillar.Vertical2PartHalf;
import dev.anvilcraft.portableaddon.init.items.EnderAmuletItem;
import dev.dubhe.anvilcraft.api.amulet.AmuletManager;
import dev.dubhe.anvilcraft.init.item.ModAmulets;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Optional;
import java.util.UUID;

/**
 * 末影护符桥接的 GameTest：
 * 玩家在主手、副手或 Curios 护身符槽中携带已绑定末影护符柱的末影护符时，
 * AnvilCraft 的 AmuletManager 应能识别到该护符柱上挂载的护符（效果经 AmuletEvent.Find 桥接提供）。
 * 背包其它格子不再生效；对空气右键可解除绑定。
 */
@GameTestHolder(AnvilcraftPortableAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public class EnderAmuletBridgeGameTest {

    @GameTest(template = "amulet_bridge")
    public void boundAmuletGrantsHungCharm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bottomAbs = placePillarWithRuby(helper);
        ServerPlayer player = mockPlayer(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, boundAmulet(level.dimension(), bottomAbs));
        helper.assertTrue(
                AmuletManager.get(level.registryAccess()).hasAmuletInInventory(player, ModAmulets.RUBY),
                "Bound Ender Amulet in main hand should grant the pillar's hung ruby amulet effect");
        helper.succeed();
    }

    @GameTest(template = "amulet_bridge")
    public void boundAmuletInOffhandGrantsHungCharm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bottomAbs = placePillarWithRuby(helper);
        ServerPlayer player = mockPlayer(level);
        player.setItemInHand(InteractionHand.OFF_HAND, boundAmulet(level.dimension(), bottomAbs));
        helper.assertTrue(
                AmuletManager.get(level.registryAccess()).hasAmuletInInventory(player, ModAmulets.RUBY),
                "Bound Ender Amulet in off hand should grant the pillar's hung ruby amulet effect");
        helper.succeed();
    }

    @GameTest(template = "amulet_bridge")
    public void boundAmuletInMainInventoryGrantsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bottomAbs = placePillarWithRuby(helper);
        ServerPlayer player = mockPlayer(level);
        player.getInventory().setItem(9, boundAmulet(level.dimension(), bottomAbs));
        helper.assertTrue(
                !AmuletManager.get(level.registryAccess()).hasAmuletInInventory(player, ModAmulets.RUBY),
                "Bound Ender Amulet in the main inventory should NOT grant the pillar's charms");
        helper.succeed();
    }

    @GameTest(template = "amulet_bridge")
    public void unboundAmuletGrantsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = mockPlayer(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AddonItems.ENDER_AMULET.get()));
        helper.assertTrue(
                !AmuletManager.get(level.registryAccess()).hasAmuletInInventory(player, ModAmulets.RUBY),
                "Unbound Ender Amulet should not grant any pillar charms");
        helper.succeed();
    }

    @GameTest(template = "amulet_bridge")
    public void airRightClickUnbindsAmulet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bottomAbs = placePillarWithRuby(helper);
        ServerPlayer player = mockPlayer(level);
        ItemStack amulet = boundAmulet(level.dimension(), bottomAbs);
        player.setItemInHand(InteractionHand.MAIN_HAND, amulet);
        ((EnderAmuletItem) amulet.getItem()).use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                amulet.get(DataComponents.AMULET_BOUND_DIMENSION) == null
                        && amulet.get(DataComponents.AMULET_BOUND_POS) == null,
                "Right-click on air should clear the amulet's binding");
        helper.assertTrue(
                !AmuletManager.get(level.registryAccess()).hasAmuletInInventory(player, ModAmulets.RUBY),
                "Unbound amulet should no longer grant the pillar's charms");
        helper.succeed();
    }

    @GameTest(template = "amulet_bridge")
    public void boundAmuletInCuriosCharmSlotGrantsHungCharm(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos bottomAbs = placePillarWithRuby(helper);
        ServerPlayer player = mockPlayer(level);
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(player);
        if (inventory.isEmpty()) {
            helper.fail("Mock player should have a Curios inventory when Curios is loaded");
            return;
        }
        Optional<ICurioStacksHandler> charm = inventory.get().getStacksHandler("charm");
        if (charm.isEmpty()) {
            helper.fail("Mock player should have a Curios charm slot");
            return;
        }
        inventory.get().setEquippedCurio("charm", 0, boundAmulet(level.dimension(), bottomAbs));
        helper.assertTrue(
                AmuletManager.get(level.registryAccess()).hasAmuletInInventory(player, ModAmulets.RUBY),
                "Bound Ender Amulet in a Curios charm slot should grant the pillar's hung ruby amulet effect");
        helper.succeed();
    }

    @GameTest(template = "amulet_bridge")
    public void boundAmuletGrantsHungCharmCrossDimension(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel end = level.getServer().getLevel(Level.END);
        if (end == null) {
            helper.fail("End dimension missing");
            return;
        }
        BlockPos endPos = new BlockPos(2000, 64, 2000);
        end.setBlock(endPos, AddonBlocks.ENDER_AMULET_PILLAR.get().defaultBlockState(), 3);
        end.setBlock(endPos.above(), AddonBlocks.ENDER_AMULET_PILLAR.get().defaultBlockState()
                .setValue(EnderAmuletPillarBlock.PARTHALF, Vertical2PartHalf.TOP), 3);
        if (end.getBlockEntity(endPos) instanceof EnderAmuletPillarBlockEntity be) {
            be.tryHangAmulet(Direction.NORTH, new ItemStack(ModItems.RUBY_AMULET.get()));
        } else {
            helper.fail("End pillar block entity missing");
            return;
        }

        ServerPlayer player = mockPlayer(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, boundAmulet(Level.END, endPos));

        helper.assertTrue(
                AmuletManager.get(level.registryAccess()).hasAmuletInInventory(player, ModAmulets.RUBY),
                "Bound amulet should grant the pillar's charms across dimensions");
        helper.assertTrue(
                end.getForcedChunks().contains(ChunkPos.asLong(endPos)),
                "The End pillar chunk should be force-loaded");
        helper.succeed();
    }

    /** 在测试模板 (1,0,1) 处放置末影护符柱并在北侧挂上一个红宝石护符，返回主部件（底部）坐标。 */
    private static BlockPos placePillarWithRuby(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bottomRel = new BlockPos(1, 0, 1);
        helper.setBlock(bottomRel, AddonBlocks.ENDER_AMULET_PILLAR.get().defaultBlockState());
        helper.setBlock(bottomRel.above(), AddonBlocks.ENDER_AMULET_PILLAR.get().defaultBlockState()
                .setValue(EnderAmuletPillarBlock.PARTHALF, Vertical2PartHalf.TOP));
        BlockPos bottomAbs = helper.absolutePos(bottomRel);
        if (!(level.getBlockEntity(bottomAbs) instanceof EnderAmuletPillarBlockEntity be)) {
            helper.fail("Pillar block entity missing at " + bottomAbs.toShortString());
            return bottomAbs;
        }
        be.tryHangAmulet(Direction.NORTH, new ItemStack(ModItems.RUBY_AMULET.get()));
        return bottomAbs;
    }

    private static ItemStack boundAmulet(ResourceKey<Level> dimension, BlockPos pos) {
        ItemStack amulet = new ItemStack(AddonItems.ENDER_AMULET.get());
        amulet.set(DataComponents.AMULET_BOUND_DIMENSION, dimension);
        amulet.set(DataComponents.AMULET_BOUND_POS, pos);
        return amulet;
    }

    /**
     * 直接构造一个不登入世界的 ServerPlayer。
     * 不使用 {@link GameTestHelper#makeMockServerPlayerInLevel()}：它会走
     * {@link net.minecraft.server.players.PlayerList#placeNewPlayer} 完整登录流程，
     * AnvilCraft 会向该模拟玩家发送 gravity_sources_sync 数据包而崩溃。
     */
    private static ServerPlayer mockPlayer(ServerLevel level) {
        return new ServerPlayer(
                level.getServer(),
                level,
                new GameProfile(UUID.randomUUID(), "test-mock-player"),
                ClientInformation.createDefault()
        );
    }
}
