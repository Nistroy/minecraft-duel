package io.github.nistroy.duel.rules;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChallengesTest {
	private final UUID alice = UUID.randomUUID();
	private final UUID bob = UUID.randomUUID();
	private final UUID carol = UUID.randomUUID();

	@Test
	void acceptConsumesPendingChallenge() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0);

		assertTrue(challenges.accept(bob, alice, 50));
		assertFalse(challenges.accept(bob, alice, 51), "un défi ne s'accepte qu'une fois");
	}

	@Test
	void onlyTheTargetCanAccept() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0);

		assertFalse(challenges.accept(alice, bob, 1));
		assertFalse(challenges.accept(carol, alice, 1));
	}

	@Test
	void expiredChallengeCannotBeAcceptedAndIsReportedOnce() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0);

		assertEquals(List.of(), challenges.expire(99));
		assertEquals(List.of(new Challenges.Challenge(alice, bob, 100)), challenges.expire(100));
		assertEquals(List.of(), challenges.expire(101));
		assertFalse(challenges.accept(bob, alice, 101));
	}

	@Test
	void acceptAfterDeadlineFailsEvenBeforeExpireRuns() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0);

		assertFalse(challenges.accept(bob, alice, 100));
	}

	@Test
	void challengingAgainRefreshesDeadline() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0);
		challenges.add(alice, bob, 80);

		assertEquals(List.of(), challenges.expire(150));
		assertTrue(challenges.accept(bob, alice, 150));
	}

	@Test
	void declineRemovesChallenge() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0);

		assertTrue(challenges.decline(bob, alice));
		assertFalse(challenges.decline(bob, alice));
		assertFalse(challenges.accept(bob, alice, 1));
	}

	@Test
	void challengersOfListsPendingSendersForTarget() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0);
		challenges.add(carol, bob, 0);
		challenges.add(bob, alice, 0);

		assertEquals(List.of(alice, carol), challenges.challengersOf(bob, 10));
		assertEquals(List.of(), challenges.challengersOf(bob, 100), "défis expirés exclus");
	}

	@Test
	void removeInvolvingDropsBothDirections() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0);
		challenges.add(bob, carol, 0);
		challenges.add(carol, alice, 0);

		challenges.removeInvolving(bob);

		assertFalse(challenges.accept(bob, alice, 1));
		assertFalse(challenges.accept(carol, bob, 1));
		assertTrue(challenges.accept(alice, carol, 1));
	}
}
