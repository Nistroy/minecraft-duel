package io.github.nistroy.duel.network;

import io.github.nistroy.duel.Duel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serveur → client : image PNG d'aperçu d'une arène ({@code config/duel/previews/<id>.png} du
 * serveur), envoyée une fois par session. Images côté serveur : une arène s'ajoute sans nouvelle
 * version du mod, et les schémas d'autres auteurs restent hors du dépôt public.
 */
public record ArenaPreviewPayload(String arena, byte[] png) implements CustomPacketPayload {
	public static final Type<ArenaPreviewPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Duel.MOD_ID, "arena_preview"));

	/** Sous la limite d'un paquet personnalisé serveur → client (1 Mio). */
	public static final int MAX_BYTES = 900 * 1024;

	public static final StreamCodec<RegistryFriendlyByteBuf, ArenaPreviewPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, ArenaPreviewPayload::arena,
			ByteBufCodecs.byteArray(MAX_BYTES), ArenaPreviewPayload::png,
			ArenaPreviewPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
