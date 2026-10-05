package io.github.nistroy.duel.rules;

import java.util.UUID;

/**
 * Défi accepté, en attente de téléportation : le temps du premier morceau du son, joué sur place
 * (Minecraft coupe tous les sons au changement de dimension).
 */
public record Departure(Challenges.Challenge challenge, long dueTick) {
	public static Departure after(Challenges.Challenge challenge, long now, int delayTicks) {
		return new Departure(challenge, now + delayTicks);
	}

	public boolean isDue(long now) {
		return now >= dueTick;
	}

	public boolean involves(UUID player) {
		return challenge.challenger().equals(player) || challenge.target().equals(player);
	}
}
