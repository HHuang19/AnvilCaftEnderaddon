package dev.anvilcraft.enderplus.init.blocks.PowerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;


public class PowerBlock extends Block implements EntityBlock {
    public PowerBlock() {
        super(BlockBehaviour.Properties.of()        // 基础的方块属性
                .strength(3.0F)          // 硬度
                .requiresCorrectToolForDrops()
                .noOcclusion()
        );
    }
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PowerBlockEntity(pos, state);
    }
    @Override
    public int getLightEmission(BlockState state, BlockGetter world, BlockPos pos) {
        return 13; // 发光
    }
}
