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
		int y = startY(10);
		y = header(y, false);

		btn(2, 0, y, "Automaten →", () -> open(new SlotsScreen()));
		btn(2, 1, y, "Würfel & Münzen →", () -> open(new DiceScreen()));
		y += ROW;

		btn(2, 0, y, "Karten →", () -> open(new CardsScreen()));
		btn(2, 1, y, "Risiko →", () -> open(new RiskScreen()));
		y += ROW;

		btn(2, 0, y, "Sonstiges →", () -> open(new MiscScreen()));
		btn(2, 1, y, "Rennen & Lotto →", () -> open(new RaceScreen()));
		y += ROW;

		btn(2, 0, y, "Blackjack →", () -> open(new BlackjackScreen()));
		btn(2, 1, y, "Video-Poker →", () -> open(new PokerScreen()));
		y += ROW;

		btn(2, 0, y, "Mines →", () -> open(new MinesScreen()));
		btn(2, 1, y, "Memory (Texturpack) →", () -> open(new MemoryScreen()));
		y += ROW;

		btn(2, 0, y, "Shop →", () -> open(new ShopScreen()));
		btn(2, 1, y, "Tägliche Belohnung", FunGambleClient::uiDaily);
		y += ROW;

		wide(y, "Schließen", this::close);

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

	protected int colWidth(int n) {
		return (W - (n - 1) * 4) / n;
	}

	protected int colX(int n, int i) {
		return left() + i * (colWidth(n) + 4);
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

	protected ButtonWidget buttonH(int x, int y, int w, int h, String text, Runnable action) {
		ButtonWidget b = ButtonWidget.builder(Text.literal(text), btn -> action.run()).dimensions(x, y, w, h).build();
		return this.addDrawableChild(b);
	}

	/** Button in einer Reihe mit n Spalten (i = 0 ... n-1). */
	protected ButtonWidget btn(int n, int i, int y, String text, Runnable action) {
		return button(colX(n, i), y, colWidth(n), text, action);
	}

	/** Ein breiter Button ueber die ganze Breite. */
	protected ButtonWidget wide(int y, String text, Runnable action) {
		return button(left(), y, W, text, action);
	}

	/** Eine graue Beschriftungszeile. */
	protected ButtonWidget caption(int y, String text) {
		ButtonWidget b = label(left(), y, W);
		b.setMessage(Text.literal(text).formatted(Formatting.GRAY));
		return b;
	}

	/** Baut Kontostand, zwei Statuszeilen und (optional) den Einsatz-Button. Liefert die naechste y-Position. */
	protected int header(int y, boolean withBet) {
		balanceLabel = label(left(), y, W);
		y += ROW;
		status1 = label(left(), y, W);
		y += ROW;
		status2 = label(left(), y, W);
		y += ROW;
		if (withBet) {
			betButton = button(left(), y, W, "", () -> betIndex = (betIndex + 1) % BETS.length);
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

/** Automaten: Slots, Gluecksrad, Plinko, Rubbellos, Roulette. */
class SlotsScreen extends CasinoScreen {

	SlotsScreen() {
		super("Automaten");
	}

	@Override
	protected void init() {
		int y = startY(8);
		y = header(y, true);

		btn(2, 0, y, "Slots", () -> FunGambleClient.uiSlots(bet()));
		btn(2, 1, y, "Glücksrad", () -> FunGambleClient.uiWheel(bet()));
		y += ROW;

		btn(2, 0, y, "Plinko", () -> FunGambleClient.uiPlinko(bet()));
		btn(2, 1, y, "Rubbellos", () -> FunGambleClient.uiScratch(bet()));
		y += ROW;

		btn(3, 0, y, "Roulette: Rot", () -> FunGambleClient.uiRoulette(bet(), "rot"));
		btn(3, 1, y, "Schwarz", () -> FunGambleClient.uiRoulette(bet(), "schwarz"));
		btn(3, 2, y, "Grün (x14)", () -> FunGambleClient.uiRoulette(bet(), "gruen"));
		y += ROW;

		wide(y, "Zurück", () -> open(new GambleScreen()));
		refresh();
	}
}

/** Wuerfel und Muenzen. */
class DiceScreen extends CasinoScreen {

	private ButtonWidget thresholdButton;
	private ButtonWidget overButton;
	private ButtonWidget underButton;

	DiceScreen() {
		super("Würfel & Münzen");
	}

	@Override
	protected void init() {
		int y = startY(10);
		y = header(y, true);

		btn(2, 0, y, "Würfeln vs. Dealer", () -> FunGambleClient.uiDice(bet()));
		btn(2, 1, y, "Craps", () -> FunGambleClient.uiCraps(bet()));
		y += ROW;

		btn(2, 0, y, "Sic Bo: Groß (11-17)", () -> FunGambleClient.uiSicBo(bet(), true));
		btn(2, 1, y, "Sic Bo: Klein (4-10)", () -> FunGambleClient.uiSicBo(bet(), false));
		y += ROW;

		thresholdButton = wide(y, "", FunGambleClient::uiCycleDice100);
		y += ROW;

		overButton = btn(2, 0, y, "", () -> FunGambleClient.uiDice100(bet(), true));
		underButton = btn(2, 1, y, "", () -> FunGambleClient.uiDice100(bet(), false));
		y += ROW;

		btn(2, 0, y, "Münze: Kopf", () -> FunGambleClient.uiCoin(bet(), true));
		btn(2, 1, y, "Münze: Zahl", () -> FunGambleClient.uiCoin(bet(), false));
		y += ROW;

		wide(y, "Zurück", () -> open(new GambleScreen()));
		refresh();
	}

	@Override
	protected void refresh() {
		super.refresh();
		if (thresholdButton == null) {
			return;
		}
		thresholdButton.setMessage(FunGambleClient.dice100ThresholdLabel());
		overButton.setMessage(FunGambleClient.dice100Label(true));
		underButton.setMessage(FunGambleClient.dice100Label(false));
	}
}

/** Kartenspiele. */
class CardsScreen extends CasinoScreen {

	CardsScreen() {
		super("Karten");
	}

	@Override
	protected void init() {
		int y = startY(10);
		y = header(y, true);

		btn(2, 0, y, "Blackjack →", () -> open(new BlackjackScreen()));
		btn(2, 1, y, "Video-Poker →", () -> open(new PokerScreen()));
		y += ROW;

		btn(3, 0, y, "Baccarat: Spieler", () -> FunGambleClient.uiBaccarat(bet(), 0));
		btn(3, 1, y, "Bank", () -> FunGambleClient.uiBaccarat(bet(), 1));
		btn(3, 2, y, "Tie (x9)", () -> FunGambleClient.uiBaccarat(bet(), 2));
		y += ROW;

		btn(2, 0, y, "Hoch", () -> FunGambleClient.uiHighLow(bet(), true));
		btn(2, 1, y, "Tief", () -> FunGambleClient.uiHighLow(bet(), false));
		y += ROW;

		caption(y, "Kartenleiter: richtig raten und weiter, oder auszahlen");
		y += ROW;

		btn(4, 0, y, "Start", () -> FunGambleClient.uiLadderStart(bet()));
		btn(4, 1, y, "Hoch", () -> FunGambleClient.uiLadderGuess(true));
		btn(4, 2, y, "Tief", () -> FunGambleClient.uiLadderGuess(false));
		btn(4, 3, y, "Cashout", FunGambleClient::uiLadderCash);
		y += ROW;

		wide(y, "Zurück", () -> open(new GambleScreen()));
		refresh();
	}
}

/** Risiko-Spiele mit Cashout. */
class RiskScreen extends CasinoScreen {

	RiskScreen() {
		super("Risiko");
	}

	@Override
	protected void init() {
		int y = startY(10);
		y = header(y, true);

		btn(2, 0, y, "Crash starten", () -> FunGambleClient.uiCrash(bet()));
		btn(2, 1, y, "Crash: Cashout!", FunGambleClient::uiCashout);
		y += ROW;

		btn(2, 0, y, "Tower starten", () -> FunGambleClient.uiTower(bet()));
		btn(2, 1, y, "Tower: Cashout", FunGambleClient::uiTowerCash);
		y += ROW;

		btn(3, 0, y, "Tür 1", () -> FunGambleClient.uiDoor(1));
		btn(3, 1, y, "Tür 2", () -> FunGambleClient.uiDoor(2));
		btn(3, 2, y, "Tür 3", () -> FunGambleClient.uiDoor(3));
		y += ROW;

		btn(3, 0, y, "Serie: Start", () -> FunGambleClient.uiStreakStart(bet()));
		btn(3, 1, y, "Werfen", FunGambleClient::uiStreakFlip);
		btn(3, 2, y, "Cashout", FunGambleClient::uiStreakCash);
		y += ROW;

		btn(2, 0, y, "Reaktionstest", () -> FunGambleClient.uiReflex(bet()));
		btn(2, 1, y, "JETZT!", FunGambleClient::uiGo);
		y += ROW;

		btn(2, 0, y, "Mines →", () -> open(new MinesScreen()));
		btn(2, 1, y, "Zurück", () -> open(new GambleScreen()));
		refresh();
	}
}

/** Sonstiges: Schere-Stein-Papier, Huetchenspiel, Schatzkisten. */
class MiscScreen extends CasinoScreen {

	MiscScreen() {
		super("Sonstiges");
	}

	@Override
	protected void init() {
		int y = startY(10);
		y = header(y, true);

		btn(3, 0, y, "Stein", () -> FunGambleClient.uiRps(bet(), 0));
		btn(3, 1, y, "Schere", () -> FunGambleClient.uiRps(bet(), 1));
		btn(3, 2, y, "Papier", () -> FunGambleClient.uiRps(bet(), 2));
		y += ROW;

		caption(y, "Hütchenspiel: Unter welchem Becher ist der Ball? (x2,85)");
		y += ROW;
		for (int i = 0; i < 3; i++) {
			final int pick = i;
			btn(3, i, y, "Becher " + (i + 1), () -> FunGambleClient.uiCups(bet(), pick));
		}
		y += ROW;

		caption(y, "Schatzkisten: Wähle eine Kiste (x0 bis x2,4)");
		y += ROW;
		for (int i = 0; i < 4; i++) {
			final int pick = i;
			btn(4, i, y, "Kiste " + (i + 1), () -> FunGambleClient.uiChests(bet(), pick));
		}
		y += ROW;

		btn(2, 0, y, "Rennen & Lotto →", () -> open(new RaceScreen()));
		btn(2, 1, y, "Zurück", () -> open(new GambleScreen()));
		refresh();
	}
}

/** Pferderennen und Lotto. */
class RaceScreen extends CasinoScreen {

	RaceScreen() {
		super("Rennen & Lotto");
	}

	@Override
	protected void init() {
		int y = startY(11);
		y = header(y, true);

		caption(y, "Pferderennen: Setze auf dein Pferd (Quote steht dabei)");
		y += ROW;
		for (int i = 0; i < 3; i++) {
			final int horse = i;
			btn(3, i, y, FunGambleClient.raceLabel(i), () -> FunGambleClient.uiRace(bet(), horse));
		}
		y += ROW;
		for (int i = 3; i < 5; i++) {
			final int horse = i;
			btn(2, i - 3, y, FunGambleClient.raceLabel(i), () -> FunGambleClient.uiRace(bet(), horse));
		}
		y += ROW;

		caption(y, "Lotto: Rate die Zahl von 1 bis 10 (x9)");
		y += ROW;
		for (int i = 1; i <= 5; i++) {
			final int n = i;
			btn(5, i - 1, y, String.valueOf(i), () -> FunGambleClient.uiLotto(bet(), n));
		}
		y += ROW;
		for (int i = 6; i <= 10; i++) {
			final int n = i;
			btn(5, i - 6, y, String.valueOf(i), () -> FunGambleClient.uiLotto(bet(), n));
		}
		y += ROW;

		wide(y, "Zurück", () -> open(new GambleScreen()));
		refresh();
	}
}

/** Eigenes Blackjack-Fenster mit sichtbaren Karten. */
class BlackjackScreen extends CasinoScreen {

	private ButtonWidget dealerLabel;
	private ButtonWidget playerLabel;

	BlackjackScreen() {
		super("Blackjack");
	}

	@Override
	protected void init() {
		int y = startY(9);
		y = header(y, true);

		dealerLabel = label(left(), y, W);
		y += ROW;
		playerLabel = label(left(), y, W);
		y += ROW;

		btn(2, 0, y, "Neue Runde", () -> FunGambleClient.uiBlackjack(bet()));
		btn(2, 1, y, "Verdoppeln (Double)", FunGambleClient::uiBjDouble);
		y += ROW;

		btn(2, 0, y, "Karte ziehen (Hit)", FunGambleClient::uiHit);
		btn(2, 1, y, "Bleiben (Stand)", FunGambleClient::uiStand);
		y += ROW;

		btn(2, 0, y, "Karten →", () -> open(new CardsScreen()));
		btn(2, 1, y, "Hauptmenü", () -> open(new GambleScreen()));
		refresh();
	}

	@Override
	protected void refresh() {
		super.refresh();
		if (dealerLabel == null) {
			return;
		}
		dealerLabel.setMessage(FunGambleClient.bjDealerText());
		playerLabel.setMessage(FunGambleClient.bjPlayerText());
	}
}

/** Eigenes Video-Poker-Fenster: Karten anklicken zum Halten. */
class PokerScreen extends CasinoScreen {

	private final ButtonWidget[] cards = new ButtonWidget[5];

	PokerScreen() {
		super("Video-Poker");
	}

	@Override
	protected void init() {
		int y = startY(11);
		y = header(y, true);

		for (int i = 0; i < 5; i++) {
			final int index = i;
			cards[i] = wide(y, "Karte " + (i + 1), () -> FunGambleClient.uiPokerHold(index));
			y += ROW;
		}

		btn(2, 0, y, "Austeilen", () -> FunGambleClient.uiPokerDeal(bet()));
		btn(2, 1, y, "Tauschen", FunGambleClient::uiPokerDraw);
		y += ROW;

		btn(2, 0, y, "Karten →", () -> open(new CardsScreen()));
		btn(2, 1, y, "Hauptmenü", () -> open(new GambleScreen()));
		refresh();
	}

	@Override
	protected void refresh() {
		super.refresh();
		for (int i = 0; i < cards.length; i++) {
			if (cards[i] != null) {
				cards[i].setMessage(FunGambleClient.pokerCardLabel(i));
			}
		}
	}
}

/** Memory: 12 Karten, 6 Paare. Mit dem Texturpack mit schoenen Bildern. */
class MemoryScreen extends CasinoScreen {

	private final ButtonWidget[] cells = new ButtonWidget[12];
	private ButtonWidget packLabel;

	MemoryScreen() {
		super("Memory");
	}

	@Override
	protected void init() {
		final int cellH = 32;
		final int gap = 2;
		int total = 6 * ROW + 3 * (cellH + gap) + ROW;
		int y = Math.max(4, (this.height - total) / 2);
		y = header(y, true);

		btn(2, 0, y, "Neue Runde", () -> FunGambleClient.uiMemoryStart(bet()));
		btn(2, 1, y, "Zurück", () -> open(new GambleScreen()));
		y += ROW;

		packLabel = label(left(), y, W);
		y += ROW;

		final int cols = 4;
		final int cellW = colWidth(cols);
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < cols; c++) {
				final int index = r * cols + c;
				cells[index] = buttonH(colX(cols, c), y, cellW, cellH, "?", () -> FunGambleClient.uiMemoryFlip(index));
			}
			y += cellH + gap;
		}

		refresh();
	}

	@Override
	protected void refresh() {
		super.refresh();
		if (packLabel == null) {
			return;
		}
		packLabel.setMessage(FunGambleClient.packStatusText());
		for (int i = 0; i < cells.length; i++) {
			if (cells[i] != null) {
				cells[i].setMessage(FunGambleClient.memoryCellLabel(i));
			}
		}
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
		int y = startY(11);
		y = header(y, true);

		btn(2, 0, y, "Mines starten", () -> FunGambleClient.uiMinesStart(bet()));
		cashButton = btn(2, 1, y, "Cashout", FunGambleClient::uiMinesCash);
		y += ROW;

		final int cell = 48;
		final int gap = 4;
		final int gridX = left() + (W - (5 * cell + 4 * gap)) / 2;
		for (int r = 0; r < 5; r++) {
			for (int c = 0; c < 5; c++) {
				final int index = r * 5 + c;
				cells[index] = button(gridX + c * (cell + gap), y, cell, "?", () -> FunGambleClient.uiMinesReveal(index));
			}
			y += ROW;
		}

		wide(y, "Zurück zu Risiko", () -> open(new RiskScreen()));
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

/** Shop-Hauptmenue mit Kategorien (Extras und Kosmetik). */
class ShopScreen extends CasinoScreen {

	ShopScreen() {
		super("Spielgeld-Shop");
	}

	@Override
	protected void init() {
		int y = startY(8);
		y = header(y, false);

		caption(y, "Extras und Kosmetik – nur für dich sichtbar");
		y += ROW;

		btn(2, 0, y, "Titel", () -> open(new ShopListScreen("title", "Titel")));
		btn(2, 1, y, "Gewinn-Sounds", () -> open(new ShopListScreen("sound", "Gewinn-Sounds")));
		y += ROW;

		btn(2, 0, y, "Farben", () -> open(new ShopListScreen("color", "Farben")));
		btn(2, 1, y, "Rahmen", () -> open(new ShopListScreen("frame", "Rahmen")));
		y += ROW;

		btn(2, 0, y, "Gewinn-Banner", () -> open(new ShopListScreen("banner", "Gewinn-Banner")));
		btn(2, 1, y, "Kartenrücken", () -> open(new ShopListScreen("back", "Kartenrücken")));
		y += ROW;

		btn(2, 0, y, "Rettungsring", () -> open(new ShopListScreen("ring", "Rettungsring")));
		btn(2, 1, y, "Zurück", () -> open(new GambleScreen()));
		refresh();
	}
}

/** Eine Kategorie des Shops als Liste. */
class ShopListScreen extends CasinoScreen {

	private final String type;
	private final List<FunGambleClient.Item> items = new ArrayList<>();
	private final List<ButtonWidget> itemButtons = new ArrayList<>();

	ShopListScreen(String type, String title) {
		super(title);
		this.type = type;
	}

	@Override
	protected void init() {
		items.clear();
		itemButtons.clear();
		items.addAll(FunGambleClient.itemsOfType(type));

		int y = startY(items.size() + 5);
		y = header(y, false);

		for (FunGambleClient.Item item : items) {
			itemButtons.add(wide(y, item.name(), () -> FunGambleClient.shopClick(item)));
			y += ROW;
		}

		if (!type.equals("ring")) {
			wide(y, "Ablegen (Standard)", () -> FunGambleClient.uiUnequip(type));
			y += ROW;
		}
		wide(y, "Zurück zum Shop", () -> open(new ShopScreen()));
		refresh();
	}

	@Override
	protected void refresh() {
		super.refresh();
		for (int i = 0; i < itemButtons.size(); i++) {
			itemButtons.get(i).setMessage(FunGambleClient.shopLabel(items.get(i)));
		}
	}
}
