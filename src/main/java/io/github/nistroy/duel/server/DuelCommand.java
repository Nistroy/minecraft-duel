package io.github.nistroy.duel.server;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /duel <joueur>}, {@code accepter|refuser [joueur]}, {@code regarder}, {@code quitter} ;
 * {@code admin arene|stop} pour les ops (console comprise).
 */
public final class DuelCommand {
	private static final String PLAYER = "joueur";

	private DuelCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Supplier<DuelService> service) {
		SuggestionProvider<CommandSourceStack> challengers = (ctx, builder) -> {
			ServerPlayer target = ctx.getSource().getPlayer();
			if (target == null) {
				return builder.buildFuture();
			}
			List<String> names = service.get().challengersOf(target).stream()
					.map(id -> ctx.getSource().getServer().getPlayerList().getPlayer(id))
					.filter(p -> p != null)
					.map(p -> p.getGameProfile().getName())
					.toList();
			return SharedSuggestionProvider.suggest(names, builder);
		};

		dispatcher.register(literal("duel")
				.then(literal("accepter")
						.executes(ctx -> answerOnly(ctx, service.get(), true))
						.then(argument(PLAYER, EntityArgument.player()).suggests(challengers)
								.executes(ctx -> run(() -> service.get().accept(self(ctx), EntityArgument.getPlayer(ctx, PLAYER))))))
				.then(literal("refuser")
						.executes(ctx -> answerOnly(ctx, service.get(), false))
						.then(argument(PLAYER, EntityArgument.player()).suggests(challengers)
								.executes(ctx -> run(() -> service.get().decline(self(ctx), EntityArgument.getPlayer(ctx, PLAYER))))))
				.then(literal("regarder")
						.executes(ctx -> run(() -> service.get().spectate(self(ctx)))))
				.then(literal("quitter")
						.executes(ctx -> run(() -> service.get().leave(self(ctx)))))
				.then(literal("admin").requires(source -> source.hasPermission(2))
						.then(literal("arene")
								.executes(ctx -> reply(ctx, service.get().buildArena())))
						.then(literal("stop")
								.executes(ctx -> reply(ctx, service.get().stop()))))
				.then(argument(PLAYER, EntityArgument.player())
						.executes(ctx -> run(() -> service.get().challenge(self(ctx), EntityArgument.getPlayer(ctx, PLAYER))))));
	}

	/** Sans nom : le seul défi en attente ; plusieurs → il faut préciser. */
	private static int answerOnly(CommandContext<CommandSourceStack> ctx, DuelService service, boolean accept)
			throws CommandSyntaxException {
		ServerPlayer target = self(ctx);
		List<UUID> pending = service.challengersOf(target);
		if (pending.size() != 1) {
			target.sendSystemMessage(Texts.error(pending.isEmpty()
					? "Aucun défi en attente."
					: "Plusieurs défis : /duel " + (accept ? "accepter" : "refuser") + " <joueur>"));
			return 0;
		}
		ServerPlayer challenger = ctx.getSource().getServer().getPlayerList().getPlayer(pending.getFirst());
		if (challenger == null) {
			target.sendSystemMessage(Texts.error("Ce joueur n'est plus connecté."));
			return 0;
		}
		if (accept) {
			service.accept(target, challenger);
		} else {
			service.decline(target, challenger);
		}
		return 1;
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int reply(CommandContext<CommandSourceStack> ctx, Component message) {
		ctx.getSource().sendSuccess(() -> message, true);
		return 1;
	}

	@FunctionalInterface
	private interface Action {
		void run() throws CommandSyntaxException;
	}

	private static int run(Action action) throws CommandSyntaxException {
		action.run();
		return 1;
	}
}
