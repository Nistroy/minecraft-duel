package io.github.nistroy.duel.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotStoreTest {
	@TempDir
	Path dir;

	private static CompoundTag tag(String value) {
		CompoundTag tag = new CompoundTag();
		tag.putString("v", value);
		return tag;
	}

	@Test
	void savedSnapshotIsReadBack() throws IOException {
		SnapshotStore store = new SnapshotStore(dir.resolve("duel"));
		UUID player = UUID.randomUUID();

		store.save(player, tag("avant"));

		assertEquals(tag("avant"), store.load(player).orElseThrow());
	}

	@Test
	void missingSnapshotIsEmpty() throws IOException {
		assertTrue(new SnapshotStore(dir).load(UUID.randomUUID()).isEmpty());
	}

	@Test
	void existingSnapshotIsNeverOverwritten() throws IOException {
		SnapshotStore store = new SnapshotStore(dir);
		UUID player = UUID.randomUUID();
		store.save(player, tag("avant"));

		assertThrows(IllegalStateException.class, () -> store.save(player, tag("pendant")));
		assertEquals(tag("avant"), store.load(player).orElseThrow());
	}

	@Test
	void deleteRemovesSnapshot() throws IOException {
		SnapshotStore store = new SnapshotStore(dir);
		UUID player = UUID.randomUUID();
		store.save(player, tag("avant"));

		store.delete(player);

		assertTrue(store.load(player).isEmpty());
		assertFalse(store.has(player));
	}

	@Test
	void pendingListsEverySavedPlayerAndIgnoresOtherFiles() throws IOException {
		SnapshotStore store = new SnapshotStore(dir);
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		store.save(a, tag("a"));
		store.save(b, tag("b"));
		Files.writeString(dir.resolve("notes.txt"), "rien");

		assertEquals(Set.of(a, b), store.pending());
	}

	@Test
	void noTemporaryFileLeftBehind() throws IOException {
		SnapshotStore store = new SnapshotStore(dir);
		store.save(UUID.randomUUID(), tag("a"));

		try (var files = Files.list(dir)) {
			assertTrue(files.allMatch(f -> f.toString().endsWith(".dat")));
		}
	}
}
