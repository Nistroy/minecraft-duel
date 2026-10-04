package io.github.nistroy.duel.config;

import java.util.Locale;

/** Emplacement d'un objet de kit : armure, seconde main, ou case d'inventaire 0..35. */
public sealed interface KitSlot {
	int INVENTORY_SIZE = 36;

	enum Equipment implements KitSlot { HEAD, CHEST, LEGS, FEET, OFFHAND }

	record Inventory(int index) implements KitSlot {
	}

	static KitSlot parse(String slot) {
		if (slot != null && slot.matches("\\d{1,2}")) {
			int index = Integer.parseInt(slot);
			if (index < INVENTORY_SIZE) {
				return new Inventory(index);
			}
		}
		try {
			return Equipment.valueOf(String.valueOf(slot).toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("emplacement de kit inconnu « " + slot
					+ " » (head, chest, legs, feet, offhand, 0..35)");
		}
	}
}
