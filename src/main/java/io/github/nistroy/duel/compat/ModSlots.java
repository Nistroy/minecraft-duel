package io.github.nistroy.duel.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Emplacements d'objets ajoutés par des mods (bijoux, élytre, sac à dos) : vidés pendant un duel à
 * kit pour que seul le kit compte. Rendus avec le reste par la restauration de fin de duel.
 */
public final class ModSlots {
	private static final boolean TRINKETS = FabricLoader.getInstance().isModLoaded("trinkets");
	private static final boolean ACCESSORIES = FabricLoader.getInstance().isModLoaded("accessories");

	private ModSlots() {
	}

	public static void clear(ServerPlayer player) {
		// Classes chargées seulement si le mod est présent (sinon NoClassDefFoundError).
		if (TRINKETS) {
			TrinketsSlots.clear(player);
		}
		if (ACCESSORIES) {
			AccessoriesSlots.clear(player);
		}
	}
}
