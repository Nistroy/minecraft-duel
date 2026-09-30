package io.github.nistroy.duel.rules;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nistroy.duel.rules.Match.Event;
import io.github.nistroy.duel.rules.Match.Phase;
import io.github.nistroy.duel.rules.Match.Reason;
import io.github.nistroy.duel.rules.Match.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchTest {
	private final UUID alice = UUID.randomUUID();
	private final UUID bob = UUID.randomUUID();
	private final UUID carol = UUID.randomUUID();

	// Compte à rebours 3 s, combat 10 s max, pause de fin 2 s.
	private Match match() {
		return new Match(alice, bob, 60, 200, 40);
	}

	private static List<Event> run(Match match, int ticks) {
		List<Event> events = new ArrayList<>();
		for (int i = 0; i < ticks; i++) {
			events.addAll(match.tick());
		}
		return events;
	}

	@Test
	void countdownAnnouncesEachSecondThenStartsFight() {
		Match match = match();

		assertEquals(Phase.COUNTDOWN, match.phase());
		assertFalse(match.damageAllowed());
		List<Event> events = run(match, 60);

		assertEquals(List.of(new Event.Countdown(3), new Event.Countdown(2), new Event.Countdown(1), new Event.FightStarted()), events);
		assertEquals(Phase.FIGHT, match.phase());
		assertTrue(match.damageAllowed());
	}

	@Test
	void defeatDuringFightFinishesWithOpponentAsWinner() {
		Match match = match();
		run(match, 60);

		assertTrue(match.defeat(bob, Reason.KNOCKED_OUT));

		assertEquals(Phase.FINISHED, match.phase());
		assertFalse(match.damageAllowed());
		assertEquals(new Result(alice, bob, Reason.KNOCKED_OUT), match.result().orElseThrow());
	}

	@Test
	void secondDefeatIsIgnored() {
		Match match = match();
		run(match, 60);
		match.defeat(bob, Reason.KNOCKED_OUT);

		assertFalse(match.defeat(alice, Reason.KNOCKED_OUT));
		assertEquals(alice, match.result().orElseThrow().winner());
	}

	@Test
	void forfeitDuringCountdownFinishesMatch() {
		Match match = match();
		run(match, 10);

		assertTrue(match.defeat(alice, Reason.FORFEIT));

		assertEquals(new Result(bob, alice, Reason.FORFEIT), match.result().orElseThrow());
		assertFalse(run(match, 60).contains(new Event.FightStarted()));
	}

	@Test
	void timeUpIsADraw() {
		Match match = match();
		List<Event> events = run(match, 60 + 200);

		assertEquals(new Event.Finished(new Result(null, null, Reason.TIME_UP)), events.getLast());
		assertEquals(Phase.FINISHED, match.phase());
	}

	@Test
	void teardownComesOnceAfterEndPause() {
		Match match = match();
		run(match, 60);
		match.defeat(bob, Reason.KNOCKED_OUT);

		assertEquals(List.of(), run(match, 39));
		assertEquals(List.of(new Event.Teardown()), match.tick());
		assertEquals(List.of(), run(match, 100));
	}

	@Test
	void cancelFinishesAsDrawAndIsIgnoredOnceFinished() {
		Match match = match();
		run(match, 60);

		assertTrue(match.cancel());

		assertEquals(new Result(null, null, Reason.CANCELLED), match.result().orElseThrow());
		assertFalse(match.cancel());
		assertFalse(match.defeat(bob, Reason.KNOCKED_OUT));
	}

	@Test
	void defeatOfOutsiderIsRejected() {
		Match match = match();

		assertThrows(IllegalArgumentException.class, () -> match.defeat(carol, Reason.FORFEIT));
	}

	@Test
	void opponentAndInvolvement() {
		Match match = match();

		assertEquals(bob, match.opponentOf(alice));
		assertEquals(alice, match.opponentOf(bob));
		assertTrue(match.involves(alice));
		assertFalse(match.involves(carol));
	}
}
