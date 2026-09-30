package io.github.nistroy.duel.server;

import io.github.nistroy.duel.rules.Match.Result;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/** Textes du duel, en français ; les boutons sont des textes cliquables qui lancent la commande. */
final class Texts {
	private Texts() {
	}

	static MutableComponent info(String text) {
		return Component.literal("⚔ ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
	}

	static MutableComponent error(String text) {
		return Component.literal(text).withStyle(ChatFormatting.RED);
	}

	static MutableComponent button(String label, ChatFormatting color, String command, String hover) {
		return Component.literal("[" + label + "]").withStyle(style -> style
				.withColor(color)
				.withBold(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
	}

	static Component challenge(String challenger) {
		return info(challenger + " te défie en duel ! ")
				.append(button("Accepter", ChatFormatting.GREEN, "/duel accepter " + challenger, "Accepter le duel"))
				.append(" ")
				.append(button("Refuser", ChatFormatting.RED, "/duel refuser " + challenger, "Refuser le duel"));
	}

	static Component watch(String first, String second) {
		return info(first + " et " + second + " s'affrontent en duel ! ")
				.append(button("Regarder", ChatFormatting.AQUA, "/duel regarder", "Regarder en spectateur"));
	}

	static Component watching() {
		return info("Tu regardes le duel, retour automatique à la fin. ")
				.append(button("Quitter", ChatFormatting.GRAY, "/duel quitter", "Revenir où tu étais"));
	}

	static Component result(Result result, String first, String second, String winner, String loser) {
		return switch (result.reason()) {
			case KNOCKED_OUT -> info(winner + " a battu " + loser + " en duel !");
			case FORFEIT -> info(loser + " a abandonné le duel : victoire de " + winner + ".");
			case LEFT_ARENA -> info(loser + " est sorti de l'arène : victoire de " + winner + ".");
			case TIME_UP -> info("Duel " + first + " contre " + second + " : temps écoulé, égalité.");
			case CANCELLED -> info("Duel " + first + " contre " + second + " arrêté par un admin.");
		};
	}
}
