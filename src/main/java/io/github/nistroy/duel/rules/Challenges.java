package io.github.nistroy.duel.rules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Défis en attente, chacun valable {@code ttlTicks} ticks. Temps = compteur de ticks du serveur. */
public final class Challenges {
	/** Conditions du duel proposé : arène et kit ({@code kit} nul = chacun son équipement). */
	public record Terms(String arena, String kit) {
	}

	public record Challenge(UUID challenger, UUID target, long deadline, Terms terms) {
	}

	private record Key(UUID challenger, UUID target) {
	}

	private final int ttlTicks;
	private final Map<Key, Challenge> pending = new LinkedHashMap<>();

	public Challenges(int ttlTicks) {
		this.ttlTicks = ttlTicks;
	}

	/** Un nouveau défi vers la même personne remplace l'ancien (conditions et échéance). */
	public void add(UUID challenger, UUID target, long now, Terms terms) {
		Key key = new Key(challenger, target);
		pending.remove(key);
		pending.put(key, new Challenge(challenger, target, now + ttlTicks, terms));
	}

	/** Consomme le défi de {@code challenger} vers {@code target} s'il est encore valable. */
	public Optional<Challenge> accept(UUID target, UUID challenger, long now) {
		Challenge challenge = pending.remove(new Key(challenger, target));
		return challenge != null && now < challenge.deadline() ? Optional.of(challenge) : Optional.empty();
	}

	public boolean decline(UUID target, UUID challenger) {
		return pending.remove(new Key(challenger, target)) != null;
	}

	public List<UUID> challengersOf(UUID target, long now) {
		List<UUID> challengers = new ArrayList<>();
		for (Challenge challenge : pending.values()) {
			if (challenge.target().equals(target) && now < challenge.deadline()) {
				challengers.add(challenge.challenger());
			}
		}
		return challengers;
	}

	/** Retire et renvoie les défis arrivés à échéance, pour prévenir les joueurs une seule fois. */
	public List<Challenge> expire(long now) {
		List<Challenge> expired = new ArrayList<>();
		Iterator<Challenge> it = pending.values().iterator();
		while (it.hasNext()) {
			Challenge challenge = it.next();
			if (now >= challenge.deadline()) {
				expired.add(challenge);
				it.remove();
			}
		}
		return expired;
	}

	public void removeInvolving(UUID player) {
		pending.keySet().removeIf(key -> key.challenger().equals(player) || key.target().equals(player));
	}
}
