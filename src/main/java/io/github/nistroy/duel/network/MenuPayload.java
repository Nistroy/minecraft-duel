package io.github.nistroy.duel.network;

import io.github.nistroy.duel.Duel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Serveur → client : de quoi remplir l'écran {@code /duel}. Le client ne décide rien : son choix
 * repart en commande {@code /duel <joueur> <arène> <mode>}, validée par le serveur.
 *
 * @param running duel en cours (« A contre B »), vide sinon
 */
public record MenuPayload(List<Opponent> opponents, List<ArenaEntry> arenas, List<KitEntry> kits, String running)
		implements CustomPacketPayload {
	public static final Type<MenuPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Duel.MOD_ID, "menu"));

	/** {@code busy} = déjà dans un duel ou spectateur : grisé à l'écran. */
	public record Opponent(UUID id, String name, boolean busy) {
		static final StreamCodec<RegistryFriendlyByteBuf, Opponent> CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, Opponent::id,
				ByteBufCodecs.STRING_UTF8, Opponent::name,
				ByteBufCodecs.BOOL, Opponent::busy,
				Opponent::new);
	}

	/** {@code hasPreview} : une image suit dans un {@link ArenaPreviewPayload}. */
	public record ArenaEntry(String id, String name, boolean hasPreview) {
		static final StreamCodec<RegistryFriendlyByteBuf, ArenaEntry> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ArenaEntry::id,
				ByteBufCodecs.STRING_UTF8, ArenaEntry::name,
				ByteBufCodecs.BOOL, ArenaEntry::hasPreview,
				ArenaEntry::new);
	}

	public record KitEntry(String id, String name, ItemStack icon, List<ItemStack> items) {
		static final StreamCodec<RegistryFriendlyByteBuf, KitEntry> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, KitEntry::id,
				ByteBufCodecs.STRING_UTF8, KitEntry::name,
				ItemStack.OPTIONAL_STREAM_CODEC, KitEntry::icon,
				ItemStack.OPTIONAL_LIST_STREAM_CODEC, KitEntry::items,
				KitEntry::new);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, MenuPayload> CODEC = StreamCodec.composite(
			Opponent.CODEC.apply(ByteBufCodecs.list()), MenuPayload::opponents,
			ArenaEntry.CODEC.apply(ByteBufCodecs.list()), MenuPayload::arenas,
			KitEntry.CODEC.apply(ByteBufCodecs.list()), MenuPayload::kits,
			ByteBufCodecs.STRING_UTF8, MenuPayload::running,
			MenuPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
