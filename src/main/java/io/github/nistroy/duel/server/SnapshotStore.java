package io.github.nistroy.duel.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/**
 * État d'avant duel d'un joueur, sur disque ({@code <monde>/duel/<uuid>.dat}) avant toute
 * modification : un crash ou une déconnexion en plein duel ne fait rien perdre, l'état est rendu à la
 * connexion suivante. Jamais écrasé : c'est la seule copie du vrai inventaire.
 */
public final class SnapshotStore {
	private static final String EXTENSION = ".dat";

	private final Path dir;

	public SnapshotStore(Path dir) {
		this.dir = dir;
	}

	public void save(UUID player, CompoundTag snapshot) throws IOException {
		Path file = file(player);
		if (Files.exists(file)) {
			throw new IllegalStateException("état d'avant duel déjà enregistré pour " + player);
		}
		Files.createDirectories(dir);
		// Écriture puis renommage atomique : jamais de fichier à moitié écrit après un crash.
		Path tmp = dir.resolve(player + ".tmp");
		NbtIo.writeCompressed(snapshot, tmp);
		Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
	}

	public Optional<CompoundTag> load(UUID player) throws IOException {
		Path file = file(player);
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		return Optional.of(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
	}

	public boolean has(UUID player) {
		return Files.exists(file(player));
	}

	public void delete(UUID player) throws IOException {
		Files.deleteIfExists(file(player));
	}

	public Set<UUID> pending() throws IOException {
		Set<UUID> players = new HashSet<>();
		if (!Files.isDirectory(dir)) {
			return players;
		}
		try (Stream<Path> files = Files.list(dir)) {
			files.map(f -> f.getFileName().toString())
					.filter(name -> name.endsWith(EXTENSION))
					.forEach(name -> players.add(UUID.fromString(name.substring(0, name.length() - EXTENSION.length()))));
		}
		return players;
	}

	private Path file(UUID player) {
		return dir.resolve(player + EXTENSION);
	}
}
