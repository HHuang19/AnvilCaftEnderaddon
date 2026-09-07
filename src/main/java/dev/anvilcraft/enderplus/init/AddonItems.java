package dev.anvilcraft.enderplus.init;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.init.items.EnderAmuletItem;
import dev.anvilcraft.enderplus.init.items.EnderPoleItem;
import dev.dubhe.anvilcraft.block.item.SimpleMultiPartBlockItem;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Objects;

/**
 * 模组物品注册与管理类
 * 负责统一维护并向游戏事件总线注册所有自定义 Item、BlockItem 及其独特的交互行为
 */
public class AddonItems {

    // 延迟注册器 (DeferredRegister)，绑定当前模组的 MOD_ID
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AnvilcraftEnderplus.MOD_ID);

    // ==========================================
    // 1. 磁铁物品 (Magnet)
    // 核心行为：右键可以吸取世界上的铁砧，或者将内部存储的铁砧重新放回世界上
    // ==========================================
    public static DeferredItem<Item> Magnet =
            ITEMS.register("magnet", () -> new Item(new Item.Properties().stacksTo(1)) {

                @Override
                public InteractionResult useOn(UseOnContext context) {
                    // 仅在服务端且点击目标非空气方块时执行物理交互
                    if (!context.getLevel().isClientSide && !context.getLevel().getBlockState(context.getClickedPos()).is(Blocks.AIR)) {
                        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
                        ItemStack _This = context.getItemInHand();
                        // 从物品的 DataComponent 中获取当前存储的铁砧状态 (BlockState)
                        BlockState AnvilState = context.getItemInHand().get(DataComponents.ANVIL_STATE);

                        if (AnvilState == null || AnvilState.equals(Blocks.AIR.defaultBlockState())) {
                            // 行为 A：磁铁当前为空，尝试吸取方块
                            if (blockState.is(BlockTags.ANVIL)) {
                                if (AnvilState == null || AnvilState.equals(Blocks.AIR.defaultBlockState())) {
                                    // 将目标铁砧方块状态写入磁铁的 DataComponent，并将世界上的铁砧方块抹除（变成空气）
                                    _This.set(DataComponents.ANVIL_STATE, blockState);
                                    context.getLevel().setBlock(context.getClickedPos(), Blocks.AIR.defaultBlockState(), 3);
                                    return InteractionResult.SUCCESS; // 拦截并结束交互事件
                                }
                            }
                        }
                        else {
                            // 行为 B：磁铁已充能（含有铁砧），尝试在点击面外侧放置铁砧
                            BlockPos _TargetBlock = context.getClickedPos().relative(context.getClickedFace());
                            if (context.getLevel().getBlockState(_TargetBlock).is(Blocks.AIR)) {
                                // 提取磁铁里的铁砧状态并生成到世界上，随后重置磁铁数据为空气
                                context.getLevel().setBlock(_TargetBlock, Objects.requireNonNull(_This.get(DataComponents.ANVIL_STATE.get())), 3);
                                _This.set(DataComponents.ANVIL_STATE, Blocks.AIR.defaultBlockState());
                                return InteractionResult.SUCCESS; // 拦截并结束交互事件
                            } else {
                                return InteractionResult.FAIL; // 目标位置被堵住，放置失败
                            }
                        }
                        return InteractionResult.SUCCESS;
                    }
                    return super.useOn(context);
                }

                @Override
                public boolean isFoil(ItemStack stack) {
                    // 动态附魔光效应：当磁铁内部成功携带了有效的铁砧时，物品图标在物品栏会自带附魔流光效果
                    return stack.get(DataComponents.ANVIL_STATE) != null && !stack.get(DataComponents.ANVIL_STATE).is(Blocks.AIR);
                }

                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                    // 动态本地化信息提示：根据磁铁内部是否装载铁砧，显示不同的 Hover 提示与具体的铁砧物品名称
                    if (stack.get(DataComponents.ANVIL_STATE) != null && !stack.get(DataComponents.ANVIL_STATE).is(Blocks.AIR)) {
                        tooltipComponents.add(Component.translatable("item.anvilcraftenderplus.magnet.AVONtooltip").append(
                                Component.literal(stack.get(DataComponents.ANVIL_STATE).getBlock().asItem().toString())
                        ));
                    } else {
                        tooltipComponents.add(Component.translatable("item.anvilcraftenderplus.magnet.AVOFFtooltip"));
                    }
                    super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                }
            });

    // ==========================================
    // 2. 末影电线杆物品 (ENDERPOLE_ITEM)
    // 核心行为：正常放置方块，并负责跨维度绑定（右键已放置的杆记录/链接目标）
    // ==========================================
    public static final DeferredItem<EnderPoleItem> ENDERPOLE_ITEM =
            ITEMS.register("enderpole", EnderPoleItem::new);

    // ==========================================
    // 3. 基础能源方块物品 (POWER_BLOCK_ITEM)
    // 快捷将常规方块转换为对应 BlockItem 的简易注册
    // ==========================================
    public static final DeferredItem<BlockItem> POWER_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("power_block", AddonBlocks.POWER_BLOCK);

    // ==========================================
    // 4. 末影护符柱物品 (ENDER_AMULET_PILLAR_ITEM)
    // 快捷将两格高方块转换为对应 BlockItem 的简易注册
    // ==========================================
    public static final DeferredItem<BlockItem> ENDER_AMULET_PILLAR_ITEM =
            ITEMS.registerSimpleBlockItem("ender_amulet_pillar", AddonBlocks.ENDER_AMULET_PILLAR);

    // ==========================================
    // 5. 末影护符物品 (ENDER_AMULET)
    // 无任何效果的 AnvilCraft 护符，携带 AMULET 数据组件，可挂在末影护符柱上；
    // 潜行右键末影护符柱可把护符绑定到该柱子
    // ==========================================
    public static final DeferredItem<EnderAmuletItem> ENDER_AMULET =
            ITEMS.register("ender_amulet", EnderAmuletItem::new);

    // ==========================================
    // 6. 巨型幻灵砧物品 (GIANT_SPECTRAL_ANVIL)
    // 3x3x3 巨型多方块砧；放置语义与巨型铁砧一致（SimpleMultiPartBlockItem）
    // ==========================================
    public static final DeferredItem<BlockItem> GIANT_SPECTRAL_ANVIL =
            ITEMS.register(
                "giant_spectral_anvil",
                () -> new SimpleMultiPartBlockItem<Cube3x3PartHalf>(
                    AddonBlocks.GIANT_SPECTRAL_ANVIL.get(),
                    new Item.Properties().stacksTo(16)
                )
            );


    /**
     * 初始化物品注册
     * @param eventBus 模组专用的 FML/NeoForge 事件总线
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}