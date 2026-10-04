package io.github.nistroy.duel.server;

import io.github.nistroy.duel.Duel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Sons du mod, jamais enregistrés dans le registre (un client sans le mod doit pouvoir se connecter) :
 * envoyés par id, le client avec le mod les trouve dans assets/duel/sounds.json, les autres n'entendent rien.
 */
public final class DuelSounds {
	/** « It's you and me » (Valorant), joué à l'entrée dans l'arène, pendant le compte à rebours. */
	public static final String START = "its_you_and_me";
	private static final SoundEvent START_EVENT = SoundEvent.createVariableRangeEvent(
			ResourceLocation.fromNamespaceAndPath(Duel.MOD_ID, START));

	private DuelSounds() {
	}

	/** Catégorie Voix : réglable à part dans les options du jeu. */
	public static void playStart(ServerPlayer player) {
		player.playNotifySound(START_EVENT, SoundSource.VOICE, 1f, 1f);
	}
}
