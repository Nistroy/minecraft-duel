package io.github.nistroy.duel.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nistroy.duel.server.PlayerSnapshots;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
}
