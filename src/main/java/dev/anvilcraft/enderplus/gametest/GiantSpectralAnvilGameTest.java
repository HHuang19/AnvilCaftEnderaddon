package dev.anvilcraft.enderplus.gametest;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.init.AddonBlocks;
import dev.anvilcraft.enderplus.init.blocks.giant_spectral_anvil.FallingGiantSpectralAnvilEntity;
import dev.anvilcraft.enderplus.init.blocks.giant_spectral_anvil.GiantSpectralAnvilBlock;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.api.event.EntityThroughPortalEvent;
import dev.dubhe.anvilcraft.api.portal.PortalType;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.block.state.GiantAnvilCube;
import dev.dubhe.anvilcraft.entity.FallingGiantAnvilEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 巨型幻灵砧机制 GameTest：
 * <ol>
 *     <li>固定不落：无托举、无支撑时结构保持原位，不产生虚影；</li>
 *     <li>磁铁托举 + 消磁释放：磁铁（有磁力）置于锚格上方第 3 格使 POWERED=true，
 *         移除磁铁（消磁沿）后释放 3x3 虚影，虚影落地触发等效 10 格巨砧事件；</li>
 *     <li>传送门转化分布：300 次末地门事件统计 20% 幻灵砧 / 40% 尘埃 / 40% 保持；</li>
 *     <li>真实跨维度传送：真实巨砧实体穿过末地门后离开主世界（事件 + 移除 + 不落地）。</li>
 * </ol>
 */
@GameTestHolder(AnvilcraftEnderplus.MOD_ID)
@PrefixGameTestTemplate(false)
public class GiantSpectralAnvilGameTest {

    /** 测试模板内的锚格（BOTTOM_CENTER）相对坐标。 */
    private static final BlockPos ANCHOR_REL = new BlockPos(1, 0, 1);

    @GameTest(template = "amulet_bridge")
    public void staysFloatingWithoutSupportOrLevitation(GameTestHelper helper) {
        placeGiantSpectralAnvil(helper, ANCHOR_REL);
        // 无磁铁/环，下方也无支撑：等待多个轮询周期后结构必须原封不动
        helper.runAfterDelay(60, () -> {
            assertAnvilIntact(helper, ANCHOR_REL);
            if (!nearbyGhosts(helper).isEmpty()) {
                helper.fail("Ghost entity must not spawn without levitation loss");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "amulet_bridge")
    public void magnetLevitationReleasesGhostOnDemagnetize(GameTestHelper helper) {
        // 铺平台（y=-1，3x3 石）接住虚影，再铺砧（y=0..2）
        placePlatform(helper);
        placeGiantSpectralAnvil(helper, ANCHOR_REL);

        // 磁铁放锚格上方第 3 格（有磁力，LIT=false → 托举）
        BlockPos magnetRel = ANCHOR_REL.above(3);
        helper.setBlock(magnetRel, ModBlocks.MAGNET_BLOCK.get().defaultBlockState());

        // 记录落地事件（按落点过滤：只统计本测试锚格附近虚影的落地，避免并发测试串扰）
        BlockPos anchorAbs = helper.absolutePos(ANCHOR_REL);
        AtomicInteger giantLandCount = new AtomicInteger();
        AtomicInteger onLandCount = new AtomicInteger();
        AtomicInteger lastFallDistance = new AtomicInteger(-1);
        NeoForge.EVENT_BUS.addListener((AnvilEvent.GiantOnLand event) -> {
            if (!event.getPos().closerThan(anchorAbs, 6.0)) return;
            giantLandCount.incrementAndGet();
            lastFallDistance.set((int) event.getFallDistance());
        });
        NeoForge.EVENT_BUS.addListener((AnvilEvent.OnLand event) -> {
            if (!event.getPos().closerThan(anchorAbs, 6.0)) return;
            onLandCount.incrementAndGet();
        });

        // 等轮询把 POWERED 置 true
        helper.runAfterDelay(10, () -> {
            BlockState anchorState = helper.getBlockState(ANCHOR_REL);
            if (!anchorState.is(AddonBlocks.GIANT_SPECTRAL_ANVIL.get())
                || !Boolean.TRUE.equals(anchorState.getValue(GiantSpectralAnvilBlock.POWERED))) {
                helper.fail("Magnet should levitate the anvil (POWERED=true), got " + anchorState);
                return;
            }
            // 消磁沿：移除磁铁
            helper.setBlock(magnetRel, Blocks.AIR);
        });

        // 等轮询检测到消磁 → 释放虚影 → 虚影落到平台 → 触发事件
        helper.runAfterDelay(40, () -> {
            if (giantLandCount.get() < 1) {
                helper.fail("No GiantOnLand event after demagnetize; OnLand=" + onLandCount.get());
                return;
            }
            if (lastFallDistance.get() != (int) FallingGiantSpectralAnvilEntity.EQUIVALENT_FALL_DISTANCE) {
                helper.fail("Ghost fall distance should be 10, got " + lastFallDistance.get());
                return;
            }
            // 虚影不铺块：本体结构必须仍在原位
            assertAnvilIntact(helper, ANCHOR_REL);
            helper.succeed();
        });
    }

    @GameTest(template = "amulet_bridge")
    public void portalConversionDistribution(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int spectral = 0;
        int dust = 0;
        int kept = 0;
        int trials = 300;
        for (int i = 0; i < trials; i++) {
            FallingGiantAnvilEntity entity = new FallingGiantAnvilEntity(
                ModEntities.FALLING_GIANT_ANVIL.get(), level);
            entity.blockState = ModBlocks.GIANT_ANVIL.get().defaultBlockState()
                .setValue(GiantAnvilBlock.HALF, Cube3x3PartHalf.MID_CENTER)
                .setValue(GiantAnvilBlock.CUBE, GiantAnvilCube.CENTER);
            NeoForge.EVENT_BUS.post(new EntityThroughPortalEvent(level, entity, PortalType.END_PORTAL));
            // anvilcraft 默认监听器随后执行：非豁免会覆盖成 END_DUST。
            if (entity.blockState.is(AddonBlocks.GIANT_SPECTRAL_ANVIL.get())) {
                spectral++;
            } else if (entity.blockState.is(ModBlocks.END_DUST.get())) {
                dust++;
            } else if (entity.blockState.is(ModBlocks.GIANT_ANVIL.get())) {
                kept++;
            } else {
                helper.fail("Unexpected post-portal block state " + entity.blockState);
                return;
            }
        }
        // 20/40/40 的宽松统计断言：三分支都应出现（300 次内任一支为 0 的概率极小）
        if (spectral == 0 || dust == 0 || kept == 0) {
            helper.fail("All three outcomes must occur; spectral=" + spectral
                + " dust=" + dust + " kept=" + kept);
            return;
        }
        // 幻灵砧占比应在 20% 附近（允许 ±10% 波动）
        double ratio = (double) spectral / trials;
        if (ratio < 0.10 || ratio > 0.30) {
            helper.fail("Spectral conversion ratio should be ~0.20, got " + ratio);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "amulet_bridge")
    public void giantAnvilTeleportsThroughEndPortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 在锚格处放末地传送门方块，巨砧实体从上方掉入
        BlockPos portalRel = new BlockPos(1, 1, 1);
        helper.setBlock(portalRel, Blocks.END_PORTAL.defaultBlockState());
        // 门下方铺一层石（避免直接掉出世界）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(new BlockPos(1 + dx, 0, 1 + dz), Blocks.STONE);
            }
        }

        // 事件级证据：mixin 调 changeDimension 前，anvilcraft 会发布末地门类型的
        // EntityThroughPortalEvent（携带本测试的实体）。须先注册监听再生成实体，
        // 避免实体首个 tick 就传送时事件先于监听发出。
        AtomicInteger portalEventCount = new AtomicInteger();
        AtomicReference<FallingGiantAnvilEntity> entityRef = new AtomicReference<>();
        NeoForge.EVENT_BUS.addListener((EntityThroughPortalEvent event) -> {
            if (event.getEntity() == entityRef.get()) portalEventCount.incrementAndGet();
        });

        // 真实巨型铁砧下落实体：生成在门正上方（3x3 碰撞盒已覆盖门格并悬空），
        // 由 mixin 在其 tick 内扫描到门并立即触发跨维度传送，不依赖下落过程
        BlockPos spawnRel = new BlockPos(1, 3, 1);
        BlockPos spawnAbs = helper.absolutePos(spawnRel);
        BlockState giantState = ModBlocks.GIANT_ANVIL.get().defaultBlockState()
            .setValue(GiantAnvilBlock.HALF, Cube3x3PartHalf.MID_CENTER)
            .setValue(GiantAnvilBlock.CUBE, GiantAnvilCube.CENTER);
        FallingGiantAnvilEntity entity = FallingGiantAnvilEntity.fall(
            level, new BlockPos(spawnAbs.getX(), spawnAbs.getY(), spawnAbs.getZ()), giantState, false);
        entityRef.set(entity);

        // 等实体掉入门中并完成传送（须在 100 tick 测试上限内完成）
        helper.runAfterDelay(80, () -> {
            // 传送成功的关键证据：旧实体被移除（changeDimension 会移除并重建实体于末地），
            // 且主世界此区域没有它落地铺下的巨型铁砧块（若 mixin 未生效，它会正常落地铺块）。
            boolean overworldAnvilBlock = false;
            for (BlockPos p : BlockPos.betweenClosed(
                helper.absolutePos(new BlockPos(0, -1, 0)),
                helper.absolutePos(new BlockPos(2, 4, 2)))) {
                if (level.getBlockState(p).is(ModBlocks.GIANT_ANVIL.get())
                    || level.getBlockState(p).is(AddonBlocks.GIANT_SPECTRAL_ANVIL.get())) {
                    overworldAnvilBlock = true;
                    break;
                }
            }
            if (!entity.isRemoved()) {
                helper.fail("Giant anvil entity not removed after 80 ticks (pos=" + entity.blockPosition()
                    + " level=" + entity.level().dimension().location() + ")");
                return;
            }
            if (overworldAnvilBlock) {
                helper.fail("Giant anvil landed in the overworld instead of teleporting");
                return;
            }
            if (portalEventCount.get() < 1) {
                helper.fail("No EntityThroughPortalEvent posted for the falling giant anvil (entity removed but no event)");
                return;
            }
            // 实体已离开主世界：真实跨维度传送发生（可能已落地于末地或仍在末地下落/虚空，
            // 因此不再做强末地存在性断言，避免虚空掉落导致的随机失败）
            helper.succeed();
        });
    }

    /** 在锚格下方 (y=-1) 铺 3x3 石平台，接住释放的虚影。 */
    private static void placePlatform(GameTestHelper helper) {
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                helper.setBlock(ANCHOR_REL.offset(dx - 1, -1, dz - 1), Blocks.STONE);
            }
        }
    }

    /** 在 anchorRel（BOTTOM_CENTER 格）铺满 3x3x3 巨型幻灵砧。 */
    private static void placeGiantSpectralAnvil(GameTestHelper helper, BlockPos anchorRel) {
        BlockState base = AddonBlocks.GIANT_SPECTRAL_ANVIL.get().defaultBlockState();
        for (Cube3x3PartHalf part : Cube3x3PartHalf.values()) {
            BlockState state = base
                .setValue(GiantSpectralAnvilBlock.HALF, part)
                .setValue(
                    GiantSpectralAnvilBlock.CUBE,
                    part == Cube3x3PartHalf.MID_CENTER ? GiantAnvilCube.CENTER : GiantAnvilCube.CORNER)
                .setValue(GiantSpectralAnvilBlock.POWERED, false);
            helper.setBlock(
                anchorRel.offset(part.getOffsetX(), part.getOffsetY(), part.getOffsetZ()),
                state);
        }
    }

    /** 断言锚格及其上 2 层全部仍为巨型幻灵砧部件（结构未自落/未消失）。 */
    private static void assertAnvilIntact(GameTestHelper helper, BlockPos anchorRel) {
        for (Cube3x3PartHalf part : Cube3x3PartHalf.values()) {
            BlockPos rel = anchorRel.offset(part.getOffsetX(), part.getOffsetY(), part.getOffsetZ());
            if (!helper.getBlockState(rel).is(AddonBlocks.GIANT_SPECTRAL_ANVIL.get())) {
                helper.fail("Structure part missing at " + rel + " (anvil fell or vanished)");
            }
        }
    }

    /** 锚格附近 ±6 格内的虚影实体（若有）。 */
    private static java.util.List<FallingGiantSpectralAnvilEntity> nearbyGhosts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchorAbs = helper.absolutePos(ANCHOR_REL);
        AABB box = new AABB(anchorAbs).inflate(6.0);
        return level.getEntitiesOfClass(FallingGiantSpectralAnvilEntity.class, box);
    }
}
