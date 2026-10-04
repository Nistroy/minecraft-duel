package io.github.nistroy.duel.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nistroy.duel.config.DuelConfig;
import io.github.nistroy.duel.server.DuelService;
import io.github.nistroy.duel.server.Kits;
import io.github.nistroy.duel.server.PlayerSnapshots;
import io.github.nistroy.duel.server.SnapshotStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Tests sur faux joueur. Le serveur GameTest ne charge pas les dimensions de datapack : la dimension
 * de l'arène se vérifie avec ./gradlew runServer.
 */
public final class DuelGameTests implements FabricGameTest {
	private static FakePlayer player(GameTestHelper helper) {
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "duel-test"));
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		player.moveTo(pos.x, pos.y, pos.z, 30f, 10f);
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void restoreBringsBackEverythingFromBeforeTheDuel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FakePlayer player = player(helper);
		Vec3 before = player.position();
		player.getInventory().setItem(0, new ItemStack(Items.STONE, 12));
		ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
		chestplate.setDamageValue(300);
		player.setItemSlot(EquipmentSlot.CHEST, chestplate);
		player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
		player.setExperienceLevels(7);
		player.setHealth(9f);
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2000));
		player.setGameMode(GameType.SURVIVAL);

		CompoundTag snapshot = PlayerSnapshots.capture(player);

		// Pendant le duel : tout change.
		player.getInventory().clearContent();
		player.getInventory().setItem(3, new ItemStack(Items.DIRT));
		player.getEnderChestInventory().clearContent();
		player.setExperienceLevels(0);
		player.setHealth(20f);
		player.removeAllEffects();
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 2000));
		player.setGameMode(GameType.ADVENTURE);
		player.moveTo(before.x + 20, before.y + 5, before.z);

		PlayerSnapshots.restore(level.getServer(), player, snapshot);

		helper.assertTrue(player.getInventory().getItem(0).is(Items.STONE) && player.getInventory().getItem(0).getCount() == 12, "pierres rendues");
		helper.assertTrue(player.getInventory().getItem(3).isEmpty(), "terre du duel retirée");
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		helper.assertTrue(chest.is(Items.DIAMOND_CHESTPLATE) && chest.getDamageValue() == 300, "plastron à son usure d'avant, lu : " + chest.getDamageValue());
		helper.assertTrue(player.getEnderChestInventory().getItem(0).getCount() == 3, "coffre de l'Ender rendu");
		helper.assertTrue(player.experienceLevel == 7, "XP rendue, lu : " + player.experienceLevel);
		helper.assertTrue(player.getHealth() == 9f, "PV rendus, lu : " + player.getHealth());
		helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "effet d'avant rendu");
		helper.assertFalse(player.hasEffect(MobEffects.POISON), "effet du duel retiré");
		helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "mode de jeu rendu");
		helper.assertTrue(player.position().distanceTo(before) < 0.01, "position rendue, lue : " + player.position());
		helper.assertTrue(player.getYRot() == 30f, "orientation rendue");
		helper.succeed();
	}

	private static Kits defaultKits(GameTestHelper helper) {
		return new Kits(DuelConfig.DEFAULT.kits(), helper.getLevel().registryAccess());
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void defaultKitsDecodeWithEnchantments(GameTestHelper helper) {
		Kits kits = defaultKits(helper);

		helper.assertTrue(kits.all().size() == DuelConfig.DEFAULT.kits().size(), "aucun kit par défaut écarté");
		ItemStack sword = kits.get("netherite").orElseThrow().items().stream()
				.map(Kits.Placed::stack).filter(s -> s.is(Items.NETHERITE_SWORD)).findFirst().orElseThrow();
		int sharpness = sword.getEnchantments().getLevel(
				helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
						.getHolderOrThrow(Enchantments.SHARPNESS));
		helper.assertTrue(sharpness == 5, "Tranchant V, lu : " + sharpness);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void kitReplacesWholeInventory(GameTestHelper helper) {
		FakePlayer player = player(helper);
		player.getInventory().setItem(0, new ItemStack(Items.STONE, 12));
		player.getInventory().setItem(20, new ItemStack(Items.DIAMOND, 5));
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));

		Kits.equip(player, defaultKits(helper).get("chevalier").orElseThrow());

		helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "casque du kit");
		helper.assertTrue(player.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD), "bouclier en seconde main");
		helper.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND_SWORD), "épée en case 0");
		helper.assertTrue(player.getInventory().getItem(20).isEmpty(), "diamants d'avant retirés");
		helper.assertTrue(player.getInventory().countItem(Items.STONE) == 0, "pierres d'avant retirées");
		helper.succeed();
	}

	/**
	 * Régression du crash du 2026-10-04 : JOIN part avant que PlayerList.placeNewPlayer ajoute le joueur
	 * à son monde. Le renvoyer là le met dans deux mondes (« UUID of added entity already exists »),
	 * puis le serveur plante à sa déconnexion suivante (DistanceManager.removePlayer).
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void joinLeavesRestoreForLaterTick(GameTestHelper helper) throws IOException {
		FakePlayer player = player(helper);
		Vec3 before = player.position();
		Path dir = Files.createTempDirectory("duel-gametest");
		SnapshotStore store = new SnapshotStore(dir);
		store.save(player.getUUID(), PlayerSnapshots.capture(player));
		player.moveTo(before.x + 20, before.y + 5, before.z);
		Vec3 inArena = player.position();
		DuelService service = new DuelService(helper.getLevel().getServer(), DuelConfig.DEFAULT, store,
				defaultKits(helper), dir.resolve("previews"));

		service.onJoin(player);

		helper.assertTrue(player.position().distanceTo(inArena) < 0.01, "pas de retour pendant JOIN, position lue : " + player.position());
		helper.assertTrue(store.has(player.getUUID()), "état d'avant duel gardé jusqu'au retour");
		helper.succeed();
	}
}
