package dev.anvilcraft.portableaddon.init.blocks.ender_amulet_pillar;

import dev.dubhe.anvilcraft.block.state.ISimpleMultiPartBlockState;
import lombok.Getter;

@Getter
public enum Vertical2PartHalf implements ISimpleMultiPartBlockState<Vertical2PartHalf> {
    TOP("top", 0, 1, 0),
    BOTTOM("bottom", 0, 0, 0);

    private final String name;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;

    Vertical2PartHalf(String name, int offsetX, int offsetY, int offsetZ) {
        this.name = name;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public String toString() {
        return this.name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
