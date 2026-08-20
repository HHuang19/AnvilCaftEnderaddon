package dev.anvilcraft.enderplus.init.blocks.ender_amulet_pillar;

import dev.anvilcraft.enderplus.init.AddonBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 末影护符柱的方块实体
 * 持有挂载在柱子上的护符列表，供服务端存储与客户端渲染读取。
 * <p>
 * 每个水平方向（侧面）各自独立计数，每侧最多 {@link #CAPACITY_PER_SIDE} 个，
 * 四个侧面互不影响，可分别挂满。
 */
public class EnderAmuletPillarBlockEntity extends BlockEntity {

    public static final int CAPACITY_PER_SIDE = 4;
    private static final String TAG_AMULETS = "amulets";
    private static final String TAG_SIDE = "side";

    // 护符渲染几何（客户端渲染器与此共享，保证"准星所指护符"的拾取槽位与实际渲染位置一致）
    /** 护符缩放比例（缩小后 0.35，保证每侧 4 个互不重叠且最下方护符不被底座掩埋） */
    public static final float AMULET_SCALE = 0.35F;
    /** 护符距柱心的水平距离（主柱面在 x/z 0.09375~0.90625，护符贴在柱面外侧） */
    public static final float AMULET_RADIUS = 0.42F;
    /** 每侧第一个护符的顶部高度（顶部盖板底缘在 1.8125，下移避免遮挡） */
    public static final float AMULET_Y_TOP = 1.55F;
    /** 护符之间的固定竖向间距（最下方护符下沿 0.265，高于底座顶面 0.1875，完整露出） */
    public static final float AMULET_Y_SPACING = 0.37F;

    /** 每侧按挂载顺序维护的护符列表（按方向索引） */
    private final Map<Direction, List<ItemStack>> amuletsBySide = new EnumMap<>(Direction.class);

    public EnderAmuletPillarBlockEntity(BlockPos pos, BlockState state) {
        super(AddonBlocks.ENDER_AMULET_PILLAR_ENTITY.get(), pos, state);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            this.amuletsBySide.put(dir, new ArrayList<>());
        }
    }

    /** 供渲染与掉落逻辑读取的只读视图（按方向→列表） */
    public @NotNull Map<Direction, List<ItemStack>> getAmuletsBySide() {
        return Collections.unmodifiableMap(this.amuletsBySide);
    }

    /** 指定侧面是否已挂满 */
    public boolean isFull(Direction side) {
        return this.amuletsBySide.getOrDefault(side, List.of()).size() >= CAPACITY_PER_SIDE;
    }

    /** 挂载一个护符到指定侧面。该侧已满时返回 false。 */
    public boolean tryHangAmulet(Direction side, ItemStack stack) {
        if (this.isFull(side)) return false;
        this.amuletsBySide.getOrDefault(side, List.of())
                .add(stack.copyWithCount(1));
        this.sync();
        return true;
    }

    /** 取下指定侧面自上而下第 index 个护符；index 越界或该侧为空时返回 EMPTY。移除后其余护符自动上移补位，不留空位。 */
    public @NotNull ItemStack removeAmulet(Direction side, int index) {
        List<ItemStack> list = this.amuletsBySide.getOrDefault(side, List.of());
        if (index < 0 || index >= list.size()) return ItemStack.EMPTY;
        ItemStack removed = list.remove(index);
        this.sync();
        return removed;
    }

    private void sync() {
        this.setChanged();
        Level level = this.level;
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ==================== NBT 持久化 ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ListTag list = new ListTag();
        for (Map.Entry<Direction, List<ItemStack>> entry : this.amuletsBySide.entrySet()) {
            for (ItemStack stack : entry.getValue()) {
                if (stack.isEmpty()) continue;
                // saveOptional 对非空物品栈返回 CompoundTag
                CompoundTag item = (CompoundTag) stack.saveOptional(provider);
                item.putString(TAG_SIDE, entry.getKey().getName());
                list.add(item);
            }
        }
        tag.put(TAG_AMULETS, list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.amuletsBySide.values().forEach(List::clear);
        ListTag list = tag.getList(TAG_AMULETS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(provider, item);
            if (stack.isEmpty()) continue;
            Direction side = Direction.byName(item.getString(TAG_SIDE));
            if (side == null || !side.getAxis().isHorizontal()) continue;
            List<ItemStack> sideList = this.amuletsBySide.get(side);
            if (sideList == null || sideList.size() >= CAPACITY_PER_SIDE) continue;
            sideList.add(stack.copyWithCount(1));
        }
    }

    // ==================== 客户端同步 ====================

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider provider) {
        return this.saveWithoutMetadata(provider);
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        this.loadAdditional(tag, provider);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
