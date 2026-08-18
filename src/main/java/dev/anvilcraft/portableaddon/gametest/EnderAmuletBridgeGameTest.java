package dev.anvilcraft.portableaddon.gametest;

import com.mojang.authlib.GameProfile;
import dev.anvilcraft.portableaddon.AnvilcraftPortableAddon;
import dev.anvilcraft.portableaddon.init.AddonBlocks;
import dev.anvilcraft.portableaddon.init.AddonItems;
import dev.anvilcraft.portableaddon.init.DataComponents;
import dev.anvilcraft.portableaddon.init.blocks.ender_amulet_pillar.EnderAmuletPillarBlock;
import dev.anvilcraft.portableaddon.init.blocks.ender_amulet_pillar.EnderAmuletPillarBlockEntity;
import dev.anvilcraft.portableaddon.init.blocks.ender_amulet_pillar.Vertical2PartHalf;
import dev.dubhe.anvilcraft.api.amulet.AmuletManager;
import dev.dubhe.anvilcraft.init.item.ModAmulets;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * 末影护符桥接的 GameTest：
 * 玩家在背包携带已绑定末影护符柱的末影护符时，AnvilCraft 的 AmuletManager
 * 应能识别到该护符柱上挂载的护符（效果经 AmuletEvent.Find 桥接提供）。
 */
@GameTestHolder(AnvilcraftPortableAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public class EnderAmuletBridgeGameTest {

    @GameTest(template = "amulet_bridge")
    public void boundAmuletGrantsHungCharm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 先放 BOTTOM 再放 TOP：方块实体只在 BOTTOM 主部件上创建
        BlockPos bottomRel = new BlockPos(1, 0, 1);
        helper.setBlock(bottomRel, AddonBlocks.ENDER_AMULET_PILLAR.get().defaultBlockState());
        helper.setBlock(bottomRel.above(), AddonBlocks.ENDER_AMULET_PILLAR.get().defaultBlockState()
                .setValue(EnderAmuletPillarBlock.PARTHALF, Vertical2PartHalf.TOP));

        BlockPos bottomAbs = helper.absolutePos(bottomRel);
        if (level.getBlockEntity(bottomAbs) instanceof EnderAmuletPillarBlockEntity be) {
            be.tryHangAmulet(Direction.NORTH, new ItemStack(ModItems.RUBY_AMULET.get()));
        } else {
            helper.fail("Pillar block entity missing at " + bottomAbs.toShortString());
            return;
        }

        ServerPlayer player = mockPlayer(level);
        ItemStack amulet = new ItemStack(AddonItems.ENDER_AMULET.get());
        amulet.set(DataComponents.AMULET_BOUND_DIMENSION, level.dimension());
        amulet.set(DataComponents.AMULET_BOUND_POS, bottomAbs);
        player.getInventory().add(amulet);

        helper.assertTrue(
                AmuletManager.get(level.registryAccess()).hasAmuletInInventory(player, ModAmulets.RUBY),
                "Bound Ender Amulet should grant the pillar's hung ruby amulet effect");
        helper.succeed();
    }

    @GameTest(template = "amulet_bridge")
    public void unboundAmuletGrantsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = mockPlayer(level);
        player.getInventory().add(new ItemStack(AddonItems.ENDER_AMULET.get()));
        helper.assertTrue(
                !AmuletManager.get(level.registryAccess()).hasAmuletInInventory(player, ModAmulets.RUBY),
                "Unbound Ender Amulet should not grant any pillar charms");
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
        ItemStack amulet = new ItemStack(AddonItems.ENDER_AMULET.get());
        amulet.set(DataComponents.AMULET_BOUND_DIMENSION, Level.END);
        amulet.set(DataComponents.AMULET_BOUND_POS, endPos);
        player.getInventory().add(amulet);

        helper.assertTrue(
                AmuletManager.get(level.registryAccess()).hasAmuletInInventory(player, ModAmulets.RUBY),
                "Bound amulet should grant the pillar's charms across dimensions");
        helper.assertTrue(
                end.getForcedChunks().contains(ChunkPos.asLong(endPos)),
                "The End pillar chunk should be force-loaded");
        helper.succeed();
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
