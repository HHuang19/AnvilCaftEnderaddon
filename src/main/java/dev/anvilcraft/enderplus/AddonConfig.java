package dev.anvilcraft.enderplus;

import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;

@Config(name = AnvilcraftEnderplus.MOD_ID)
public class AddonConfig {
    @Comment("Teleport distance for the Blink enchantment (in blocks)\n闪现附魔的传送距离（以方块为单位）")
    @BoundedDiscrete(min = 1.0, max = 30.0)
    public double doubleWalkDistance = 5.0;

    @Comment("Power output for the Power Block\n能源方块的发电功率")
    @BoundedDiscrete(min = 0, max = 32)
    public int powerBlockOutput = 16;

    @Comment("Force-load the chunks of a bound charm pillar and bound ender poles so they work across dimensions\n跨维度时强加载已绑定的护符柱与末影输电杆区块，使其跨维度生效")
    public boolean crossDimensionChunkLoad = true;
}
