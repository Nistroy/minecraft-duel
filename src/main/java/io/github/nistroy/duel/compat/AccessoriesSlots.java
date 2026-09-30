package io.github.nistroy.duel.compat;

import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.server.level.ServerPlayer;

final class AccessoriesSlots {
	private AccessoriesSlots() {
	}

	/** Emplacements actifs seulement : les cosmétiques ne donnent aucun bonus. */
	static void clear(ServerPlayer player) {
		AccessoriesCapability.getOptionally(player).ifPresent(capability -> capability.getContainers().values()
				.forEach(container -> {
					container.getAccessories().clearContent();
					container.markChanged();
				}));
	}
}
