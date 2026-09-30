package io.github.nistroy.duel.rules;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Déroulement d'un duel, sans Minecraft : compte à rebours, combat (durée max → égalité), pause de
 * fin puis démontage. Le serveur appelle {@link #tick()} à chaque tick et réagit aux événements.
 */
public final class Match {
	public enum Phase { COUNTDOWN, FIGHT, FINISHED }

	public enum Reason { KNOCKED_OUT, FORFEIT, LEFT_ARENA, TIME_UP, CANCELLED }

	/** {@code winner} et {@code loser} nuls = égalité. */
	public record Result(UUID winner, UUID loser, Reason reason) {
	}

	public sealed interface Event {
		record Countdown(int seconds) implements Event {
		}

		record FightStarted() implements Event {
		}

		/** Fin décidée par le temps ; les défaites passent par {@link #defeat}. */
		record Finished(Result result) implements Event {
		}

		record Teardown() implements Event {
		}
	}

	private static final int TICKS_PER_SECOND = 20;

	private final UUID first;
	private final UUID second;
	private final int countdownTicks;
	private final int maxFightTicks;
	private final int endPauseTicks;

	private Phase phase = Phase.COUNTDOWN;
	private int ticksInPhase;
	private Result result;
	private boolean tornDown;

	public Match(UUID first, UUID second, int countdownTicks, int maxFightTicks, int endPauseTicks) {
		this.first = Objects.requireNonNull(first);
		this.second = Objects.requireNonNull(second);
		this.countdownTicks = countdownTicks;
		this.maxFightTicks = maxFightTicks;
		this.endPauseTicks = endPauseTicks;
	}

	public UUID first() {
		return first;
	}

	public UUID second() {
		return second;
	}

	public Phase phase() {
		return phase;
	}

	public boolean damageAllowed() {
		return phase == Phase.FIGHT;
	}

	public boolean involves(UUID player) {
		return first.equals(player) || second.equals(player);
	}

	public UUID opponentOf(UUID player) {
		if (first.equals(player)) {
			return second;
		}
		if (second.equals(player)) {
			return first;
		}
		throw new IllegalArgumentException("pas dans ce duel : " + player);
	}

	public Optional<Result> result() {
		return Optional.ofNullable(result);
	}

	/** @return faux si le duel était déjà fini (double coup fatal le même tick, par ex.). */
	public boolean defeat(UUID loser, Reason reason) {
		UUID winner = opponentOf(loser);
		if (phase == Phase.FINISHED) {
			return false;
		}
		finish(new Result(winner, loser, reason));
		return true;
	}

	/** Arrêt par un admin : égalité. */
	public boolean cancel() {
		if (phase == Phase.FINISHED) {
			return false;
		}
		finish(new Result(null, null, Reason.CANCELLED));
		return true;
	}

	public List<Event> tick() {
		ticksInPhase++;
		switch (phase) {
			case COUNTDOWN -> {
				if (ticksInPhase >= countdownTicks) {
					enter(Phase.FIGHT);
					return List.of(new Event.FightStarted());
				}
				// Annonce au début de chaque seconde restante : 3 au 1er tick, 2 une seconde plus tard…
				int remaining = countdownTicks - ticksInPhase + 1;
				if (ticksInPhase == 1 || remaining % TICKS_PER_SECOND == 0) {
					return List.of(new Event.Countdown(ceilSeconds(remaining)));
				}
			}
			case FIGHT -> {
				if (ticksInPhase >= maxFightTicks) {
					finish(new Result(null, null, Reason.TIME_UP));
					return List.of(new Event.Finished(result));
				}
			}
			case FINISHED -> {
				if (!tornDown && ticksInPhase >= endPauseTicks) {
					tornDown = true;
					return List.of(new Event.Teardown());
				}
			}
		}
		return List.of();
	}

	private void finish(Result result) {
		this.result = result;
		enter(Phase.FINISHED);
	}

	private void enter(Phase next) {
		phase = next;
		ticksInPhase = 0;
	}

	private static int ceilSeconds(int ticks) {
		return (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
	}
}
