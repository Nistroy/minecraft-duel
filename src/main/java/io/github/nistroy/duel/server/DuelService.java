package io.github.nistroy.duel.server;

import io.github.nistroy.duel.config.DuelConfig;
import io.github.nistroy.duel.config.DuelConfig.Spot;
import io.github.nistroy.duel.rules.Challenges;
import io.github.nistroy.duel.rules.Match;
import io.github.nistroy.duel.rules.Match.Event;
import io.github.nistroy.duel.rules.Match.Phase;
import io.github.nistroy.duel.rules.Match.Reason;
import io.github.nistroy.duel.rules.Match.Result;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Un seul duel à la fois. Chaque participant (duelliste ou spectateur) a son état d'avant duel sur
 * disque ({@link SnapshotStore}) tant qu'il n'a pas été rendu : c'est ce fichier, pas la mémoire, qui
 * dit « à restaurer » — d'où la reprise après déconnexion ou crash.
 */
public final class DuelService {
	private static final Logger LOG = LoggerFactory.getLogger("duel");
	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Marge sous le bas de l'arène avant de remonter un spectateur (le vide tue même en spectateur). */
	private static final int SPECTATOR_FLOOR_MARGIN = 16;

	private final MinecraftServer server;
	private final DuelConfig config;
	private final SnapshotStore store;
	private final Challenges challenges;
	private final Set<UUID> spectators = new LinkedHashSet<>();
	private final Map<UUID, String> names = new HashMap<>();
	private Match match;
	private ArenaBuilder builder;

	public DuelService(MinecraftServer server, DuelConfig config, SnapshotStore store) {
		this.server = server;
		this.config = config;
		this.store = store;
		this.challenges = new Challenges(config.challengeTicks());
	}

	// --- Commandes -------------------------------------------------------------------------------

	public void challenge(ServerPlayer challenger, ServerPlayer target) {
		if (challenger == target) {
			challenger.sendSystemMessage(Texts.error("Tu ne peux pas te défier toi-même."));
			return;
		}
		Optional<String> problem = unavailable(challenger, true).or(() -> unavailable(target, false));
		if (problem.isPresent()) {
			challenger.sendSystemMessage(Texts.error(problem.get()));
			return;
		}
		challenges.add(challenger.getUUID(), target.getUUID(), now());
		challenger.sendSystemMessage(Texts.info("Défi envoyé à " + target.getGameProfile().getName()
				+ " (" + config.challengeSeconds() + " s pour accepter)."));
		target.sendSystemMessage(Texts.challenge(challenger.getGameProfile().getName()));
		target.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1f);
	}

	public void accept(ServerPlayer target, ServerPlayer challenger) {
		if (!challenges.accept(target.getUUID(), challenger.getUUID(), now())) {
			target.sendSystemMessage(Texts.error("Aucun défi de " + challenger.getGameProfile().getName() + " en attente."));
			return;
		}
		start(challenger, target);
	}

	public void decline(ServerPlayer target, ServerPlayer challenger) {
		if (!challenges.decline(target.getUUID(), challenger.getUUID())) {
			target.sendSystemMessage(Texts.error("Aucun défi de " + challenger.getGameProfile().getName() + " en attente."));
			return;
		}
		target.sendSystemMessage(Texts.info("Duel refusé."));
		challenger.sendSystemMessage(Texts.info(target.getGameProfile().getName() + " a refusé ton duel."));
	}

	public List<UUID> challengersOf(ServerPlayer target) {
		return challenges.challengersOf(target.getUUID(), now());
	}

	public void spectate(ServerPlayer player) {
		if (match == null || match.phase() == Phase.FINISHED) {
			player.sendSystemMessage(Texts.error("Aucun duel en cours."));
			return;
		}
		if (match.involves(player.getUUID())) {
			player.sendSystemMessage(Texts.error("Tu es dans le duel !"));
			return;
		}
		Optional<String> problem = unavailable(player, true);
		if (problem.isPresent()) {
			player.sendSystemMessage(Texts.error(problem.get()));
			return;
		}
		if (!saveSnapshot(player)) {
			return;
		}
		spectators.add(player.getUUID());
		prepareForTravel(player);
		Arena.teleport(player, Arena.level(server), config.spectator());
		player.setGameMode(GameType.SPECTATOR);
		player.sendSystemMessage(Texts.watching());
	}

	/** Spectateur : repart. Duelliste : abandonne. */
	public void leave(ServerPlayer player) {
		UUID id = player.getUUID();
		if (spectators.remove(id)) {
			restore(player);
		} else if (match != null && match.involves(id) && match.phase() != Phase.FINISHED) {
			defeat(id, Reason.FORFEIT);
		} else {
			player.sendSystemMessage(Texts.error("Tu n'es ni dans un duel ni spectateur."));
		}
	}

	public Component buildArena() {
		if (match != null || builder != null) {
			return Texts.error("Impossible pendant un duel ou une construction.");
		}
		ResourceLocation id = ResourceLocation.parse(config.structure());
		Optional<StructureTemplate> template = server.getStructureManager().get(id);
		if (template.isEmpty()) {
			return Texts.error("Structure " + id + " introuvable : fichier attendu dans <monde>/generated/"
					+ id.getNamespace() + "/structures/" + id.getPath() + ".nbt");
		}
		builder = new ArenaBuilder(Arena.level(server), template.get(),
				new BlockPos(config.originX(), config.originY(), config.originZ()));
		LOG.info("Pose de l'arène {} : {} tranches", id, builder.total());
		return Texts.info("Pose de l'arène en " + builder.total() + " tranches (1 par tick)…");
	}

	public Component stop() {
		if (match == null || !match.cancel()) {
			return Texts.error("Aucun duel en cours.");
		}
		announce(match.result().orElseThrow());
		return Texts.info("Duel arrêté.");
	}

	// --- Événements ------------------------------------------------------------------------------

	/** Hors combat, personne dans le duel ne prend de dégâts ; les spectateurs jamais. */
	public boolean allowDamage(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		UUID id = player.getUUID();
		if (spectators.contains(id)) {
			return false;
		}
		if (isDuelist(id)) {
			return match.damageAllowed();
		}
		return true;
	}

	/** Coup fatal en duel = défaite, jamais de vraie mort (ni KO Hardcore Revival, ni tombe). */
	public boolean allowDeath(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		UUID id = player.getUUID();
		boolean duelist = isDuelist(id) && Arena.contains(player);
		if (!duelist && !spectators.contains(id)) {
			return true;
		}
		player.setHealth(1f);
		if (duelist && match.phase() != Phase.FINISHED) {
			defeat(id, Reason.KNOCKED_OUT);
		}
		return false;
	}

	public void tick() {
		for (Challenges.Challenge expired : challenges.expire(now())) {
			ServerPlayer challenger = server.getPlayerList().getPlayer(expired.challenger());
			if (challenger != null) {
				challenger.sendSystemMessage(Texts.info("Ton défi à " + nameOf(expired.target()) + " a expiré."));
			}
		}
		tickBuilder();
		if (match == null) {
			return;
		}
		for (Event event : match.tick()) {
			handle(event);
		}
		if (match == null) {
			return;
		}
		if (match.phase() != Phase.FINISHED) {
			for (UUID id : List.of(match.first(), match.second())) {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if (player != null && (!Arena.contains(player) || config.isOutside(player.getX(), player.getY(), player.getZ()))) {
					defeat(id, Reason.LEFT_ARENA);
					break;
				}
			}
		}
		tickSpectators();
	}

	public void onJoin(ServerPlayer player) {
		UUID id = player.getUUID();
		spectators.remove(id);
		if (store.has(id)) {
			// Revenu d'une déconnexion ou d'un crash en plein duel : il retrouve son état d'avant.
			restore(player);
		}
	}

	public void onDisconnect(ServerPlayer player) {
		UUID id = player.getUUID();
		challenges.removeInvolving(id);
		spectators.remove(id);
		if (isDuelist(id) && match.phase() != Phase.FINISHED) {
			defeat(id, Reason.FORFEIT);
		}
		// Son fichier reste : état rendu à la prochaine connexion.
	}

	/** Arrêt du serveur : chacun est rendu tant qu'il est connecté, pour être sauvegardé propre. */
	public void onStopping() {
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (store.has(player.getUUID())) {
				restore(player);
			}
		}
		spectators.clear();
		match = null;
		builder = null;
	}

	// --- Déroulement -----------------------------------------------------------------------------

	private void start(ServerPlayer challenger, ServerPlayer target) {
		if (match != null) {
			challenger.sendSystemMessage(Texts.error("Un duel est déjà en cours."));
			target.sendSystemMessage(Texts.error("Un duel est déjà en cours, attends la fin."));
			return;
		}
		Optional<String> problem = unavailable(challenger, false).or(() -> unavailable(target, false));
		ServerLevel arena = Arena.level(server);
		if (problem.isEmpty() && builder != null) {
			problem = Optional.of("L'arène est en cours de construction.");
		}
		if (problem.isEmpty() && !Arena.isBuilt(arena, config)) {
			problem = Optional.of("L'arène n'est pas construite (admin : /duel admin arene).");
		}
		if (problem.isPresent()) {
			challenger.sendSystemMessage(Texts.error(problem.get()));
			target.sendSystemMessage(Texts.error(problem.get()));
			return;
		}

		challenges.removeInvolving(challenger.getUUID());
		challenges.removeInvolving(target.getUUID());
		if (!saveSnapshot(challenger)) {
			return;
		}
		if (!saveSnapshot(target)) {
			// Le premier n'a encore rien subi : sa photo peut être jetée.
			deleteSnapshot(challenger.getUUID());
			return;
		}

		Arena.clearEntities(arena);
		enter(challenger, arena, config.first());
		enter(target, arena, config.second());
		if (!Arena.contains(challenger) || !Arena.contains(target)) {
			// Téléportation refusée (joueur KO Hardcore Revival, par exemple) : on rend tout.
			restore(challenger);
			restore(target);
			challenger.sendSystemMessage(Texts.error("Téléportation dans l'arène impossible, duel annulé."));
			target.sendSystemMessage(Texts.error("Téléportation dans l'arène impossible, duel annulé."));
			return;
		}

		names.put(challenger.getUUID(), challenger.getGameProfile().getName());
		names.put(target.getUUID(), target.getGameProfile().getName());
		match = new Match(challenger.getUUID(), target.getUUID(),
				config.countdownTicks(), config.maxDurationTicks(), config.endPauseTicks());
		Component watch = Texts.watch(challenger.getGameProfile().getName(), target.getGameProfile().getName());
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != challenger && other != target) {
				other.sendSystemMessage(watch);
			}
		}
		LOG.info("Duel {} contre {}", challenger.getGameProfile().getName(), target.getGameProfile().getName());
	}

	/** Entrée en arène : PV, faim et armure au maximum, effets retirés, mode aventure (pas de blocs). */
	private void enter(ServerPlayer player, ServerLevel arena, Spot spot) {
		prepareForTravel(player);
		Arena.teleport(player, arena, spot);
		player.removeAllEffects();
		player.clearFire();
		player.resetFallDistance();
		player.setAirSupply(player.getMaxAirSupply());
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5f);
		for (EquipmentSlot slot : ARMOR) {
			ItemStack armor = player.getItemBySlot(slot);
			if (armor.isDamageableItem()) {
				armor.setDamageValue(0);
			}
		}
		player.setGameMode(GameType.ADVENTURE);
	}

	private static void prepareForTravel(ServerPlayer player) {
		player.stopRiding();
		if (player.isSleeping()) {
			player.stopSleepInBed(true, true);
		}
	}

	private void handle(Event event) {
		switch (event) {
			case Event.Countdown countdown -> title(Component.literal(String.valueOf(countdown.seconds())), false);
			case Event.FightStarted started -> title(Component.literal("Combat !"), true);
			case Event.Finished finished -> announce(finished.result());
			case Event.Teardown teardown -> teardown();
		}
	}

	private void defeat(UUID loser, Reason reason) {
		if (match.defeat(loser, reason)) {
			announce(match.result().orElseThrow());
		}
	}

	private void announce(Result result) {
		String first = nameOf(match.first());
		String second = nameOf(match.second());
		String winner = result.winner() == null ? null : nameOf(result.winner());
		String loser = result.loser() == null ? null : nameOf(result.loser());
		server.getPlayerList().broadcastSystemMessage(Texts.result(result, first, second, winner, loser), false);
		for (UUID id : List.of(match.first(), match.second())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				continue;
			}
			String text = id.equals(result.winner()) ? "Victoire !" : id.equals(result.loser()) ? "Défaite" : "Égalité";
			sendTitle(player, Component.literal(text));
		}
		LOG.info("Fin du duel {} contre {} : {}", first, second, result);
	}

	private void teardown() {
		Arena.clearEntities(Arena.level(server));
		List<UUID> participants = new ArrayList<>(List.of(match.first(), match.second()));
		participants.addAll(spectators);
		spectators.clear();
		match = null;
		names.clear();
		for (UUID id : participants) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				restore(player);
			}
		}
	}

	private void tickBuilder() {
		if (builder == null) {
			return;
		}
		if (builder.step()) {
			LOG.info("Arène posée ({} tranches)", builder.total());
			server.getPlayerList().broadcastSystemMessage(Texts.info("L'arène de duel est prête."), false);
			builder = null;
		}
	}

	private void tickSpectators() {
		ServerLevel arena = Arena.level(server);
		for (UUID id : List.copyOf(spectators)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				spectators.remove(id);
			} else if (!Arena.contains(player)) {
				// Parti par le menu spectateur (téléportation vers un joueur) : fin du visionnage.
				spectators.remove(id);
				restore(player);
			} else if (player.getY() < config.minY() - SPECTATOR_FLOOR_MARGIN) {
				Arena.teleport(player, arena, config.spectator());
			}
		}
	}

	// --- Outils ----------------------------------------------------------------------------------

	private boolean isDuelist(UUID id) {
		return match != null && match.involves(id);
	}

	/** Raison pour laquelle un joueur ne peut pas entrer dans l'arène, sinon vide. */
	private Optional<String> unavailable(ServerPlayer player, boolean self) {
		UUID id = player.getUUID();
		String subject = self ? "Tu es" : player.getGameProfile().getName() + " est";
		if (isDuelist(id) || spectators.contains(id)) {
			return Optional.of(subject + " déjà dans un duel.");
		}
		if (store.has(id)) {
			return Optional.of("L'état d'avant duel de " + player.getGameProfile().getName()
					+ " n'a pas encore été rendu : préviens l'admin.");
		}
		if (Arena.contains(player)) {
			return Optional.of(subject + " déjà dans l'arène.");
		}
		if (player.isDeadOrDying()) {
			return Optional.of(subject + " mort.");
		}
		return Optional.empty();
	}

	private boolean saveSnapshot(ServerPlayer player) {
		try {
			store.save(player.getUUID(), PlayerSnapshots.capture(player));
			return true;
		} catch (IOException | IllegalStateException e) {
			LOG.error("Sauvegarde de l'état de {} impossible", player.getGameProfile().getName(), e);
			player.sendSystemMessage(Texts.error("Sauvegarde de ton état impossible, rien n'a changé. Préviens l'admin."));
			return false;
		}
	}

	private void deleteSnapshot(UUID id) {
		try {
			store.delete(id);
		} catch (IOException e) {
			LOG.error("Suppression de l'état de {} impossible", id, e);
		}
	}

	/** Le fichier n'est effacé qu'après un retour réussi : en cas d'échec, l'admin garde la copie. */
	private void restore(ServerPlayer player) {
		UUID id = player.getUUID();
		try {
			Optional<CompoundTag> snapshot = store.load(id);
			if (snapshot.isEmpty()) {
				return;
			}
			PlayerSnapshots.restore(server, player, snapshot.get());
			store.delete(id);
		} catch (Exception e) {
			LOG.error("Retour de {} impossible, état gardé dans le dossier duel du monde",
					player.getGameProfile().getName(), e);
			player.sendSystemMessage(Texts.error("Problème au retour du duel : ton inventaire d'avant est gardé, préviens l'admin."));
		}
	}

	private void title(Component text, boolean ding) {
		List<UUID> audience = new ArrayList<>(List.of(match.first(), match.second()));
		audience.addAll(spectators);
		for (UUID id : audience) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				sendTitle(player, text);
				player.playNotifySound(ding ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.NOTE_BLOCK_HAT.value(),
						SoundSource.PLAYERS, 1f, 1f);
			}
		}
	}

	private static void sendTitle(ServerPlayer player, Component text) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 5));
		player.connection.send(new ClientboundSetTitleTextPacket(text));
	}

	private String nameOf(UUID id) {
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null) {
			return online.getGameProfile().getName();
		}
		return names.getOrDefault(id, id.toString());
	}

	private long now() {
		return server.getTickCount();
	}
}
