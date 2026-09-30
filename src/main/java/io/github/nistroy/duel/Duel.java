package io.github.nistroy.duel;

import io.github.nistroy.duel.config.DuelConfig;
import io.github.nistroy.duel.network.ArenaPreviewPayload;
import io.github.nistroy.duel.network.MenuPayload;
import io.github.nistroy.duel.server.DuelCommand;
import io.github.nistroy.duel.server.DuelService;
import io.github.nistroy.duel.server.Kits;
import io.github.nistroy.duel.server.SnapshotStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Duel implements ModInitializer {
	public static final String MOD_ID = "duel";
	private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
	/**
	 * Avant la phase par défaut, où Balm branche Hardcore Revival (KO) : sinon un coup fatal en duel
	 * mettrait KO au lieu de compter comme défaite.
	 */
	private static final ResourceLocation BEFORE_OTHER_MODS = ResourceLocation.fromNamespaceAndPath(MOD_ID, "before_other_mods");

	private static DuelConfig config;
	private static DuelService service;

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(MenuPayload.TYPE, MenuPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ArenaPreviewPayload.TYPE, ArenaPreviewPayload.CODEC);

		ServerLifecycleEvents.SERVER_STARTING.register(server -> config = loadConfig());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> service = new DuelService(server, config,
				new SnapshotStore(server.getWorldPath(LevelResource.ROOT).resolve(MOD_ID)),
				new Kits(config.kits(), server.registryAccess()),
				FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("previews")));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> service.onStopping());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> service = null);
		ServerTickEvents.END_SERVER_TICK.register(server -> service.tick());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> service.onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> service.onDisconnect(handler.player));

		ServerLivingEntityEvents.ALLOW_DAMAGE.addPhaseOrdering(BEFORE_OTHER_MODS, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(BEFORE_OTHER_MODS,
				(entity, source, amount) -> service == null || service.allowDamage(entity, source));
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(BEFORE_OTHER_MODS, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(BEFORE_OTHER_MODS,
				(entity, source, amount) -> service == null || service.allowDeath(entity));

		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
				DuelCommand.register(dispatcher, () -> service, () -> config));
	}

	/** Fichier absent → écrit avec les valeurs par défaut ; fichier invalide → erreur dans le log et défauts. */
	private static DuelConfig loadConfig() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
		try {
			if (Files.notExists(file)) {
				Files.writeString(file, DuelConfig.DEFAULT.toJson());
				return DuelConfig.DEFAULT;
			}
			return DuelConfig.parse(Files.readString(file));
		} catch (IOException | RuntimeException e) {
			LOG.error("{} illisible, réglages par défaut utilisés", file, e);
			return DuelConfig.DEFAULT;
		}
	}
}
