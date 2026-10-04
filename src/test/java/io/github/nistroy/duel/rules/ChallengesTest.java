package io.github.nistroy.duel.rules;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChallengesTest {
	private final UUID alice = UUID.randomUUID();
	private final UUID bob = UUID.randomUUID();
	private final UUID carol = UUID.randomUUID();
	private final Challenges.Terms terms = new Challenges.Terms("molten_core", null);

	@Test
	void acceptConsumesPendingChallenge() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0, terms);

		assertEquals(terms, challenges.accept(bob, alice, 50).orElseThrow().terms());
		assertTrue(challenges.accept(bob, alice, 51).isEmpty(), "un défi ne s'accepte qu'une fois");
	}

	@Test
	void onlyTheTargetCanAccept() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0, terms);

		assertTrue(challenges.accept(alice, bob, 1).isEmpty());
		assertTrue(challenges.accept(carol, alice, 1).isEmpty());
	}

	@Test
	void expiredChallengeCannotBeAcceptedAndIsReportedOnce() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0, terms);

		assertEquals(List.of(), challenges.expire(99));
		assertEquals(List.of(new Challenges.Challenge(alice, bob, 100, terms)), challenges.expire(100));
		assertEquals(List.of(), challenges.expire(101));
		assertTrue(challenges.accept(bob, alice, 101).isEmpty());
	}

	@Test
	void acceptAfterDeadlineFailsEvenBeforeExpireRuns() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0, terms);

		assertTrue(challenges.accept(bob, alice, 100).isEmpty());
	}

	@Test
	void challengingAgainRefreshesDeadline() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0, terms);
		challenges.add(alice, bob, 80, terms);

		assertEquals(List.of(), challenges.expire(150));
		assertTrue(challenges.accept(bob, alice, 150).isPresent());
	}

	@Test
	void newChallengeReplacesTerms() {
		Challenges challenges = new Challenges(100);
		Challenges.Terms kit = new Challenges.Terms("molten_core", "netherite");
		challenges.add(alice, bob, 0, terms);
		challenges.add(alice, bob, 10, kit);

		assertEquals(kit, challenges.accept(bob, alice, 20).orElseThrow().terms());
	}

	@Test
	void declineRemovesChallenge() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0, terms);

		assertTrue(challenges.decline(bob, alice));
		assertFalse(challenges.decline(bob, alice));
		assertTrue(challenges.accept(bob, alice, 1).isEmpty());
	}

	@Test
	void challengersOfListsPendingSendersForTarget() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0, terms);
		challenges.add(carol, bob, 0, terms);
		challenges.add(bob, alice, 0, terms);

		assertEquals(List.of(alice, carol), challenges.challengersOf(bob, 10));
		assertEquals(List.of(), challenges.challengersOf(bob, 100), "défis expirés exclus");
	}

	@Test
	void removeInvolvingDropsBothDirections() {
		Challenges challenges = new Challenges(100);
		challenges.add(alice, bob, 0, terms);
		challenges.add(bob, carol, 0, terms);
		challenges.add(carol, alice, 0, terms);

		challenges.removeInvolving(bob);

		assertTrue(challenges.accept(bob, alice, 1).isEmpty());
		assertTrue(challenges.accept(carol, bob, 1).isEmpty());
		assertTrue(challenges.accept(alice, carol, 1).isPresent());
	}
}
