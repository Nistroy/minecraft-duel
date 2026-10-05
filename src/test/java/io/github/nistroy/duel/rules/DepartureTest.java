package io.github.nistroy.duel.rules;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DepartureTest {
	private final UUID alice = UUID.randomUUID();
	private final UUID bob = UUID.randomUUID();
	private final Challenges.Challenge challenge =
			new Challenges.Challenge(alice, bob, 500, new Challenges.Terms("molten_core", null));

	@Test
	void leavesExactlyAfterTheDelay() {
		Departure departure = Departure.after(challenge, 100, 41);

		assertFalse(departure.isDue(140), "pas avant la fin du premier morceau du son");
		assertTrue(departure.isDue(141));
	}

	@Test
	void involvesOnlyTheTwoDuelists() {
		Departure departure = Departure.after(challenge, 0, 41);

		assertTrue(departure.involves(alice));
		assertTrue(departure.involves(bob));
		assertFalse(departure.involves(UUID.randomUUID()));
	}
}
