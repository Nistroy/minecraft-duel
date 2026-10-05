package io.github.nistroy.duel.server;

import io.github.nistroy.duel.Duel;
import io.github.nistroy.duel.config.DuelConfig.ArenaSpec;
import io.github.nistroy.duel.config.DuelConfig.Spot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Dimension vide {@code duel:arena} (datapack du mod) où chaque arène est posée une fois pour toutes, à sa place. */
public final class Arena {
	public static final ResourceKey<Level> DIMENSION =
			ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Duel.MOD_ID, "arena"));

	private Arena() {
	}

	public static ServerLevel level(MinecraftServer server) {
		ServerLevel level = server.getLevel(DIMENSION);
		if (level == null) {
			throw new IllegalStateException("dimension " + DIMENSION.location() + " absente (datapack du mod non chargé ?)");
		}
		return level;
	}

	/**
	 * Pas de vol plané dans l'arène, même en mode équipement : l'élytre (torse, emplacement Elytra Slot,
	 * Élytre des âmes) déciderait du duel. Lu par EntityElytraEvents côté serveur et côté client (sinon le
	 * client planerait puis serait recalé par le serveur) : d'où la dimension, connue des deux.
	 */
	public static boolean allowsElytraFlight(Entity entity) {
		return !entity.level().dimension().equals(DIMENSION);
	}

	public static boolean contains(ServerPlayer player) {
		return player.level().dimension().equals(DIMENSION);
	}

	/** Arène posée = du sol sous les deux points de départ (sinon chute dans le vide dès l'arrivée). */
	public static boolean isBuilt(ServerLevel level, ArenaSpec arena) {
		return hasFloor(level, arena.first()) && hasFloor(level, arena.second());
	}

	private static boolean hasFloor(ServerLevel level, Spot spot) {
		return !level.getBlockState(BlockPos.containing(spot.x(), spot.y() - 1, spot.z())).isAir();
	}

	public static void teleport(ServerPlayer player, ServerLevel level, Spot spot) {
		player.teleportTo(level, spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch());
	}

	/** Flèches, objets lâchés, invocations : rien ne reste d'un duel à l'autre. */
	public static void clearEntities(ServerLevel level) {
		List<Entity> leftovers = new ArrayList<>();
		for (Entity entity : level.getAllEntities()) {
			if (!(entity instanceof Player)) {
				leftovers.add(entity);
			}
		}
		leftovers.forEach(Entity::discard);
	}
}
