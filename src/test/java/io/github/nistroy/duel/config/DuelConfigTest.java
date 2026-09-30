package io.github.nistroy.duel.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DuelConfigTest {
	@Test
	void emptyJsonGivesDefaults() {
		assertEquals(DuelConfig.DEFAULT, DuelConfig.parse("{}"));
	}

	@Test
	void defaultsSurviveRoundTrip() {
		assertEquals(DuelConfig.DEFAULT, DuelConfig.parse(DuelConfig.DEFAULT.toJson()));
	}

	@Test
	void partialJsonOverridesOnlyGivenFields() {
		DuelConfig config = DuelConfig.parse("""
				{"maxDurationSeconds": 120, "first": {"x": 1.5, "y": 70, "z": -3.5, "yaw": 45, "pitch": 0}}
				""");

		assertEquals(120, config.maxDurationSeconds());
		assertEquals(new DuelConfig.Spot(1.5, 70, -3.5, 45, 0), config.first());
		assertEquals(DuelConfig.DEFAULT.second(), config.second());
		assertEquals(DuelConfig.DEFAULT.structure(), config.structure());
	}

	@Test
	void ticksAreSecondsTimesTwenty() {
		DuelConfig config = DuelConfig.parse("""
				{"countdownSeconds": 3, "maxDurationSeconds": 300, "challengeSeconds": 60, "endPauseSeconds": 4}
				""");

		assertEquals(60, config.countdownTicks());
		assertEquals(6000, config.maxDurationTicks());
		assertEquals(1200, config.challengeTicks());
		assertEquals(80, config.endPauseTicks());
	}

	@Test
	void nonPositiveDurationIsRejected() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> DuelConfig.parse("{\"maxDurationSeconds\": 0}"));
		assertTrue(error.getMessage().contains("maxDurationSeconds"), error.getMessage());
	}

	@Test
	void malformedStructureIdIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> DuelConfig.parse("{\"structure\": \"Pas Un Id\"}"));
	}

	@Test
	void spotsOutsideRadiusAreRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> DuelConfig.parse("{\"radius\": 5, \"first\": {\"x\": 10.5, \"y\": 66, \"z\": 0.5, \"yaw\": 0, \"pitch\": 0}}"));
	}

	@Test
	void outsideArenaByRadiusOrHeight() {
		DuelConfig config = DuelConfig.DEFAULT;

		assertFalse(config.isOutside(config.first().x(), config.first().y(), config.first().z()));
		assertTrue(config.isOutside(config.centerX() + config.radius() + 1, 66, config.centerZ()));
		assertTrue(config.isOutside(config.centerX(), config.minY() - 1, config.centerZ()));
	}
}
