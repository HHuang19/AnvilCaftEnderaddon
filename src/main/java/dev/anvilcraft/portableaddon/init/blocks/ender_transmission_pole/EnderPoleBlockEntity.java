package dev.anvilcraft.portableaddon.init.blocks.ender_transmission_pole;

import dev.anvilcraft.portableaddon.init.AddonBlocks;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 末影输电杆的方块实体 (BlockEntity)
 * <p>
 * 同时作为本地电网的 {@link PowerComponentType#TRANSMITTER} 与跨维度电力桥元件
 * （{@link EnderBridge}），把两根互相绑定的杆所在的两个电网变成"共享电网"：
 * <ul>
 *   <li>仍以顶部段接入本地电网，由红石信号控制开关（沿用原逻辑）；</li>
 *   <li>每电网刻（{@code gridTick}）重算本地电网的纯发电/用电（排除所有桥元件与储电），
 *       并把远端电网的发电/用电镜像到本地（{@code getOutputPower()} = 远端发电，
 *       {@code getInputPower()} = 远端用电）。</li>
 * </ul>
 * 由此两侧电网的 {@code generate}/{@code consume} 完全相同，等效于同一电网：
 * 发送侧能发多少、接收侧就能用多少；任一侧过载则两侧同时过载。
 * <p>
 * 已知限制（储电类）：电网自身的储电充放发生在 {@code flush()} 之后，无法跨维度镜像。
 * 因此单侧储电表现正确（唯一储电兜底整个共享电网的富余/缺口）；若两侧同时放置储电，
 * 两侧电网会各自按共享富余/缺口充放，导致储电重复计数（文档化的边界情形）。
 */
public class EnderPoleBlockEntity extends AbstractTransmissionPoleBlockEntity
    implements EnderBridge, IPowerProducer, IPowerConsumer {

    private static final String TAG_BOUND = "bound";
    private static final String TAG_BOUND_DIMENSION = "boundDimension";
    private static final String TAG_BOUND_X = "boundX";
    private static final String TAG_BOUND_Y = "boundY";
    private static final String TAG_BOUND_Z = "boundZ";

    @Nullable
    private PowerGrid grid = null;

    /** 是否已绑定远端末影输电杆 */
    private boolean bound = false;
    /** 绑定目标维度 */
    @Nullable
    private ResourceKey<Level> boundDimension = null;
    /** 绑定目标顶段坐标 */
    @Nullable
    private BlockPos boundPos = null;

    /** 本电网（排除桥元件与储电）的纯发电量——供远端镜像 */
    private int reportedGen = 0;
    /** 本电网（排除桥元件与储电）的纯用电量——供远端镜像 */
    private int reportedCon = 0;
    /** 镜像自远端电网的发电量（注入本电网，表现为发电） */
    private int mirroredGen = 0;
    /** 镜像自远端电网的用电量（计入本电网，表现为耗电） */
    private int mirroredCon = 0;

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
     * 记录下来供远端镜像，再把远端电网的发电/用电镜像到本电网。
     * 于是两侧电网的 {@code generate}/{@code consume} 完全相同（共享电网），
     * 且镜像值只依赖远端上次上报的纯值，不依赖本杆自身输出，因此不存在振荡。
     */
    @Override
    public void gridTick() {
        PowerGrid powerGrid = this.grid;
        if (powerGrid == null) return;

        int gen = 0;
        int con = 0;
        for (IPowerComponent component : List.copyOf(powerGrid.getComponents())) {
            if (component instanceof IPowerStorage) continue;
            if (component instanceof EnderBridge) continue;
            if (component instanceof IPowerProducer producer) gen += producer.getOutputPower();
            if (component instanceof IPowerConsumer consumer) con += consumer.getInputPower();
        }
        this.reportedGen = gen;
        this.reportedCon = con;

        EnderPoleBlockEntity remote = this.findRemote();
        if (remote == null || !remote.isBound() || remote.getGrid() == null || remote.getGrid() == powerGrid) {
            // 远端未加载/已解除绑定/不在电网/与本地处于同一电网 → 不镜像
            this.mirroredGen = 0;
            this.mirroredCon = 0;
        } else {
            this.mirroredGen = remote.getReportedGen();
            this.mirroredCon = remote.getReportedCon();
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
        return this.bound && this.boundDimension != null && this.boundPos != null;
    }

    public boolean isBoundTo(ResourceKey<Level> dimension, BlockPos pos) {
        return this.isBound() && this.boundDimension.equals(dimension) && this.boundPos.equals(pos);
    }

    @Nullable
    public ResourceKey<Level> getBoundDimension() {
        return this.boundDimension;
    }

    @Nullable
    public BlockPos getBoundPos() {
        return this.boundPos;
    }

    /**
     * 设置绑定目标（不触碰对方；双向绑定由末影链接器保证）。
     */
    public void setBound(ResourceKey<Level> dimension, BlockPos pos) {
        this.bound = true;
        this.boundDimension = dimension;
        this.boundPos = pos;
        this.resetBridge();
        this.setChanged();
    }

    /**
     * 解除绑定：若远端仍指向本杆则一并解除。
     */
    public void unbind() {
        if (!this.isBound()) return;
        ResourceKey<Level> oldDimension = this.boundDimension;
        BlockPos oldPos = this.boundPos;
        this.bound = false;
        this.boundDimension = null;
        this.boundPos = null;
        this.resetBridge();
        this.setChanged();
        // 远端若仍指向本杆，递归解除（远端解除后不再指向本杆，递归终止）
        Level level = this.getLevel();
        if (level instanceof ServerLevel serverLevel) {
            Level targetLevel = serverLevel.getServer().getLevel(oldDimension);
            if (targetLevel != null
                && targetLevel.getBlockEntity(oldPos) instanceof EnderPoleBlockEntity partner
                && partner.isBoundTo(level.dimension(), this.getBlockPos())) {
                partner.unbind();
            }
        }
    }

    private void resetBridge() {
        this.mirroredGen = 0;
        this.mirroredCon = 0;
        this.reportedGen = 0;
        this.reportedCon = 0;
    }

    /** 跨维度读取绑定的远端杆（不加载区块；远端未加载/不是杆时返回 null） */
    @Nullable
    private EnderPoleBlockEntity findRemote() {
        if (!this.isBound() || !(this.getLevel() instanceof ServerLevel serverLevel)) return null;
        Level targetLevel = serverLevel.getServer().getLevel(this.boundDimension);
        if (targetLevel == null || !targetLevel.isLoaded(this.boundPos)) return null;
        BlockEntity blockEntity = targetLevel.getBlockEntity(this.boundPos);
        return blockEntity instanceof EnderPoleBlockEntity remote ? remote : null;
    }

    // ==================== NBT 持久化 ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (this.isBound()) {
            tag.putBoolean(TAG_BOUND, true);
            tag.putString(TAG_BOUND_DIMENSION, this.boundDimension.location().toString());
            tag.putInt(TAG_BOUND_X, this.boundPos.getX());
            tag.putInt(TAG_BOUND_Y, this.boundPos.getY());
            tag.putInt(TAG_BOUND_Z, this.boundPos.getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.bound = false;
        this.boundDimension = null;
        this.boundPos = null;
        this.resetBridge();
        if (tag.getBoolean(TAG_BOUND)) {
            this.bound = true;
            this.boundDimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(tag.getString(TAG_BOUND_DIMENSION))
            );
            this.boundPos = new BlockPos(tag.getInt(TAG_BOUND_X), tag.getInt(TAG_BOUND_Y), tag.getInt(TAG_BOUND_Z));
        }
    }
}
