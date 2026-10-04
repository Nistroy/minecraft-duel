package io.github.nistroy.duel.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Réglages du duel, lus dans {@code config/duel.json} : arènes, kits, durées. JSON partiel accepté,
 * fusionné champ par champ sur {@code duel/default_config.json} du mod (une liste donnée remplace la
 * liste par défaut).
 */
public record DuelConfig(
		List<ArenaSpec> arenas, List<KitSpec> kits,
		int countdownSeconds, int maxDurationSeconds, int challengeSeconds, int endPauseSeconds) {

	public record Spot(double x, double y, double z, float yaw, float pitch) {
	}

	/** Arène posée une fois par {@code /duel admin arene <id>}, coin de la structure à {@code origin}. */
	public record ArenaSpec(
			String id, String name, String structure,
			int originX, int originY, int originZ,
			Spot first, Spot second, Spot spectator,
			double centerX, double centerZ, int radius, int minY) {

		public ArenaSpec {
			requireId("arena", id);
			requireResourceId("structure", structure);
			requirePositive("radius", radius);
			for (Spot spot : new Spot[] {first, second}) {
				if (Math.hypot(spot.x() - centerX, spot.z() - centerZ) > radius || spot.y() < minY) {
					throw new IllegalArgumentException("arène " + id + " : point de départ hors de l'arène " + spot);
				}
			}
		}

		/** Sortie de l'arène = défaite : trop loin du centre ou tombé sous {@code minY}. */
		public boolean isOutside(double x, double y, double z) {
			return y < minY || Math.hypot(x - centerX, z - centerZ) > radius;
		}
	}

	/**
	 * Équipement identique pour les deux duellistes. {@code item} = objet au format JSON du jeu
	 * ({@code id}, {@code count}, {@code components}), décodé côté serveur avec les registres.
	 */
	public record KitSpec(String id, String name, String icon, List<KitItem> items) {
		public KitSpec {
			requireId("kit", id);
			if (id.equals(OWN_GEAR)) {
				throw new IllegalArgumentException("kit : « " + OWN_GEAR + " » est réservé au duel avec son propre équipement");
			}
			requireResourceId("kit " + id + " icon", icon);
			items.forEach(item -> KitSlot.parse(item.slot()));
		}
	}

	/** {@code slot} : head, chest, legs, feet, offhand, ou 0..35 (inventaire, 0..8 = barre). */
	public record KitItem(String slot, JsonObject item) {
	}

	/** Mode de duel sans kit, dans la commande : {@code /duel <joueur> <arène> equipement}. */
	public static final String OWN_GEAR = "equipement";

	private static final int TICKS_PER_SECOND = 20;
	private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
	private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final DuelConfig DEFAULT = GSON.fromJson(readDefaults(), DuelConfig.class);

	public DuelConfig {
		if (arenas.isEmpty()) {
			throw new IllegalArgumentException("au moins une arène");
		}
		requireUnique("arène", arenas.stream().map(ArenaSpec::id).toList());
		requireUnique("kit", kits.stream().map(KitSpec::id).toList());
		requirePositive("countdownSeconds", countdownSeconds);
		requirePositive("maxDurationSeconds", maxDurationSeconds);
		requirePositive("challengeSeconds", challengeSeconds);
		requirePositive("endPauseSeconds", endPauseSeconds);
		arenas = List.copyOf(arenas);
		kits = List.copyOf(kits);
	}

	public static DuelConfig parse(String json) {
		JsonObject merged = JsonParser.parseString(readDefaults()).getAsJsonObject();
		JsonParser.parseString(json).getAsJsonObject().entrySet().forEach(e -> merged.add(e.getKey(), e.getValue()));
		try {
			return GSON.fromJson(merged, DuelConfig.class);
		} catch (RuntimeException e) {
			// Gson enveloppe l'erreur de validation d'un constructeur : on remonte le message lisible.
			for (Throwable cause = e; cause != null; cause = cause.getCause()) {
				if (cause instanceof IllegalArgumentException invalid) {
					throw invalid;
				}
			}
			throw e;
		}
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	public Optional<ArenaSpec> arena(String id) {
		return arenas.stream().filter(a -> a.id().equals(id)).findFirst();
	}

	public Optional<KitSpec> kit(String id) {
		return kits.stream().filter(k -> k.id().equals(id)).findFirst();
	}

	public int countdownTicks() {
		return countdownSeconds * TICKS_PER_SECOND;
	}

	public int maxDurationTicks() {
		return maxDurationSeconds * TICKS_PER_SECOND;
	}

	public int challengeTicks() {
		return challengeSeconds * TICKS_PER_SECOND;
	}

	public int endPauseTicks() {
		return endPauseSeconds * TICKS_PER_SECOND;
	}

	private static String readDefaults() {
		try (InputStream in = DuelConfig.class.getResourceAsStream("/duel/default_config.json")) {
			if (in == null) {
				throw new IllegalStateException("duel/default_config.json absent du jar");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void requireId(String what, String id) {
		if (id == null || !ID.matcher(id).matches()) {
			throw new IllegalArgumentException(what + " : identifiant invalide « " + id + " » (a-z, 0-9, _)");
		}
	}

	private static void requireResourceId(String what, String id) {
		if (id == null || !RESOURCE_ID.matcher(id).matches()) {
			throw new IllegalArgumentException(what + " : identifiant invalide « " + id + " »");
		}
	}

	private static void requirePositive(String name, int value) {
		if (value <= 0) {
			throw new IllegalArgumentException(name + " doit être > 0 (lu : " + value + ")");
		}
	}

	private static void requireUnique(String what, List<String> ids) {
		Set<String> seen = new HashSet<>();
		for (String id : ids) {
			if (!seen.add(id)) {
				throw new IllegalArgumentException(what + " en double : " + id);
			}
		}
	}
}
