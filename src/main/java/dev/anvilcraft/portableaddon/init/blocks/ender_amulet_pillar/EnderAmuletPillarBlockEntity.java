package dev.anvilcraft.portableaddon.init.blocks.ender_amulet_pillar;

import dev.anvilcraft.portableaddon.init.AddonBlocks;
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

    /** 取下指定侧面最后挂载的护符；该侧为空时返回 EMPTY。 */
    public @NotNull ItemStack removeLastAmulet(Direction side) {
        List<ItemStack> list = this.amuletsBySide.getOrDefault(side, List.of());
        if (list.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = list.remove(list.size() - 1);
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
