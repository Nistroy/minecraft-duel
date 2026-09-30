package io.github.nistroy.duel.compat;

import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.server.level.ServerPlayer;

final class TrinketsSlots {
	private TrinketsSlots() {
	}

	static void clear(ServerPlayer player) {
		TrinketsApi.getTrinketComponent(player).ifPresent(component -> component.getInventory().values()
				.forEach(group -> group.values().forEach(inventory -> inventory.clearContent())));
	}
}
