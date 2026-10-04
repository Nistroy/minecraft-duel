package io.github.nistroy.duel.server;

import com.mojang.serialization.JsonOps;
import io.github.nistroy.duel.config.DuelConfig.KitItem;
import io.github.nistroy.duel.config.DuelConfig.KitSpec;
import io.github.nistroy.duel.config.KitSlot;
import io.github.nistroy.duel.compat.ModSlots;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Kits de la config décodés avec les registres du serveur ; un kit invalide est écarté (log ERROR). */
public final class Kits {
	private static final Logger LOG = LoggerFactory.getLogger("duel");

	public record Kit(String id, String name, ItemStack icon, List<Placed> items) {
	}

	public record Placed(KitSlot slot, ItemStack stack) {
	}

	private final Map<String, Kit> kits = new LinkedHashMap<>();

	public Kits(List<KitSpec> specs, HolderLookup.Provider registries) {
		RegistryOps<com.google.gson.JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
		for (KitSpec spec : specs) {
			try {
				kits.put(spec.id(), decode(spec, ops));
			} catch (IllegalArgumentException e) {
				LOG.error("Kit {} ignoré : {}", spec.id(), e.getMessage());
			}
		}
	}

	private static Kit decode(KitSpec spec, RegistryOps<com.google.gson.JsonElement> ops) {
		List<Placed> items = new ArrayList<>();
		for (KitItem item : spec.items()) {
			ItemStack stack = ItemStack.CODEC.parse(ops, item.item())
					.getOrThrow(error -> new IllegalArgumentException(item.item() + " : " + error));
			items.add(new Placed(KitSlot.parse(item.slot()), stack));
		}
		ItemStack icon = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(spec.icon()))
				.map(ItemStack::new)
				.orElseThrow(() -> new IllegalArgumentException("icône inconnue " + spec.icon()));
		return new Kit(spec.id(), spec.name(), icon, List.copyOf(items));
	}

	public Optional<Kit> get(String id) {
		return Optional.ofNullable(kits.get(id));
	}

	public List<Kit> all() {
		return List.copyOf(kits.values());
	}

	/** Remplace tout l'équipement (inventaire et emplacements de mods) par le kit. */
	public static void equip(ServerPlayer player, Kit kit) {
		player.getInventory().clearContent();
		ModSlots.clear(player);
		for (Placed placed : kit.items()) {
			ItemStack stack = placed.stack().copy();
			switch (placed.slot()) {
				case KitSlot.Inventory inventory -> player.getInventory().setItem(inventory.index(), stack);
				case KitSlot.Equipment equipment -> player.setItemSlot(switch (equipment) {
					case HEAD -> EquipmentSlot.HEAD;
					case CHEST -> EquipmentSlot.CHEST;
					case LEGS -> EquipmentSlot.LEGS;
					case FEET -> EquipmentSlot.FEET;
					case OFFHAND -> EquipmentSlot.OFFHAND;
				}, stack);
			}
		}
		player.inventoryMenu.broadcastChanges();
	}
}
