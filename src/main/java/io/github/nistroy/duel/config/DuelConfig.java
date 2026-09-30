package io.github.nistroy.duel.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.regex.Pattern;

/**
 * Réglages du duel, lus dans {@code config/duel.json}. Valeurs par défaut = arène « Molten Core »
 * posée par {@code /duel admin arene} avec son coin à {@code origin} : centre de la croix en 0 66 0.
 */
public record DuelConfig(
		String structure,
		int originX, int originY, int originZ,
		Spot first, Spot second, Spot spectator,
		double centerX, double centerZ, int radius, int minY,
		int countdownSeconds, int maxDurationSeconds, int challengeSeconds, int endPauseSeconds) {

	public record Spot(double x, double y, double z, float yaw, float pitch) {
	}

	private static final int TICKS_PER_SECOND = 20;
	private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final DuelConfig DEFAULT = new DuelConfig(
			"duel:molten_core",
			-73, 0, -67,
			// Face à face sur le bras est-ouest de la croix, sommet de la pyramide (sol y=65).
			new Spot(-9.5, 66, 0.5, -90, 0),
			new Spot(10.5, 66, 0.5, 90, 0),
			// Au-dessus, en retrait côté sud, regard vers le centre.
			new Spot(7.5, 82, 28.5, 166, 29),
			0.5, 0.5, 75, 0,
			3, 300, 60, 4);

	public DuelConfig {
		if (!RESOURCE_ID.matcher(structure).matches()) {
			throw new IllegalArgumentException("structure : identifiant invalide « " + structure + " »");
		}
		requirePositive("radius", radius);
		requirePositive("countdownSeconds", countdownSeconds);
		requirePositive("maxDurationSeconds", maxDurationSeconds);
		requirePositive("challengeSeconds", challengeSeconds);
		requirePositive("endPauseSeconds", endPauseSeconds);
		for (Spot spot : new Spot[] {first, second}) {
			if (Math.hypot(spot.x() - centerX, spot.z() - centerZ) > radius || spot.y() < minY) {
				throw new IllegalArgumentException("point de départ hors de l'arène : " + spot);
			}
		}
	}

	private static void requirePositive(String name, int value) {
		if (value <= 0) {
			throw new IllegalArgumentException(name + " doit être > 0 (lu : " + value + ")");
		}
	}

	/** JSON partiel accepté : chaque champ absent garde sa valeur par défaut. */
	public static DuelConfig parse(String json) {
		JsonObject merged = JsonParser.parseString(DEFAULT.toJson()).getAsJsonObject();
		JsonParser.parseString(json).getAsJsonObject().entrySet().forEach(e -> merged.add(e.getKey(), e.getValue()));
		try {
			return GSON.fromJson(merged, DuelConfig.class);
		} catch (RuntimeException e) {
			// Gson enveloppe l'erreur de validation du constructeur : on remonte le message lisible.
			if (e.getCause() instanceof IllegalArgumentException invalid) {
				throw invalid;
			}
			throw e;
		}
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	/** Sortie de l'arène = défaite : trop loin du centre ou tombé sous {@code minY}. */
	public boolean isOutside(double x, double y, double z) {
		return y < minY || Math.hypot(x - centerX, z - centerZ) > radius;
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
}
