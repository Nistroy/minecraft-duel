package io.github.nistroy.duel.server;

import io.github.nistroy.duel.Duel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Sons du mod, jamais enregistrés dans le registre (un client sans le mod doit pouvoir se connecter) :
 * envoyés par id, le client avec le mod les trouve dans assets/duel/sounds.json, les autres n'entendent rien.
 * « It's you and me » (Valorant) coupé en 2 à 2,05 s (choix nistroy) : Minecraft coupe tous les sons au
 * changement de dimension, donc le début est joué sur place à l'acceptation, la suite après la téléportation.
 */
public final class DuelSounds {
	public static final String ACCEPT = "its_you_and_me";
	public static final String ARENA = "its_you_and_me_arena";
	/** Durée de {@link #ACCEPT} (2,05 s) : la téléportation attend la fin de ce morceau. */
	public static final int ACCEPT_TICKS = 41;
	private static final SoundEvent ACCEPT_EVENT = event(ACCEPT);
	private static final SoundEvent ARENA_EVENT = event(ARENA);

	private DuelSounds() {
	}

	public static void playAccept(ServerPlayer player) {
		play(player, ACCEPT_EVENT);
	}

	public static void playArena(ServerPlayer player) {
		play(player, ARENA_EVENT);
	}

	private static SoundEvent event(String id) {
		return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Duel.MOD_ID, id));
	}

	/** Catégorie Voix : réglable à part dans les options du jeu. */
	private static void play(ServerPlayer player, SoundEvent sound) {
		player.playNotifySound(sound, SoundSource.VOICE, 1f, 1f);
	}
}
