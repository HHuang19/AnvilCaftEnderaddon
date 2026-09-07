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
import java.util.function.Consumer;

/**
 * 巨型幻灵砧机制 GameTest：
 * <ol>
 *     <li>固定不落：无托举、无支撑时结构保持原位，不产生虚影；</li>
 *     <li>磁铁托举 + 消磁释放：磁铁（有磁力）置于锚格上方第 3 格使 POWERED=true，
 *         移除磁铁（消磁沿）后释放 3x3 虚影，虚影落地触发等效 10 格巨砧事件；</li>
 *     <li>传送门转化分布：300 次末地门事件统计 20% 幻灵砧 / 40% 尘埃 / 40% 保持；</li>
 *     <li>幻灵砧状态落体铺结构：携带幻灵砧主件状态的巨砧实体（20% 分支产物）落地时
 *         anvilcraft 会对其调用 GiantAnvilBlock.onLand —— 直接驱动 onLand 验证
 *         铺满 27 块幻灵砧结构（含各部件 HALF/CUBE 与 POWERED=false）；</li>
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
        Consumer<AnvilEvent.GiantOnLand> giantListener = event -> {
            if (!event.getPos().closerThan(anchorAbs, 6.0)) return;
            giantLandCount.incrementAndGet();
            lastFallDistance.set((int) event.getFallDistance());
        };
        Consumer<AnvilEvent.OnLand> onLandListener = event -> {
            if (!event.getPos().closerThan(anchorAbs, 6.0)) return;
            onLandCount.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(giantListener);
        NeoForge.EVENT_BUS.addListener(onLandListener);

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
            NeoForge.EVENT_BUS.unregister(giantListener);
            NeoForge.EVENT_BUS.unregister(onLandListener);
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
    public void spectralAnvilLandPlacesFullStructure(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 实体落点：锚格 y=0 处铺 3x3 石平台（支撑 27 块结构）
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                helper.setBlock(new BlockPos(1 + dx - 1, 0, 1 + dz - 1), Blocks.STONE);
            }
        }
        // 等价于末地门 20% 转化后的状态：幻灵砧主件（MID_CENTER/CENTER）下落实体。
        // anvilcraft 巨砧实体落地（FallingGiantAnvilEntity.tick 落地分支）会对携带的
        // 幻灵砧状态调 GiantAnvilBlock.onLand → 铺满 27 块。这里直接驱动 onLand，
        // 验证 20% 分支产物落地的铺结构行为（实体自由落体的贴地几何由 anvilcraft
        // 巨砧实体自身逻辑决定，本测试聚焦 onLand 对幻灵砧状态的展开）。
        FallingGiantAnvilEntity entity = new FallingGiantAnvilEntity(
            ModEntities.FALLING_GIANT_ANVIL.get(), level);
        // 主件格（MID_CENTER）在平台上方 1 格：结构 BOTTOM 层将落在平台 y=0
        BlockPos mainRel = new BlockPos(1, 1, 1);
        BlockPos mainAbs = helper.absolutePos(mainRel);
        BlockState spectralState = AddonBlocks.GIANT_SPECTRAL_ANVIL.get().defaultBlockState()
            .setValue(GiantSpectralAnvilBlock.HALF, Cube3x3PartHalf.MID_CENTER)
            .setValue(GiantSpectralAnvilBlock.CUBE, GiantAnvilCube.CENTER)
            .setValue(GiantSpectralAnvilBlock.POWERED, false);
        entity.setPos(mainAbs.getX() + 0.5, mainAbs.getY(), mainAbs.getZ() + 0.5);
        ((GiantSpectralAnvilBlock) spectralState.getBlock()).onLand(
            level, mainAbs, spectralState,
            Blocks.AIR.defaultBlockState(), entity, FallingGiantSpectralAnvilEntity.EQUIVALENT_FALL_DISTANCE);

        // onLand 铺结构：BOTTOM 层 y=0 贴平台，MID 层 y=1，TOP 层 y=2
        for (int y = 0; y <= 2; y++) {
            for (int dx = 0; dx < 3; dx++) {
                for (int dz = 0; dz < 3; dz++) {
                    BlockPos rel = new BlockPos(1 + dx - 1, y, 1 + dz - 1);
                    BlockState state = helper.getBlockState(rel);
                    if (!state.is(AddonBlocks.GIANT_SPECTRAL_ANVIL.get())) {
                        helper.fail("Missing spectral part at " + rel + " got " + state);
                        return;
                    }
                    // 各层 HALF/CUBE 应与位置匹配（MID 层中心格为主件 CENTER）
                    Cube3x3PartHalf expected = Cube3x3PartHalf.findByOffset(dx - 1, y, dz - 1);
                    if (expected == null || state.getValue(GiantSpectralAnvilBlock.HALF) != expected) {
                        helper.fail("Wrong HALF at " + rel + " expected " + expected + " got "
                            + state.getValue(GiantSpectralAnvilBlock.HALF));
                        return;
                    }
                    if (Boolean.TRUE.equals(state.getValue(GiantSpectralAnvilBlock.POWERED))) {
                        helper.fail("Landed spectral parts must not be POWERED at " + rel);
                        return;
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "amulet_bridge")
    public void giantAnvilTeleportsThroughEndPortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 末地传送门方块：实体（3x3 盒，makeBoundingBox 以 position.y-1 为盒底、
        // 高 3 格）从门格上方下落时会覆盖门格，mixin 在 tick 落地判定前扫描命中。
        BlockPos portalRel = new BlockPos(1, 2, 1);
        helper.setBlock(portalRel, Blocks.END_PORTAL.defaultBlockState());
        // 门下方铺 3x3 石平台：仅兜底接住“未传送”的实体，避免其掉出世界
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
            }
        }

        // 事件级证据：mixin 调 changeDimension 前，anvilcraft 会发布末地门类型的
        // EntityThroughPortalEvent（携带本测试的实体）。须先注册监听再生成实体，
        // 避免实体首个 tick 就传送时事件先于监听发出。
        AtomicInteger portalEventCount = new AtomicInteger();
        AtomicReference<FallingGiantAnvilEntity> entityRef = new AtomicReference<>();
        Consumer<EntityThroughPortalEvent> portalListener = event -> {
            if (event.getEntity() == entityRef.get()) portalEventCount.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(portalListener);

        // 真实巨型铁砧下落实体：生成在门上方悬空，3x3 碰撞盒持续覆盖门格，
        // 由 mixin 在其 tick 内扫描到门并立即触发跨维度传送。
        BlockPos spawnRel = new BlockPos(1, 4, 1);
        BlockPos spawnAbs = helper.absolutePos(spawnRel);
        BlockState giantState = ModBlocks.GIANT_ANVIL.get().defaultBlockState()
            .setValue(GiantAnvilBlock.HALF, Cube3x3PartHalf.MID_CENTER)
            .setValue(GiantAnvilBlock.CUBE, GiantAnvilCube.CENTER);
        FallingGiantAnvilEntity entity = FallingGiantAnvilEntity.fall(
            level, new BlockPos(spawnAbs.getX(), spawnAbs.getY(), spawnAbs.getZ()), giantState, false);
        entityRef.set(entity);

        // 手动驱动实体 tick：GameTestServer 并发批量跑测试时，下落实体的自动 tick 偶发
        // 不执行（表现为实体位置恒定不变、mixin 无机会运行），导致传送不触发。手动 tick
        // 与服务器 tick 同线程且等效（move/重力/事件均在服务端线程内完成），可确定性
        // 覆盖 mixin 的传送路径；实体若已被服务器自动 tick，多 tick 几次亦无副作用。
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 30 && !entity.isRemoved(); i++) {
                entity.tick();
            }
            if (entity.isRemoved()) {
                // 传送成功：旧实体已随 changeDimension 移除（新实体在末地）。
                // 事件级证据由下方监听计数（事件须已发布）。
                if (portalEventCount.get() < 1) {
                    NeoForge.EVENT_BUS.unregister(portalListener);
                    helper.fail("Entity teleported but no EntityThroughPortalEvent posted");
                    return;
                }
                NeoForge.EVENT_BUS.unregister(portalListener);
                helper.succeed();
                return;
            }
            // 未传送：实体应仍在主世界门附近或已落地；由 80 tick 终检兜底
            // （不在此 fail，避免与下落中的合法状态竞争）
        });

        // 终检兜底（手动 tick 未传送时给出诊断）
        helper.runAfterDelay(80, () -> {
            if (entity.isRemoved()) {
                if (portalEventCount.get() < 1) {
                    NeoForge.EVENT_BUS.unregister(portalListener);
                    helper.fail("No EntityThroughPortalEvent posted for the falling giant anvil (entity removed but no event)");
                    return;
                }
                NeoForge.EVENT_BUS.unregister(portalListener);
                helper.succeed();
                return;
            }
            BlockPos portalAbs = helper.absolutePos(new BlockPos(1, 2, 1));
            helper.fail("Giant anvil entity not removed after manual ticks (pos=" + entity.blockPosition()
                + " y=" + entity.getY()
                + " level=" + entity.level().dimension().location()
                + " portalEvents=" + portalEventCount.get()
                + " portalAt=" + level.getBlockState(portalAbs)
                + " bbox=" + entity.getBoundingBox() + ")");
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
