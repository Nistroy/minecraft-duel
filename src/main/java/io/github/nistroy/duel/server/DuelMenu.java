package io.github.nistroy.duel.server;

import io.github.nistroy.duel.config.DuelConfig;
import io.github.nistroy.duel.config.DuelConfig.ArenaSpec;
import io.github.nistroy.duel.network.ArenaPreviewPayload;
import io.github.nistroy.duel.network.MenuPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Écran {@code /duel} des joueurs qui ont le mod côté client. Aperçus d'arène lus au démarrage dans
 * {@code config/duel/previews/<id>.png}, envoyés une fois par session et par joueur.
 */
final class DuelMenu {
	private static final Logger LOG = LoggerFactory.getLogger("duel");
	private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

	private final DuelConfig config;
	private final Kits kits;
	private final Map<String, byte[]> previews = new HashMap<>();
	private final Map<UUID, Set<String>> sent = new HashMap<>();

	DuelMenu(DuelConfig config, Kits kits, Path previewDir) {
		this.config = config;
		this.kits = kits;
		for (ArenaSpec arena : config.arenas()) {
			Path file = previewDir.resolve(arena.id() + ".png");
			if (Files.exists(file)) {
				loadPreview(arena.id(), file);
			}
		}
	}

	private void loadPreview(String arena, Path file) {
		try {
			byte[] png = Files.readAllBytes(file);
			if (png.length > ArenaPreviewPayload.MAX_BYTES
					|| !Arrays.equals(Arrays.copyOf(png, PNG_SIGNATURE.length), PNG_SIGNATURE)) {
				LOG.error("Aperçu {} ignoré : PNG attendu, {} Kio max", file, ArenaPreviewPayload.MAX_BYTES / 1024);
				return;
			}
			previews.put(arena, png);
		} catch (IOException e) {
			LOG.error("Aperçu {} illisible", file, e);
		}
	}

	boolean canOpen(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, MenuPayload.TYPE);
	}

	void open(ServerPlayer player, List<MenuPayload.Opponent> opponents, String running) {
		Set<String> alreadySent = sent.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
		previews.forEach((arena, png) -> {
			if (alreadySent.add(arena)) {
				ServerPlayNetworking.send(player, new ArenaPreviewPayload(arena, png));
			}
		});
		List<MenuPayload.ArenaEntry> arenas = config.arenas().stream()
				.map(a -> new MenuPayload.ArenaEntry(a.id(), a.name(), previews.containsKey(a.id())))
				.toList();
		List<MenuPayload.KitEntry> kitEntries = kits.all().stream()
				.map(k -> new MenuPayload.KitEntry(k.id(), k.name(), k.icon(),
						k.items().stream().map(p -> p.stack().copy()).toList()))
				.toList();
		ServerPlayNetworking.send(player, new MenuPayload(opponents, arenas, kitEntries, running));
	}

	void forget(UUID player) {
		sent.remove(player);
	}
}
