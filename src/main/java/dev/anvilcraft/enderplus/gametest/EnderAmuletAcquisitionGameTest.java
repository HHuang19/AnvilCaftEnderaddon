package dev.anvilcraft.enderplus.gametest;

import com.mojang.authlib.GameProfile;
import dev.anvilcraft.enderplus.AnvilcraftEnderplus;
import dev.anvilcraft.enderplus.init.AddonItems;
import dev.anvilcraft.enderplus.init.items.EnderAmuletItem;
import dev.dubhe.anvilcraft.api.amulet.AmuletManager;
import dev.dubhe.anvilcraft.api.amulet.def.IAmuletDefinition;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.init.registry.ModRegistryKeys;
import dev.dubhe.anvilcraft.recipe.JewelCraftingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.cache.RecipeCaches;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 末影护符获取方式的 GameTest：
 * 1) 末影伤害可匹配 anvilcraft_enderplus:ender 护符定义；
 * 2) 抽奖概率 100% 时抽奖必然授予护符，且持有护符后概率归零（全物品栏扫描）；
 * 3) 珠宝加工台配方缓存包含末影护符复制配方。
 */
@GameTestHolder(AnvilcraftEnderplus.MOD_ID)
@PrefixGameTestTemplate(false)
public class EnderAmuletAcquisitionGameTest {

    private static final ResourceKey<IAmuletDefinition> ENDER_DEF_KEY =
        ResourceKey.create(ModRegistryKeys.AMULET_DEF, AnvilcraftEnderplus.of("ender"));

    @GameTest(template = "amulet_bridge")
    public void endermanDamageMatchesEnderDefinition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = mockPlayer(level);
        List<Holder.Reference<IAmuletDefinition>> defs =
            AmuletManager.get(level.registryAccess()).getDefinitionMatchedDamage(player, endermanDamageSource(level));
        helper.assertTrue(
            defs.stream().anyMatch(def -> def.getKey().equals(ENDER_DEF_KEY)),
            "Enderman damage should match the anvilcraft_enderplus:ender amulet definition");
        helper.succeed();
    }

    @GameTest(template = "amulet_bridge")
    public void raffleGrantsEnderAmuletAtHundredPercent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = mockPlayer(level);
        attachStubConnection(player);
        AmuletManager manager = AmuletManager.get(level.registryAccess());
        Holder.Reference<IAmuletDefinition> enderDef = enderDef(level);

        manager.setRaffleProbability(player, enderDef, 100);
        helper.assertTrue(
            manager.getRaffleProbability(player, enderDef) == 100,
            "Forced raffle probability should read 100% before the raffle");

        manager.tryRaffle(player, endermanDamageSource(level));

        helper.assertTrue(
            hasEnderAmuletInInventory(player),
            "Forcing 100% should deterministically grant the ender amulet");
        helper.assertTrue(
            manager.getRaffleProbability(player, enderDef) == 0,
            "Owning the amulet should drop raffle probability to 0%");
        helper.succeed();
    }

    @GameTest(template = "amulet_bridge")
    public void raffleProbabilityZeroWhenAmuletInMainInventory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = mockPlayer(level);
        player.getInventory().setItem(9, new ItemStack(AddonItems.ENDER_AMULET.get()));
        AmuletManager manager = AmuletManager.get(level.registryAccess());
        helper.assertTrue(
            manager.getRaffleProbability(player, enderDef(level)) == 0,
            "An ender amulet anywhere in the main inventory must force 0% raffle probability");
        helper.succeed();
    }

    @GameTest(template = "amulet_bridge")
    public void jewelCraftingRecipeCachedForEnderAmulet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RecipeHolder<JewelCraftingRecipe> holder =
            RecipeCaches.getJewelRecipeByResult(new ItemStack(AddonItems.ENDER_AMULET.get()));
        helper.assertTrue(holder != null, "Jewel crafting recipe for the ender amulet should be cached");
        JewelCraftingRecipe recipe = holder.value();
        helper.assertTrue(
            recipe.result.is(AddonItems.ENDER_AMULET.get()),
            "Recipe result should be the ender amulet");
        helper.assertTrue(
            recipe.ingredients.size() == 2,
            "Recipe should have exactly two ingredients");
        helper.assertTrue(
            recipe.ingredients.get(0).test(new ItemStack(ModItems.SILVER_INGOT.get())),
            "First ingredient should be silver ingot");
        helper.assertTrue(
            recipe.ingredients.get(1).test(new ItemStack(Items.ENDER_EYE)),
            "Second ingredient should be ender eye");
        helper.succeed();
    }

    private static boolean hasEnderAmuletInInventory(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof EnderAmuletItem) {
                return true;
            }
        }
        return false;
    }

    private static Holder.Reference<IAmuletDefinition> enderDef(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(ModRegistryKeys.AMULET_DEF).getOrThrow(ENDER_DEF_KEY);
    }

    /** 创建一个未加入世界的末影人作为伤害来源（mobAttack 只需一个 LivingEntity 引用其类型）。 */
    private static DamageSource endermanDamageSource(ServerLevel level) {
        EnderMan enderman = EntityType.ENDERMAN.create(level);
        Objects.requireNonNull(enderman, "Failed to create an Enderman for the damage source");
        return level.damageSources().mobAttack(enderman);
    }

    /** 与 EnderAmuletBridgeGameTest 相同的 mock 玩家构造：不登入，避免 AnvilCraft 同步崩溃。 */
    private static ServerPlayer mockPlayer(ServerLevel level) {
        return new ServerPlayer(
            level.getServer(),
            level,
            new GameProfile(UUID.randomUUID(), "test-ender-acquire"),
            ClientInformation.createDefault());
    }

    /**
     * 抽奖成功路径会调用 {@link net.minecraft.world.entity.player.Inventory#placeItemBackInInventory}
     * 并向玩家连接发送槽位同步包；mock 玩家没有 connection，这里挂一个未连接的
     * {@link ServerGamePacketListenerImpl}，其 {@code send} 只会把包排入 pendingActions，不会崩溃。
     */
    private static void attachStubConnection(ServerPlayer player) {
        player.connection = new ServerGamePacketListenerImpl(
            player.server,
            new Connection(PacketFlow.SERVERBOUND),
            player,
            CommonListenerCookie.createInitial(player.getGameProfile(), false)
        );
    }
}
