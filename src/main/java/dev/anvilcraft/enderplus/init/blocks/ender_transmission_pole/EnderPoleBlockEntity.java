package dev.anvilcraft.enderplus.init.blocks.ender_transmission_pole;

import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.init.AddonBlocks;
import dev.dubhe.anvilcraft.api.power.IPowerComponent;
import dev.dubhe.anvilcraft.api.power.IPowerConsumer;
import dev.dubhe.anvilcraft.api.power.IPowerProducer;
import dev.dubhe.anvilcraft.api.power.IPowerStorage;
import dev.dubhe.anvilcraft.api.power.PowerComponentType;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import dev.dubhe.anvilcraft.block.entity.AbstractTransmissionPoleBlockEntity;
import dev.dubhe.anvilcraft.block.state.Vertical3PartHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 末影输电杆的方块实体 (BlockEntity)
 * <p>
 * 同时作为本地电网的 {@link PowerComponentType#TRANSMITTER} 与跨维度电力桥元件
 * （{@link EnderBridge}），把互相绑定的杆所在的两个电网变成"共享电网"，从而实现
 * **跨维度输电**：
 * <ul>
 *   <li>仍以顶部段接入本地电网，由红石信号控制开关（沿用原逻辑）；</li>
 *   <li>一根杆可以同时绑定**多根**杆（一根杆可以无数个杆），全部绑定的杆组成**一整个电网**；</li>
 *   <li>每电网刻（{@code gridTick}）重算本地电网的纯发电/用电（排除所有桥元件与储电），
 *       并把所有远端电网的发电/用电求和镜像到本地（{@code getOutputPower()} = 远端发电，
 *       {@code getInputPower()} = 远端用电）。</li>
 * </ul>
 * 由此两侧电网的 {@code generate}/{@code consume} 完全相同，等效于同一电网：
 * 发送侧能发多少、接收侧就能用多少；任一侧过载则两侧同时过载。
 * <p>
 * 跨维度生效时，按配置 {@link dev.anvilcraft.enderplus.AddonConfig#crossDimensionChunkLoad}
 * 强加载绑定各端所在区块，让没有玩家靠近时也能跨维度输电。
 * <p>
 * 已知限制（储电类）：电网自身的储电充放发生在 {@code flush()} 之后，无法跨维度镜像。
 * 因此单侧储电表现正确（唯一储电兜底整个共享电网的富余/缺口）；若两侧同时放置储电，
 * 两侧电网会各自按共享富余/缺口充放，导致储电重复计数（文档化的边界情形）。
 */
public class EnderPoleBlockEntity extends AbstractTransmissionPoleBlockEntity
    implements EnderBridge, IPowerProducer, IPowerConsumer {

    private static final String TAG_LINKS = "links";

    /** 本杆当前绑定的所有远端杆（一根杆可以同时绑定多根杆，全部组成同一共享电网） */
    private final List<Link> links = new ArrayList<>();

    /** 本杆当前已强加载的 (维度, 区块)，保证 force/release 严格配对，避免引用计数失衡导致区块被永久强加载。 */
    private final Set<PoleChunkRef> forcedChunks = new HashSet<>();

    @Nullable
    private PowerGrid grid = null;

    /** 本电网（排除桥元件与储电）的纯发电量——供远端镜像 */
    private int reportedGen = 0;
    /** 本电网（排除桥元件与储电）的纯用电量——供远端镜像 */
    private int reportedCon = 0;
    /** 镜像自远端电网的发电量（注入本电网，表现为发电） */
    private int mirroredGen = 0;
    /** 镜像自远端电网的用电量（计入本电网，表现为耗电） */
    private int mirroredCon = 0;

    /** 一条绑定关系：指向远端杆的维度与顶段坐标 */
    private record Link(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /** 一个已强加载的 (维度, 区块)，用于精确配对 force/release */
    private record PoleChunkRef(ResourceKey<Level> dimension, long chunkPos) {
    }

    public EnderPoleBlockEntity(BlockPos pos, BlockState blockState) {
        super(AddonBlocks.ENDER_POLE_ENTITY.get(), pos, blockState);
    }

    // ==================== 电网成员 (IPowerComponent) ====================

    @Override
    public @NotNull BlockPos getPos() {
        return this.getBlockPos();
    }

    @Override
    public @NotNull PowerComponentType getComponentType() {
        if (this.getLevel() == null) return PowerComponentType.INVALID;
        if (!this.getBlockState().is(AddonBlocks.ENDERPOLE.get())) return PowerComponentType.INVALID;
        if (this.getBlockState().getValue(EnderPoleBlock.PARTHALF) != Vertical3PartHalf.TOP) {
            return PowerComponentType.INVALID;
        }
        return PowerComponentType.TRANSMITTER;
    }

    @Override
    public @Nullable Level getCurrentLevel() {
        return this.getLevel();
    }

    @Override
    public @Nullable PowerGrid getGrid() {
        return this.grid;
    }

    @Override
    public void setGrid(@Nullable PowerGrid powerGrid) {
        this.grid = powerGrid;
    }

    // ==================== 跨维度共享电网 (IPowerProducer / IPowerConsumer) ====================

    @Override
    public int getOutputPower() {
        return this.mirroredGen;
    }

    @Override
    public int getInputPower() {
        return this.mirroredCon;
    }

    public int getReportedGen() {
        return this.reportedGen;
    }

    public int getReportedCon() {
        return this.reportedCon;
    }

    /**
     * 每电网刻（约 1 秒）由所在电网在 {@code flush()} 之后调用。
     * <p>
     * 先把本电网的纯发电/用电（排除全部桥元件与储电，避免与已施加的镜像形成反馈）
     * 记录下来供远端镜像，再把**所有**远端电网的发电/用电求和镜像到本电网。
     * 于是各侧电网的 {@code generate}/{@code consume} 完全相同（共享电网），
     * 且镜像值只依赖远端上次上报的纯值，不依赖本杆自身输出，因此不存在振荡。
     */
    @Override
    public void gridTick() {
        PowerGrid powerGrid = this.grid;
        if (powerGrid == null) return;

        int gen = 0;
        int con = 0;
        // 服务端电网刻在单线程执行，且此处只读不改，直接迭代安全集，避免每电网刻拷贝整个组件集
        for (IPowerComponent component : powerGrid.getComponents()) {
            if (component instanceof IPowerStorage) continue;
            if (component instanceof EnderBridge) continue;
            if (component instanceof IPowerProducer producer) gen += producer.getOutputPower();
            if (component instanceof IPowerConsumer consumer) con += consumer.getInputPower();
        }
        this.reportedGen = gen;
        this.reportedCon = con;

        // 把所有远端电网上报的纯发电/用电求和镜像到本电网
        boolean anyRemote = false;
        int mg = 0;
        int mc = 0;
        for (EnderPoleBlockEntity remote : this.findRemotes()) {
            if (!remote.isBound() || remote.getGrid() == null || remote.getGrid() == powerGrid) continue;
            anyRemote = true;
            mg += remote.getReportedGen();
            mc += remote.getReportedCon();
        }
        if (anyRemote) {
            this.mirroredGen = mg;
            this.mirroredCon = mc;
        } else {
            this.mirroredGen = 0;
            this.mirroredCon = 0;
        }
    }

    // ==================== tick / 电网维护 ====================

    /**
     * 每 tick 执行的核心逻辑（仅在服务端运行）
     */
    public void tick(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(AddonBlocks.ENDERPOLE.get())) return;
        if (state.getValue(EnderPoleBlock.PARTHALF) != Vertical3PartHalf.TOP) return;
        if (state.getValue(EnderPoleBlock.SWITCH) == IPowerComponent.Switch.OFF && this.getGrid() != null) {
            this.getGrid().remove(this);
        } else if (state.getValue(EnderPoleBlock.SWITCH) == IPowerComponent.Switch.ON && this.getGrid() == null) {
            PowerGrid.addComponent(this);
        }
        // 不在电网中（开关关闭等）→ 清除镜像与上报值，停止输电
        if (this.getGrid() == null) {
            this.mirroredGen = 0;
            this.mirroredCon = 0;
            this.reportedGen = 0;
            this.reportedCon = 0;
        }
        this.flushState(level, pos);
    }

    // ==================== 绑定管理 ====================

    public boolean isBound() {
        return !this.links.isEmpty();
    }

    public boolean isBoundTo(ResourceKey<Level> dimension, BlockPos pos) {
        return this.links.contains(new Link(dimension, pos));
    }

    /** 本杆当前连接的其它末影输电杆数量（每条绑定计 1，无连接为 0）。 */
    public int getLinkCount() {
        return this.links.size();
    }

    /**
     * 新增一条绑定（若已存在则忽略）。本杆可以同时绑定多根杆；
     * 双向绑定由末影链接器（物品）保证，这里只负责建立单侧引用。
     * 随后按配置强加载两端所在区块，实现跨维度输电。
     */
    public void setBound(ResourceKey<Level> dimension, BlockPos pos) {
        Link link = new Link(dimension, pos);
        if (this.links.contains(link)) return;
        this.links.add(link);
        this.resetBridge();
        this.setChanged();
        this.syncLinksToClient();
        this.reconcileChunks();
    }

    /**
     * 解除本杆的全部绑定；通知每个远端移除指向本杆的反向引用，并释放强加载的区块。
     */
    public void unbind() {
        if (this.links.isEmpty()) return;
        List<Link> toRemove = new ArrayList<>(this.links);
        this.links.clear();
        // 先释放本杆持有的全部强加载引用（本杆区块 + 所有远端区块）
        this.reconcileChunks();
        Level level = this.getLevel();
        if (level instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            for (Link link : toRemove) {
                // 通知远端移除指向本杆的反向引用（不触发远端的整体解绑，保留其其它绑定）
                Level targetLevel = server.getLevel(link.dimension());
                if (targetLevel != null
                    && targetLevel.getBlockEntity(link.pos()) instanceof EnderPoleBlockEntity partner) {
                    partner.removeLinkTo(level.dimension(), this.getBlockPos());
                }
            }
        }
        this.resetBridge();
        this.setChanged();
        this.syncLinksToClient();
    }

    /**
     * 移除指向 (dimension, pos) 的单条绑定（由远端解绑时回调），并释放对应强加载。
     */
    public void removeLinkTo(ResourceKey<Level> dimension, BlockPos pos) {
        Link link = new Link(dimension, pos);
        if (!this.links.remove(link)) return;
        this.reconcileChunks();
        this.resetBridge();
        this.setChanged();
        this.syncLinksToClient();
    }

    private void resetBridge() {
        this.mirroredGen = 0;
        this.mirroredCon = 0;
        this.reportedGen = 0;
        this.reportedCon = 0;
    }

    /**
     * 把本杆当前需要的强加载与已持有集合对齐：
     * 需要 = 本杆区块（仅在仍有绑定链时，避免孤立杆也常驻）+ 每条绑定链的远端区块。
     * 只有首次需要某区块时才 {@link EnderPoleChunkLoader#force}，并在不再需要时精确释放，
     * 保证每个 (维度, 区块) 的 force/release 严格配对，不会因重复 {@code setBound} 或存档重载而虚增引用。
     */
    private void reconcileChunks() {
        if (!AnvilcraftEnderplus.CONFIG.crossDimensionChunkLoad) return;
        if (!(this.getLevel() instanceof ServerLevel serverLevel)) return;
        MinecraftServer server = serverLevel.getServer();

        Set<PoleChunkRef> needed = new HashSet<>();
        if (!this.links.isEmpty()) {
            needed.add(new PoleChunkRef(serverLevel.dimension(), new ChunkPos(this.getBlockPos()).toLong()));
        }
        for (Link link : this.links) {
            needed.add(new PoleChunkRef(link.dimension(), new ChunkPos(link.pos()).toLong()));
        }

        for (PoleChunkRef ref : needed) {
            if (this.forcedChunks.add(ref)) {
                EnderPoleChunkLoader.force(server, ref.dimension(), new ChunkPos(ref.chunkPos()));
            }
        }
        for (PoleChunkRef ref : new ArrayList<>(this.forcedChunks)) {
            if (!needed.contains(ref)) {
                this.forcedChunks.remove(ref);
                EnderPoleChunkLoader.release(server, ref.dimension(), new ChunkPos(ref.chunkPos()));
            }
        }
    }

    /** 方块实体加入世界时调用：用于载入存档后按持久化的绑定恢复区块强加载。 */
    @Override
    public void onLoad() {
        super.onLoad();
        this.reconcileChunks();
    }

    /** 跨维度读取所有已加载的远端杆（不主动加载区块；远端未加载/不是杆时忽略）。 */
    private List<EnderPoleBlockEntity> findRemotes() {
        List<EnderPoleBlockEntity> result = new ArrayList<>();
        if (!(this.getLevel() instanceof ServerLevel serverLevel)) return result;
        for (Link link : this.links) {
            Level targetLevel = serverLevel.getServer().getLevel(link.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(link.pos())) continue;
            if (targetLevel.getBlockEntity(link.pos()) instanceof EnderPoleBlockEntity remote) {
                result.add(remote);
            }
        }
        return result;
    }

    // ==================== 客户端同步 ====================

    /** 数据变化后通知客户端刷新方块实体数据，使 HUD tooltip 中的连接数即时更新。 */
    private void syncLinksToClient() {
        Level level = this.getLevel();
        if (level != null && !level.isClientSide()) {
            BlockState state = this.getBlockState();
            level.sendBlockUpdated(this.getBlockPos(), state, state, 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return this.saveWithoutMetadata(provider);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ==================== NBT 持久化 ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ListTag list = new ListTag();
        for (Link link : this.links) {
            CompoundTag c = new CompoundTag();
            c.putString("dimension", link.dimension().location().toString());
            c.putInt("x", link.pos().getX());
            c.putInt("y", link.pos().getY());
            c.putInt("z", link.pos().getZ());
            list.add(c);
        }
        tag.put(TAG_LINKS, list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.links.clear();
        this.resetBridge();
        ListTag list = tag.getList(TAG_LINKS, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(c.getString("dimension"))
            );
            BlockPos pos = new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z"));
            this.links.add(new Link(dimension, pos));
        }
        // 区块强加载在 onLoad() 中按持久化的绑定恢复
    }
}
