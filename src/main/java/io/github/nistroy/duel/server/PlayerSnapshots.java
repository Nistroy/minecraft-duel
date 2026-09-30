package io.github.nistroy.duel.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * Photo complète d'un joueur : données sauvegardées par le jeu (inventaire, coffre de l'Ender, PV,
 * faim, XP, effets) et par les mods (emplacements Trinkets, Accessories, sac Traveler's Backpack),
 * plus position, dimension et mode de jeu.
 */
public final class PlayerSnapshots {
	private static final String PLAYER = "Player";
	private static final String DIMENSION = "Dimension";
	private static final String GAME_MODE = "GameMode";

	private PlayerSnapshots() {
	}

	public static CompoundTag capture(ServerPlayer player) {
		CompoundTag snapshot = new CompoundTag();
		snapshot.put(PLAYER, player.saveWithoutId(new CompoundTag()));
		snapshot.putString(DIMENSION, player.level().dimension().location().toString());
		snapshot.putDouble("X", player.getX());
		snapshot.putDouble("Y", player.getY());
		snapshot.putDouble("Z", player.getZ());
		snapshot.putFloat("Yaw", player.getYRot());
		snapshot.putFloat("Pitch", player.getXRot());
		snapshot.putString(GAME_MODE, player.gameMode.getGameModeForPlayer().getName());
		return snapshot;
	}

	/**
	 * Recharge la photo puis téléporte hors de l'arène. Le changement de dimension renvoie tout au
	 * client (inventaire, effets, XP, composants Trinkets/Accessories) : la téléportation vient après.
	 */
	public static void restore(MinecraftServer server, ServerPlayer player, CompoundTag snapshot) {
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
				ResourceLocation.parse(snapshot.getString(DIMENSION))));
		double x = snapshot.getDouble("X");
		double y = snapshot.getDouble("Y");
		double z = snapshot.getDouble("Z");
		if (level == null) {
			// Dimension d'origine disparue (mod retiré) : point d'apparition du monde.
			level = server.overworld();
			BlockPos spawn = level.getSharedSpawnPos();
			x = spawn.getX() + 0.5;
			y = spawn.getY();
			z = spawn.getZ() + 0.5;
		}

		CompoundTag data = snapshot.getCompound(PLAYER).copy();
		// Position du fichier = hors arène, dans une autre dimension : on reste sur place le temps de la
		// téléportation, qui fixe la vraie position.
		data.put("Pos", doubles(player.getX(), player.getY(), player.getZ()));
		data.put("Motion", doubles(0, 0, 0));

		player.stopRiding();
		// Le chargement ajoute les effets sauvegardés sans retirer ceux du duel.
		player.removeAllEffects();
		player.load(data);
		player.teleportTo(level, x, y, z, snapshot.getFloat("Yaw"), snapshot.getFloat("Pitch"));
		player.setGameMode(GameType.byName(snapshot.getString(GAME_MODE), GameType.SURVIVAL));
		player.resetSentInfo();
		server.getPlayerList().sendAllPlayerInfo(player);
	}

	private static ListTag doubles(double... values) {
		ListTag list = new ListTag();
		for (double value : values) {
			list.add(DoubleTag.valueOf(value));
		}
		return list;
	}
}
