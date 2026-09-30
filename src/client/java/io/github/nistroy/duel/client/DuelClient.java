package io.github.nistroy.duel.client;

import io.github.nistroy.duel.network.ArenaPreviewPayload;
import io.github.nistroy.duel.network.MenuPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class DuelClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(ArenaPreviewPayload.TYPE,
				(payload, context) -> Previews.put(payload.arena(), payload.png()));
		ClientPlayNetworking.registerGlobalReceiver(MenuPayload.TYPE,
				(payload, context) -> context.client().setScreen(new DuelScreen(payload)));
		// Le serveur renvoie les aperçus à la session suivante.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Previews.clear());
	}
}
