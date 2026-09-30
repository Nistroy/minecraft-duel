package io.github.nistroy.duel.config;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nistroy.duel.config.DuelConfig.ArenaSpec;
import io.github.nistroy.duel.config.DuelConfig.Spot;
import org.junit.jupiter.api.Test;

class DuelConfigTest {
	private static final String ARENA = """
			{"id": "%s", "name": "Test", "structure": "duel:test", "originX": 0, "originY": 0, "originZ": 0,
			 "first": {"x": 1.5, "y": 70, "z": 0.5, "yaw": 0, "pitch": 0},
			 "second": {"x": -1.5, "y": 70, "z": 0.5, "yaw": 0, "pitch": 0},
			 "spectator": {"x": 0, "y": 80, "z": 0, "yaw": 0, "pitch": 0},
			 "centerX": 0, "centerZ": 0, "radius": %d, "minY": 0}
			""";

	private static String arena(String id, int radius) {
		return ARENA.formatted(id, radius);
	}

	@Test
	void defaultsHaveMoltenCoreAndThreeKits() {
		DuelConfig config = DuelConfig.DEFAULT;

		assertEquals("molten_core", config.arenas().getFirst().id());
		assertEquals(3, config.kits().size());
		assertTrue(config.kit("netherite").isPresent());
		assertTrue(config.kit("absent").isEmpty());
	}

	@Test
	void emptyJsonGivesDefaults() {
		assertEquals(DuelConfig.DEFAULT, DuelConfig.parse("{}"));
	}

	@Test
	void defaultsSurviveRoundTrip() {
		assertEquals(DuelConfig.DEFAULT, DuelConfig.parse(DuelConfig.DEFAULT.toJson()));
	}

	@Test
	void givenListReplacesDefaultList() {
		DuelConfig config = DuelConfig.parse("{\"arenas\": [" + arena("a", 10) + "," + arena("b", 10) + "], \"maxDurationSeconds\": 120}");

		assertEquals(2, config.arenas().size());
		assertEquals("b", config.arena("b").orElseThrow().id());
		assertEquals(120, config.maxDurationSeconds());
		assertEquals(DuelConfig.DEFAULT.kits(), config.kits());
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
	void noArenaIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> DuelConfig.parse("{\"arenas\": []}"));
	}

	@Test
	void duplicateArenaIdIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> DuelConfig.parse("{\"arenas\": [" + arena("a", 10) + "," + arena("a", 10) + "]}"));
	}

	@Test
	void malformedIdsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> DuelConfig.parse("{\"arenas\": [" + arena("Pas Un Id", 10) + "]}"));
		assertThrows(IllegalArgumentException.class, () -> DuelConfig.parse(
				"{\"arenas\": [" + arena("a", 10).replace("duel:test", "pas un id") + "]}"));
	}

	@Test
	void spotsOutsideRadiusAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> DuelConfig.parse("{\"arenas\": [" + arena("a", 1) + "]}"));
	}

	@Test
	void unknownKitSlotIsRejected() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> DuelConfig.parse("""
				{"kits": [{"id": "k", "name": "K", "icon": "minecraft:stone",
				  "items": [{"slot": "tete", "item": {"id": "minecraft:stone"}}]}]}
				"""));
		assertTrue(error.getMessage().contains("tete"), error.getMessage());
	}

	@Test
	void ownGearModeNameIsReserved() {
		assertThrows(IllegalArgumentException.class, () -> DuelConfig.parse("""
				{"kits": [{"id": "equipement", "name": "K", "icon": "minecraft:stone", "items": []}]}
				"""));
	}

	@Test
	void inventorySlotMustBeInMainInventory() {
		assertThrows(IllegalArgumentException.class, () -> DuelConfig.parse("""
				{"kits": [{"id": "k", "name": "K", "icon": "minecraft:stone",
				  "items": [{"slot": "36", "item": {"id": "minecraft:stone"}}]}]}
				"""));
	}

	@Test
	void outsideArenaByRadiusOrHeight() {
		ArenaSpec arena = DuelConfig.DEFAULT.arenas().getFirst();
		Spot first = arena.first();

		assertFalse(arena.isOutside(first.x(), first.y(), first.z()));
		assertTrue(arena.isOutside(arena.centerX() + arena.radius() + 1, 66, arena.centerZ()));
		assertTrue(arena.isOutside(arena.centerX(), arena.minY() - 1, arena.centerZ()));
	}
}
