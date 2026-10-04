package io.github.nistroy.duel.client;

import io.github.nistroy.duel.config.DuelConfig;
import io.github.nistroy.duel.network.MenuPayload;
import io.github.nistroy.duel.network.MenuPayload.ArenaEntry;
import io.github.nistroy.duel.network.MenuPayload.KitEntry;
import io.github.nistroy.duel.network.MenuPayload.Opponent;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Écran {@code /duel} : adversaire (têtes), arène (aperçu, flèches si plusieurs), mode (son
 * équipement ou un kit, contenu du kit affiché). « Défier » envoie la commande
 * {@code /duel <joueur> <arène> <mode>} : le serveur reste seul juge.
 */
final class DuelScreen extends Screen {
	private static final int WIDTH = 400;
	private static final int HEIGHT = 222;
	private static final int ROW = 20;
	private static final int COLUMN_TOP = 32;
	private static final int PLAYERS_X = 8;
	private static final int PLAYERS_W = 110;
	private static final int ARENA_X = 124;
	private static final int ARENA_W = 152;
	private static final int ARENA_IMAGE_H = 86;
	private static final int MODES_X = 282;
	private static final int MODES_W = 110;
	private static final int KIT_CELL = 18;
	private static final int KIT_COLUMNS = 6;
	private static final int MAX_PLAYER_ROWS = 7;

	private static final int PANEL = 0xE8141418;
	private static final int BORDER = 0xFFC8A040;
	private static final int FRAME = 0xFF3A3A44;
	private static final int HOVER = 0x30FFFFFF;
	private static final int SELECTED = 0x60C8A040;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFF8A8A96;
	private static final int GOLD = 0xFFE8C060;

	private final List<Opponent> opponents;
	private final List<ArenaEntry> arenas;
	private final List<KitEntry> kits;
	private final String running;
	private final ItemStack ownGearIcon = new ItemStack(Items.IRON_CHESTPLATE);

	private int left;
	private int top;
	private Opponent opponent;
	private int arena;
	/** -1 = son propre équipement, sinon index dans {@link #kits}. */
	private int mode = -1;
	private Button challenge;

	DuelScreen(MenuPayload menu) {
		super(Component.literal("Duel"));
		this.opponents = menu.opponents();
		this.arenas = menu.arenas();
		this.kits = menu.kits();
		this.running = menu.running();
	}

	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		int buttonsY = top + HEIGHT - 26;
		challenge = addRenderableWidget(Button.builder(Component.literal("Défier"), b -> sendChallenge())
				.bounds(left + 8, buttonsY, 120, 20).build());
		Button watch = addRenderableWidget(Button.builder(Component.literal("Regarder"), b -> sendCommand("duel regarder"))
				.bounds(left + 140, buttonsY, 120, 20).build());
		watch.active = !running.isEmpty();
		addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
				.bounds(left + WIDTH - 128, buttonsY, 120, 20).build());
		if (arenas.size() > 1) {
			int arrowsY = top + COLUMN_TOP + ARENA_IMAGE_H + 16;
			addRenderableWidget(Button.builder(Component.literal("◀"), b -> arena = (arena + arenas.size() - 1) % arenas.size())
					.bounds(left + ARENA_X, arrowsY, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal("▶"), b -> arena = (arena + 1) % arenas.size())
					.bounds(left + ARENA_X + ARENA_W - 20, arrowsY, 20, 20).build());
		}
		updateButtons();
	}

	private void updateButtons() {
		challenge.active = opponent != null && !arenas.isEmpty();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(font, "Duel", left + WIDTH / 2, top + 7, GOLD);
		header(g, "Adversaire", PLAYERS_X);
		header(g, "Arène", ARENA_X);
		header(g, "Mode", MODES_X);
		renderPlayers(g, mouseX, mouseY);
		renderArena(g);
		renderModes(g, mouseX, mouseY);
		if (!running.isEmpty()) {
			g.drawCenteredString(font, "En cours : " + running, left + WIDTH / 2, top + HEIGHT - 38, MUTED);
		}
		// Contenu du kit en dernier : l'infobulle passe au-dessus de tout.
		renderKitTooltip(g, mouseX, mouseY);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		g.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
		g.renderOutline(left, top, WIDTH, HEIGHT, BORDER);
	}

	private void header(GuiGraphics g, String text, int x) {
		g.drawString(font, text, left + x, top + COLUMN_TOP - 12, GOLD);
	}

	private void renderPlayers(GuiGraphics g, int mouseX, int mouseY) {
		int x = left + PLAYERS_X;
		if (opponents.isEmpty()) {
			g.drawString(font, "Personne d'autre", x, top + COLUMN_TOP + 6, MUTED);
			g.drawString(font, "n'est connecté.", x, top + COLUMN_TOP + 16, MUTED);
			return;
		}
		for (int i = 0; i < Math.min(opponents.size(), MAX_PLAYER_ROWS); i++) {
			Opponent row = opponents.get(i);
			int y = top + COLUMN_TOP + i * ROW;
			if (row.equals(opponent)) {
				g.fill(x, y, x + PLAYERS_W, y + ROW - 2, SELECTED);
			} else if (!row.busy() && inside(mouseX, mouseY, x, y, PLAYERS_W, ROW - 2)) {
				g.fill(x, y, x + PLAYERS_W, y + ROW - 2, HOVER);
			}
			PlayerFaceRenderer.draw(g, skin(row), x + 2, y + 1, 16);
			String name = font.plainSubstrByWidth(row.name(), PLAYERS_W - 24);
			g.drawString(font, name, x + 22, y + 5, row.busy() ? MUTED : TEXT);
			if (row.busy()) {
				g.fill(x + 2, y + 1, x + 18, y + 17, 0x90000000);
			}
		}
	}

	private PlayerSkin skin(Opponent row) {
		PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(row.id());
		return info != null ? info.getSkin() : DefaultPlayerSkin.get(row.id());
	}

	private void renderArena(GuiGraphics g) {
		if (arenas.isEmpty()) {
			return;
		}
		ArenaEntry entry = arenas.get(arena);
		int x = left + ARENA_X;
		int y = top + COLUMN_TOP;
		g.fill(x, y, x + ARENA_W, y + ARENA_IMAGE_H, FRAME);
		Previews.Preview preview = Previews.get(entry.id());
		if (preview != null) {
			// Image recadrée au centre pour remplir le cadre sans déformation.
			float scale = Math.max((float) (ARENA_W - 2) / preview.width(), (float) (ARENA_IMAGE_H - 2) / preview.height());
			int regionW = Math.round((ARENA_W - 2) / scale);
			int regionH = Math.round((ARENA_IMAGE_H - 2) / scale);
			g.blit(preview.texture(), x + 1, y + 1, ARENA_W - 2, ARENA_IMAGE_H - 2,
					(preview.width() - regionW) / 2f, (preview.height() - regionH) / 2f,
					regionW, regionH, preview.width(), preview.height());
		} else {
			g.drawCenteredString(font, "Pas d'aperçu", x + ARENA_W / 2, y + ARENA_IMAGE_H / 2 - 4, MUTED);
		}
		g.renderOutline(x, y, ARENA_W, ARENA_IMAGE_H, BORDER);
		g.drawCenteredString(font, entry.name(), x + ARENA_W / 2, y + ARENA_IMAGE_H + 4, TEXT);
		if (arenas.size() > 1) {
			g.drawCenteredString(font, (arena + 1) + " / " + arenas.size(), x + ARENA_W / 2, y + ARENA_IMAGE_H + 22, MUTED);
		}
	}

	private void renderModes(GuiGraphics g, int mouseX, int mouseY) {
		int x = left + MODES_X;
		for (int i = -1; i < kits.size(); i++) {
			int y = modeY(i);
			if (i == mode) {
				g.fill(x, y, x + MODES_W, y + ROW - 2, SELECTED);
			} else if (inside(mouseX, mouseY, x, y, MODES_W, ROW - 2)) {
				g.fill(x, y, x + MODES_W, y + ROW - 2, HOVER);
			}
			g.renderItem(i < 0 ? ownGearIcon : kits.get(i).icon(), x + 1, y + 1);
			g.drawString(font, i < 0 ? "Mon équipement" : "Kit " + kits.get(i).name(), x + 21, y + 5, TEXT);
		}
		int gridY = modeY(kits.size()) + 4;
		if (mode < 0) {
			g.drawString(font, "Armure réparée,", x, gridY, MUTED);
			g.drawString(font, "tout rendu à la fin.", x, gridY + 10, MUTED);
			return;
		}
		List<ItemStack> items = kits.get(mode).items();
		for (int i = 0; i < items.size(); i++) {
			int cx = x + (i % KIT_COLUMNS) * KIT_CELL;
			int cy = gridY + (i / KIT_COLUMNS) * KIT_CELL;
			g.fill(cx, cy, cx + KIT_CELL - 1, cy + KIT_CELL - 1, FRAME);
			g.renderItem(items.get(i), cx, cy);
			g.renderItemDecorations(font, items.get(i), cx, cy);
		}
	}

	private void renderKitTooltip(GuiGraphics g, int mouseX, int mouseY) {
		if (mode < 0) {
			return;
		}
		List<ItemStack> items = kits.get(mode).items();
		int gridY = modeY(kits.size()) + 4;
		for (int i = 0; i < items.size(); i++) {
			int cx = left + MODES_X + (i % KIT_COLUMNS) * KIT_CELL;
			int cy = gridY + (i / KIT_COLUMNS) * KIT_CELL;
			if (inside(mouseX, mouseY, cx, cy, KIT_CELL - 1, KIT_CELL - 1)) {
				g.renderTooltip(font, items.get(i), mouseX, mouseY);
			}
		}
	}

	private int modeY(int index) {
		return top + COLUMN_TOP + (index + 1) * ROW;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (int i = 0; i < Math.min(opponents.size(), MAX_PLAYER_ROWS); i++) {
				Opponent row = opponents.get(i);
				if (!row.busy() && inside(mouseX, mouseY, left + PLAYERS_X, top + COLUMN_TOP + i * ROW, PLAYERS_W, ROW - 2)) {
					opponent = row;
					updateButtons();
					return true;
				}
			}
			for (int i = -1; i < kits.size(); i++) {
				if (inside(mouseX, mouseY, left + MODES_X, modeY(i), MODES_W, ROW - 2)) {
					mode = i;
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void sendChallenge() {
		if (opponent == null || arenas.isEmpty()) {
			return;
		}
		String modeArg = mode < 0 ? DuelConfig.OWN_GEAR : kits.get(mode).id();
		sendCommand("duel " + opponent.name() + " " + arenas.get(arena).id() + " " + modeArg);
	}

	private void sendCommand(String command) {
		if (minecraft.getConnection() != null) {
			minecraft.getConnection().sendCommand(command);
		}
		onClose();
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
