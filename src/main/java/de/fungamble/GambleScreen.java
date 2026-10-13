package de.fungamble;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Das Casino-Menue. Oeffnen mit /gamble oder /gamble menu. */
public class GambleScreen extends Screen {

	private static final long[] BETS = {10, 50, 100, 500, 1000, 5000, -1}; // -1 = All-in
	private static int betIndex = 2;

	private ButtonWidget balanceLabel;
	private ButtonWidget status1;
	private ButtonWidget status2;
	private ButtonWidget betButton;

	public GambleScreen() {
		super(Text.literal("Fun Gamble Casino"));
	}

	private static long bet() {
		long b = BETS[betIndex];
		return b < 0 ? Math.max(1, FunGambleClient.getBalance()) : b;
	}

	private static Text betText() {
		String amount = BETS[betIndex] < 0 ? "ALL-IN (" + bet() + ")" : String.valueOf(BETS[betIndex]);
		return Text.literal("Einsatz: " + amount + "  (klicken zum Ändern)").formatted(Formatting.AQUA);
	}

	private ButtonWidget label(int x, int y, int w) {
		ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> {}).dimensions(x, y, w, 18).build();
		b.active = false;
		return this.addDrawableChild(b);
	}

	private ButtonWidget button(int x, int y, int w, String text, Runnable action) {
		ButtonWidget b = ButtonWidget.builder(Text.literal(text), btn -> action.run()).dimensions(x, y, w, 18).build();
		return this.addDrawableChild(b);
	}

	@Override
	protected void init() {
		final int w = 260;
		final int half = (w - 4) / 2;
		final int rowH = 20;
		final int x = (this.width - w) / 2;
		final int x2 = x + half + 4;
		int y = Math.max(4, (this.height - 13 * rowH) / 2);

		balanceLabel = label(x, y, w);
		y += rowH;
		status1 = label(x, y, w);
		y += rowH;
		status2 = label(x, y, w);
		y += rowH;

		betButton = button(x, y, w, "", () -> {
			betIndex = (betIndex + 1) % BETS.length;
		});
		y += rowH;

		button(x, y, half, "Slots", () -> FunGambleClient.uiSlots(bet()));
		button(x2, y, half, "Würfeln", () -> FunGambleClient.uiDice(bet()));
		y += rowH;

		button(x, y, half, "Münze: Kopf", () -> FunGambleClient.uiCoin(bet(), true));
		button(x2, y, half, "Münze: Zahl", () -> FunGambleClient.uiCoin(bet(), false));
		y += rowH;

		button(x, y, half, "Roulette: Rot", () -> FunGambleClient.uiRoulette(bet(), "rot"));
		button(x2, y, half, "Roulette: Schwarz", () -> FunGambleClient.uiRoulette(bet(), "schwarz"));
		y += rowH;

		button(x, y, half, "Roulette: Grün", () -> FunGambleClient.uiRoulette(bet(), "gruen"));
		button(x2, y, half, "Glücksrad", () -> FunGambleClient.uiWheel(bet()));
		y += rowH;

		button(x, y, half, "Hoch", () -> FunGambleClient.uiHighLow(bet(), true));
		button(x2, y, half, "Tief", () -> FunGambleClient.uiHighLow(bet(), false));
		y += rowH;

		button(x, y, half, "Crash starten", () -> FunGambleClient.uiCrash(bet()));
		button(x2, y, half, "Cashout!", FunGambleClient::uiCashout);
		y += rowH;

		button(x, y, half, "Blackjack starten", () -> FunGambleClient.uiBlackjack(bet()));
		button(x2, y, half, "Karte ziehen (Hit)", FunGambleClient::uiHit);
		y += rowH;

		button(x, y, half, "Bleiben (Stand)", FunGambleClient::uiStand);
		button(x2, y, half, "Shop", () -> MinecraftClient.getInstance().setScreen(new ShopScreen()));
		y += rowH;

		button(x, y, w, "Schließen", this::close);

		refresh();
	}

	private void refresh() {
		if (balanceLabel == null) {
			return;
		}
		balanceLabel.setMessage(FunGambleClient.balanceLabel());
		status1.setMessage(FunGambleClient.statusLine1());
		status2.setMessage(FunGambleClient.statusLine2());
		betButton.setMessage(betText());
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

/** Der Spielgeld-Shop als Fenster. */
class ShopScreen extends Screen {

	private final List<ButtonWidget> itemButtons = new ArrayList<>();
	private ButtonWidget balanceLabel;
	private ButtonWidget status;

	ShopScreen() {
		super(Text.literal("Spielgeld-Shop"));
	}

	@Override
	protected void init() {
		itemButtons.clear();
		final int w = 260;
		final int rowH = 20;
		final int x = (this.width - w) / 2;
		int rows = FunGambleClient.ITEMS.size() + 4;
		int y = Math.max(4, (this.height - rows * rowH) / 2);

		balanceLabel = ButtonWidget.builder(Text.empty(), btn -> {}).dimensions(x, y, w, 18).build();
		balanceLabel.active = false;
		this.addDrawableChild(balanceLabel);
		y += rowH;

		status = ButtonWidget.builder(Text.empty(), btn -> {}).dimensions(x, y, w, 18).build();
		status.active = false;
		this.addDrawableChild(status);
		y += rowH;

		for (FunGambleClient.Item item : FunGambleClient.ITEMS) {
			ButtonWidget b = ButtonWidget.builder(Text.literal(item.name()), btn -> FunGambleClient.shopClick(item))
				.dimensions(x, y, w, 18).build();
			this.addDrawableChild(b);
			itemButtons.add(b);
			y += rowH;
		}

		y += 4;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Zurück zum Casino"),
			btn -> MinecraftClient.getInstance().setScreen(new GambleScreen())).dimensions(x, y, w, 18).build());

		refresh();
	}

	private void refresh() {
		if (balanceLabel == null) {
			return;
		}
		balanceLabel.setMessage(FunGambleClient.balanceLabel());
		status.setMessage(FunGambleClient.statusLine1());
		for (int i = 0; i < itemButtons.size(); i++) {
			itemButtons.get(i).setMessage(FunGambleClient.shopLabel(FunGambleClient.ITEMS.get(i)));
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
