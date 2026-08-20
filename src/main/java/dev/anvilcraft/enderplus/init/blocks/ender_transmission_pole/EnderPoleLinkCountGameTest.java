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
 * Ender pole connection-count GameTest.
 * <p>
 * Verifies that {@link EnderPoleBlockEntity#getLinkCount()} reflects the number
 * of poles this pole is currently bound to: 0 when nothing is bound, and the exact
 * count after binding one or more poles, returning to 0 after unbinding.
 * <p>
 * This drives the connection hint shown in the ender pole HUD tooltip.
 */
@GameTestHolder(AnvilcraftEnderplus.MOD_ID)
@PrefixGameTestTemplate(false)
public class EnderPoleLinkCountGameTest {

    @GameTest(template = "amulet_bridge")
    public void linkCountReflectsBoundPoles(GameTestHelper helper) {
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

        helper.assertTrue(poleA.getLinkCount() == 0, "Unbound pole must report 0 links");

        poleA.setBound(level.dimension(), b);
        helper.assertTrue(poleA.getLinkCount() == 1, "Pole must report 1 link after binding one pole");

        poleA.setBound(level.dimension(), c);
        helper.assertTrue(poleA.getLinkCount() == 2, "Pole must report 2 links after binding two poles");

        poleA.unbind();
        helper.assertTrue(poleA.getLinkCount() == 0, "Pole must report 0 links after unbinding");
        helper.succeed();
    }
}
