package dev.anvilcraft.portableaddon.client.tooltip;

import dev.anvilcraft.portableaddon.init.blocks.ender_transmission_pole.EnderPoleBlock;
import dev.dubhe.anvilcraft.api.power.PowerComponentInfo;
import dev.dubhe.anvilcraft.api.power.SimplePowerGrid;
import dev.dubhe.anvilcraft.api.tooltip.impl.PowerComponentTooltipProvider;
import dev.dubhe.anvilcraft.api.tooltip.providers.ITooltipProvider;
import dev.dubhe.anvilcraft.client.AnvilCraftClient;
import dev.dubhe.anvilcraft.util.CompatUtil;
import dev.dubhe.anvilcraft.util.UnitUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 末影输电杆的 HUD 电网信息 tooltip（持铁砧锤看向方块时显示）。
 * <p>
 * 普通输电杆由 {@link PowerComponentTooltipProvider} 渲染电网信息，
 * 但该 provider 对多方块方块只查询主部件（底部段）坐标处的电网信息；
 * 而末影杆的电网成员注册在顶部段，因此原版 provider 查询底部坐标得不到结果、返回空 tooltip。
 * 本 provider 按顶部段坐标查找同一份电网信息，渲染与普通输电杆完全相同的行。
 */
public class EnderPoleTooltipProvider extends ITooltipProvider.BlockEntityTooltipProvider {

    @Override
    public boolean accepts(BlockEntity entity) {
        return entity != null && entity.getBlockState().getBlock() instanceof EnderPoleBlock;
    }

    @Override
    public List<Component> tooltip(BlockEntity entity) {
        // 与普通输电杆一致：顶部段才是电网成员，按顶部段坐标向上查找电网信息
        BlockState state = entity.getBlockState();
        BlockPos mainPartPos = ((EnderPoleBlock) state.getBlock())
            .getMainPartPos(entity.getBlockPos(), state);
        BlockPos topPos = mainPartPos.above(2);

        Optional<SimplePowerGrid> powerGrids = SimplePowerGrid.findPowerGrid(topPos);
        if (powerGrids.isEmpty()) return List.of();
        SimplePowerGrid grid = powerGrids.get();
        Optional<PowerComponentInfo> optional = grid.getInfoForPos(topPos);
        if (optional.isEmpty()) return List.of();

        if (CompatUtil.HAS_JADE.get() && AnvilCraftClient.CONFIG.doNotShowTooltipWhenJadePresent) return List.of();

        LocalPlayer player = Minecraft.getInstance().player;
        boolean original = player != null && player.isShiftKeyDown();
        boolean overloaded = grid.getConsume() > grid.getGenerate();

        List<Component> lines = new ArrayList<>();
        if (overloaded) {
            for (int i = 1; i <= 3; i++) {
                lines.add(Component.translatable("tooltip.anvilcraft.grid_information.overloaded" + i));
            }
        }
        lines.add(Component.translatable("tooltip.anvilcraft.grid_information.title")
            .setStyle(Style.EMPTY.applyFormat(ChatFormatting.BLUE)));
        lines.add(Component.translatable(
                "tooltip.anvilcraft.grid_information.total_consumed",
                UnitUtil.electricityUnit(grid.getConsume(), original, false)
            )
            .setStyle(Style.EMPTY.applyFormat(ChatFormatting.GRAY)));
        lines.add(Component.translatable(
                "tooltip.anvilcraft.grid_information.total_generated",
                UnitUtil.electricityUnit(grid.getGenerate(), original, grid.isInfinitePower())
            )
            .setStyle(Style.EMPTY.applyFormat(ChatFormatting.GRAY)));
        return lines;
    }

    @Override
    public int priority() {
        // 比原版 PowerComponentTooltipProvider(0) 更优先，确保末影杆由本 provider 渲染
        return -1;
    }
}
