package de.fungamble;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Das Casino-Hauptmenue. Oeffnen mit /gamble oder /gamble menu. */
public class GambleScreen extends CasinoScreen {

	public GambleScreen() {
		super("Fun Gamble Casino");
	}

	@Override
	protected void init() {
		final int x = left();
		final int half = (W - 4) / 2;
		final int x2 = x + half + 4;
		int y = startY(14);

		y = header(y, true);

		button(x, y, half, "Slots", () -> FunGambleClient.uiSlots(bet()));
		button(x2, y, half, "Würfeln", () -> FunGambleClient.uiDice(bet()));
		y += ROW;

		button(x, y, half, "Münze: Kopf", () -> FunGambleClient.uiCoin(bet(), true));
		button(x2, y, half, "Münze: Zahl", () -> FunGambleClient.uiCoin(bet(), false));
		y += ROW;

		button(x, y, half, "Roulette: Rot", () -> FunGambleClient.uiRoulette(bet(), "rot"));
		button(x2, y, half, "Roulette: Schwarz", () -> FunGambleClient.uiRoulette(bet(), "schwarz"));
		y += ROW;

		button(x, y, half, "Roulette: Grün", () -> FunGambleClient.uiRoulette(bet(), "gruen"));
		button(x2, y, half, "Glücksrad", () -> FunGambleClient.uiWheel(bet()));
		y += ROW;

		button(x, y, half, "Hoch", () -> FunGambleClient.uiHighLow(bet(), true));
		button(x2, y, half, "Tief", () -> FunGambleClient.uiHighLow(bet(), false));
		y += ROW;

		button(x, y, half, "Crash starten", () -> FunGambleClient.uiCrash(bet()));
		button(x2, y, half, "Cashout!", FunGambleClient::uiCashout);
		y += ROW;

		button(x, y, half, "Blackjack starten", () -> FunGambleClient.uiBlackjack(bet()));
		button(x2, y, half, "Karte ziehen (Hit)", FunGambleClient::uiHit);
		y += ROW;

		button(x, y, half, "Bleiben (Stand)", FunGambleClient::uiStand);
		button(x2, y, half, "Mini-Games →", () -> open(new MiniScreen()));
		y += ROW;

		button(x, y, half, "Shop", () -> open(new ShopScreen()));
		button(x2, y, half, "Schließen", this::close);

		refresh();
	}
}

/** Gemeinsame Basis: Kontostand, Statuszeilen, Einsatz-Button. */
abstract class CasinoScreen extends Screen {

	static final long[] BETS = {10, 50, 100, 500, 1000, 5000, -1}; // -1 = All-in
	static int betIndex = 2;
	static final int W = 260;
	static final int ROW = 20;

	protected ButtonWidget balanceLabel;
	protected ButtonWidget status1;
	protected ButtonWidget status2;
	protected ButtonWidget betButton;

	protected CasinoScreen(String title) {
		super(Text.literal(title));
	}

	static long bet() {
		long b = BETS[betIndex];
		return b < 0 ? Math.max(1, FunGambleClient.getBalance()) : b;
	}

	static Text betText() {
		String amount = BETS[betIndex] < 0 ? "ALL-IN (" + bet() + ")" : String.valueOf(BETS[betIndex]);
		return Text.literal("Einsatz: " + amount + "  (klicken zum Ändern)").formatted(Formatting.AQUA);
	}

	static void open(Screen screen) {
		MinecraftClient.getInstance().setScreen(screen);
	}

	protected int left() {
		return (this.width - W) / 2;
	}

	protected int startY(int rows) {
		return Math.max(4, (this.height - rows * ROW) / 2);
	}

	protected ButtonWidget label(int x, int y, int w) {
		ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> {}).dimensions(x, y, w, 18).build();
		b.active = false;
		return this.addDrawableChild(b);
	}

	protected ButtonWidget button(int x, int y, int w, String text, Runnable action) {
		ButtonWidget b = ButtonWidget.builder(Text.literal(text), btn -> action.run()).dimensions(x, y, w, 18).build();
		return this.addDrawableChild(b);
	}

	/** Baut Kontostand, zwei Statuszeilen und (optional) den Einsatz-Button. Liefert die naechste y-Position. */
	protected int header(int y, boolean withBet) {
		final int x = left();
		balanceLabel = label(x, y, W);
		y += ROW;
		status1 = label(x, y, W);
		y += ROW;
		status2 = label(x, y, W);
		y += ROW;
		if (withBet) {
			betButton = button(x, y, W, "", () -> betIndex = (betIndex + 1) % BETS.length);
			y += ROW;
		} else {
			betButton = null;
		}
		return y;
	}

	protected void refresh() {
		if (balanceLabel == null) {
			return;
		}
		balanceLabel.setMessage(FunGambleClient.balanceLabel());
		status1.setMessage(FunGambleClient.statusLine1());
		status2.setMessage(FunGambleClient.statusLine2());
		if (betButton != null) {
			betButton.setMessage(betText());
		}
	}

	@Override
	public void tick() {
		refresh();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}

/** Zweite Seite: Mini-Games. */
class MiniScreen extends CasinoScreen {

	MiniScreen() {
		super("Mini-Games");
	}

	@Override
	protected void init() {
		final int x = left();
		final int half = (W - 4) / 2;
		final int x2 = x + half + 4;
		final int third = (W - 8) / 3;
		final int x3 = x + third + 4;
		final int x4 = x + 2 * (third + 4);
		int y = startY(12);

		y = header(y, true);

		button(x, y, half, "Plinko", () -> FunGambleClient.uiPlinko(bet()));
		button(x2, y, half, "Rubbellos", () -> FunGambleClient.uiScratch(bet()));
		y += ROW;

		button(x, y, third, "Stein", () -> FunGambleClient.uiRps(bet(), 0));
		button(x3, y, third, "Schere", () -> FunGambleClient.uiRps(bet(), 1));
		button(x4, y, third, "Papier", () -> FunGambleClient.uiRps(bet(), 2));
		y += ROW;

		button(x, y, half, "Reaktionstest starten", () -> FunGambleClient.uiReflex(bet()));
		button(x2, y, half, "JETZT!", FunGambleClient::uiGo);
		y += ROW;

		button(x, y, half, "Tower starten", () -> FunGambleClient.uiTower(bet()));
		button(x2, y, half, "Tower: Cashout", FunGambleClient::uiTowerCash);
		y += ROW;

		button(x, y, third, "Tür 1", () -> FunGambleClient.uiDoor(1));
		button(x3, y, third, "Tür 2", () -> FunGambleClient.uiDoor(2));
		button(x4, y, third, "Tür 3", () -> FunGambleClient.uiDoor(3));
		y += ROW;

		button(x, y, half, "Mines →", () -> open(new MinesScreen()));
		button(x2, y, half, "Tägliche Belohnung", FunGambleClient::uiDaily);
		y += ROW;

		button(x, y, W, "Zurück zum Casino", () -> open(new GambleScreen()));

		refresh();
	}
}

/** Mines: 5x5 Felder, 3 davon sind Minen. */
class MinesScreen extends CasinoScreen {

	private final ButtonWidget[] cells = new ButtonWidget[25];
	private ButtonWidget cashButton;

	MinesScreen() {
		super("Mines");
	}

	@Override
	protected void init() {
		final int x = left();
		final int half = (W - 4) / 2;
		final int x2 = x + half + 4;
		int y = startY(11);

		y = header(y, true);

		button(x, y, half, "Mines starten", () -> FunGambleClient.uiMinesStart(bet()));
		cashButton = button(x2, y, half, "Cashout", FunGambleClient::uiMinesCash);
		y += ROW;

		final int cell = 48;
		final int gap = 4;
		final int gridX = x + (W - (5 * cell + 4 * gap)) / 2;
		for (int r = 0; r < 5; r++) {
			for (int c = 0; c < 5; c++) {
				final int index = r * 5 + c;
				cells[index] = button(gridX + c * (cell + gap), y, cell, "?", () -> FunGambleClient.uiMinesReveal(index));
			}
			y += ROW;
		}

		button(x, y, W, "Zurück zu den Mini-Games", () -> open(new MiniScreen()));

		refresh();
	}

	@Override
	protected void refresh() {
		super.refresh();
		if (cashButton == null) {
			return;
		}
		cashButton.setMessage(FunGambleClient.minesCashLabel());
		for (int i = 0; i < cells.length; i++) {
			if (cells[i] != null) {
				cells[i].setMessage(FunGambleClient.minesCellLabel(i));
			}
		}
	}
}

/** Der Spielgeld-Shop als Fenster. */
class ShopScreen extends CasinoScreen {

	private final List<ButtonWidget> itemButtons = new ArrayList<>();

	ShopScreen() {
		super("Spielgeld-Shop");
	}

	@Override
	protected void init() {
		itemButtons.clear();
		final int x = left();
		int y = startY(FunGambleClient.ITEMS.size() + 4);

		y = header(y, false);

		for (FunGambleClient.Item item : FunGambleClient.ITEMS) {
			ButtonWidget b = button(x, y, W, item.name(), () -> FunGambleClient.shopClick(item));
			itemButtons.add(b);
			y += ROW;
		}

		button(x, y, W, "Zurück zum Casino", () -> open(new GambleScreen()));

		refresh();
	}

	@Override
	protected void refresh() {
		super.refresh();
		for (int i = 0; i < itemButtons.size(); i++) {
			itemButtons.get(i).setMessage(FunGambleClient.shopLabel(FunGambleClient.ITEMS.get(i)));
		}
	}
}
