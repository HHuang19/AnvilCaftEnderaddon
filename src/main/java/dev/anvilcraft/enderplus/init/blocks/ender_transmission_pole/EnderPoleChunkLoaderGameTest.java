package dev.anvilcraft.enderplus.init.blocks.ender_transmission_pole;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.init.AddonBlocks;
import dev.dubhe.anvilcraft.block.state.Vertical3PartHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Cross-dimension power bridge chunk force-load GameTest.
 * <p>
 * Verifies that the ender pole keeps force/release reference counting to
 * {@link EnderPoleChunkLoader} strictly balanced: after unbinding a pole that is
 * bound to multiple other poles, no force-load reference may be left behind.
 * The old implementation recomputed and re-forced every link on each
 * {@link EnderPoleBlockEntity#setBound}, inflating the refcount and permanently
 * force-loading chunks (a memory/CPU leak).
 */
@GameTestHolder(AnvilcraftEnderplus.MOD_ID)
@PrefixGameTestTemplate(false)
public class EnderPoleChunkLoaderGameTest {

    @GameTest(template = "amulet_bridge")
    public void multiplyBoundUnbindReleasesAllChunkForces(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockState top = AddonBlocks.ENDERPOLE.get().defaultBlockState()
            .setValue(EnderPoleBlock.PARTHALF, Vertical3PartHalf.TOP);

        helper.setBlock(new BlockPos(1, 1, 1), top);
        helper.setBlock(new BlockPos(3, 1, 1), top);
        helper.setBlock(new BlockPos(5, 1, 1), top);

        BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos b = helper.absolutePos(new BlockPos(3, 1, 1));
        BlockPos c = helper.absolutePos(new BlockPos(5, 1, 1));

        if (!(level.getBlockEntity(a) instanceof EnderPoleBlockEntity poleA)) {
            helper.fail("Pole A block entity missing");
            return;
        }

        // One pole bound to two other poles (simulating a multi-link grid).
        poleA.setBound(level.dimension(), b);
        poleA.setBound(level.dimension(), c);

        // Full unbind: every force-load reference must be precisely released.
        poleA.unbind();

        helper.assertTrue(
            EnderPoleChunkLoader.totalRefs() == 0,
            "Unbinding a multiply-bound pole must leave no chunk force-load reference behind"
        );
        helper.succeed();
    }
}