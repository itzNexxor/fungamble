package de.fungamble;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Reine Client-Mod: Alles passiert nur bei dir lokal.
 * Der Server (und andere Spieler) bekommen davon nichts mit.
 * Spielgeld ist nicht echt und hat nichts mit Items im Spiel zu tun.
 *
 * Version 1.4: alle Spiele + Spielgeld-Shop + Casino-Menue (/gamble oder /gamble menu).
 */
public class FunGambleClient implements ClientModInitializer {

	private static final long START_MONEY = 1000;
	private static final Random RNG = new Random();
	private static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
	private static final String[] SYMBOLS = {"Kirsche", "Zitrone", "Glocke", "Stern", "Diamant", "7"};
	private static final Formatting[] SYMBOL_COLORS = {
		Formatting.RED, Formatting.YELLOW, Formatting.GOLD, Formatting.AQUA, Formatting.BLUE, Formatting.DARK_RED
	};
	private static final double[] WHEEL = {0, 0, 0, 0, 0, 0.5, 0.5, 1, 1, 1.5, 2, 5};

	private static long balance = START_MONEY;
	private static Path saveFile;

	// Runde mit Animation (Slots, Muenzwurf, Wuerfeln, Roulette, Rad, Hoch/Tief)
	private static Game running = null;
	private static long runningBet = 0;
	private static int runningTick = 0;

	// Crash
	private static boolean crashActive = false;
	private static long crashBet = 0;
	private static double crashPoint = 1.0;
	private static int crashTicks = 0;

	// Blackjack
	private static boolean bjActive = false;
	private static long bjBet = 0;
	private static final List<Integer> bjPlayer = new ArrayList<>();
	private static final List<Integer> bjDealer = new ArrayList<>();

	// Shop
	record Item(String id, String name, String type, long price, String desc) {}

	static final List<Item> ITEMS = List.of(
		new Item("gluckspilz", "Glückspilz", "title", 500, "Titel"),
		new Item("highroller", "High Roller", "title", 2500, "Titel"),
		new Item("casinoboss", "Casino-Boss", "title", 10000, "Titel"),
		new Item("legende", "Legende", "title", 50000, "Titel"),
		new Item("feuerwerk", "Feuerwerk-Sound", "sound", 800, "Gewinn-Sound"),
		new Item("glocke", "Glocken-Sound", "sound", 800, "Gewinn-Sound"),
		new Item("donner", "Donner-Sound", "sound", 1500, "Gewinn-Sound"),
		new Item("rettungsring", "Rettungsring", "ring", 300, "Bei einer Niete bekommst du 50 % vom Einsatz zurück (einmalig)")
	);

	static final LinkedHashSet<String> owned = new LinkedHashSet<>();
	static String activeTitle = "";
	static String activeSound = "";
	static int rings = 0;

	// Fuer das Casino-Menue: zuletzt angezeigte Texte
	private static final java.util.ArrayDeque<Text> recent = new java.util.ArrayDeque<>();
	private static Text lastOverlay = Text.empty();

	/** Ergebnis steht schon fest, die Animation zeigt es nur nach und nach. */
	private record Game(double multiplier, Text result, int duration, IntConsumer tick) {}

	@Override
	public void onInitializeClient() {
		saveFile = FabricLoader.getInstance().getConfigDir().resolve("fungamble.txt");
		load();

		ClientTickEvents.END_CLIENT_TICK.register(client -> tickAll());

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("gamble")
				.executes(ctx -> openMenu())
				.then(ClientCommandManager.literal("menu").executes(ctx -> openMenu()))
				.then(ClientCommandManager.literal("help").executes(ctx -> help()))
				.then(ClientCommandManager.literal("balance").executes(ctx -> showBalance()))
				.then(ClientCommandManager.literal("reset").executes(ctx -> reset()))
				.then(ClientCommandManager.literal("coinflip").then(bet()
					.then(ClientCommandManager.literal("kopf").executes(ctx -> play(ctx, () -> coinflip(true))))
					.then(ClientCommandManager.literal("zahl").executes(ctx -> play(ctx, () -> coinflip(false))))))
				.then(ClientCommandManager.literal("slots").then(bet()
					.executes(ctx -> play(ctx, FunGambleClient::slots))))
				.then(ClientCommandManager.literal("dice").then(bet()
					.executes(ctx -> play(ctx, FunGambleClient::dice))))
				.then(ClientCommandManager.literal("roulette").then(bet()
					.then(ClientCommandManager.literal("rot").executes(ctx -> play(ctx, () -> roulette("rot"))))
					.then(ClientCommandManager.literal("schwarz").executes(ctx -> play(ctx, () -> roulette("schwarz"))))
					.then(ClientCommandManager.literal("gruen").executes(ctx -> play(ctx, () -> roulette("gruen"))))))
				.then(ClientCommandManager.literal("wheel").then(bet()
					.executes(ctx -> play(ctx, FunGambleClient::wheel))))
				.then(ClientCommandManager.literal("highlow").then(bet()
					.then(ClientCommandManager.literal("hoch").executes(ctx -> play(ctx, () -> highLow(true))))
					.then(ClientCommandManager.literal("tief").executes(ctx -> play(ctx, () -> highLow(false))))))
				.then(ClientCommandManager.literal("crash").then(bet()
					.executes(ctx -> startCrash(ctx))))
				.then(ClientCommandManager.literal("cashout").executes(ctx -> cashout()))
				.then(ClientCommandManager.literal("blackjack").then(bet()
					.executes(ctx -> startBlackjack(ctx))))
				.then(ClientCommandManager.literal("hit").executes(ctx -> bjHit()))
				.then(ClientCommandManager.literal("stand").executes(ctx -> bjStand()));

			// Shop
			root.then(ClientCommandManager.literal("shop").executes(ctx -> showShop()));
			LiteralArgumentBuilder<FabricClientCommandSource> buyNode = ClientCommandManager.literal("buy");
			LiteralArgumentBuilder<FabricClientCommandSource> equipNode = ClientCommandManager.literal("equip");
			for (Item item : ITEMS) {
				final String id = item.id();
				buyNode.then(ClientCommandManager.literal(id).executes(ctx -> buy(id)));
				if (!item.type().equals("ring")) {
					equipNode.then(ClientCommandManager.literal(id).executes(ctx -> equip(id)));
				}
			}
			root.then(buyNode);
			root.then(equipNode);
			root.then(ClientCommandManager.literal("unequip")
				.then(ClientCommandManager.literal("title").executes(ctx -> unequip("title")))
				.then(ClientCommandManager.literal("sound").executes(ctx -> unequip("sound"))));

			dispatcher.register(root);
		});
	}

	// Einsatz-Argument: /gamble <spiel> <einsatz> ...
	private static RequiredArgumentBuilder<FabricClientCommandSource, Long> bet() {
		return ClientCommandManager.argument("einsatz", LongArgumentType.longArg(1));
	}

	// ---------- Hilfsfunktionen ----------

	private static void chat(Text text) {
		recent.addLast(text);
		while (recent.size() > 3) {
			recent.removeFirst();
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			mc.player.sendMessage(text, false); // nur lokal sichtbar
		}
	}

	private static void chat(String text, Formatting color) {
		chat(Text.literal(text).formatted(color));
	}

	private static void overlay(Text text) {
		lastOverlay = text;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.inGameHud != null) {
			mc.inGameHud.setOverlayMessage(text, false);
		}
	}

	private static void sound(SoundEvent event, float pitch) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getSoundManager() != null) {
			mc.getSoundManager().play(PositionedSoundInstance.master(event, pitch, 1.0f));
		}
	}

	private static String fmt(double d) {
		return String.format(Locale.ROOT, "%.2f", d);
	}

	private static boolean isBusy() {
		return running != null || crashActive || bjActive;
	}

	private static Text reel(int s) {
		return Text.literal(SYMBOLS[s]).formatted(SYMBOL_COLORS[s]);
	}

	private static Text reels(int a, int b, int c) {
		return Text.literal("[ ")
			.append(reel(a)).append(" | ")
			.append(reel(b)).append(" | ")
			.append(reel(c)).append(" ]");
	}

	private static Formatting rouletteFormat(int n) {
		if (n == 0) return Formatting.GREEN;
		return RED.contains(n) ? Formatting.RED : Formatting.GRAY;
	}

	private static String rouletteColor(int n) {
		if (n == 0) return "gruen";
		return RED.contains(n) ? "rot" : "schwarz";
	}

	private static String cardName(int c) {
		return switch (c) {
			case 1 -> "Ass";
			case 11 -> "Bube";
			case 12 -> "Dame";
			case 13 -> "König";
			default -> String.valueOf(c);
		};
	}

	// ---------- Spiele mit Animation ----------

	private static Game coinflip(boolean guessHeads) {
		final boolean heads = RNG.nextBoolean();
		final Text result = Text.literal("Münze: " + (heads ? "Kopf" : "Zahl")).formatted(Formatting.YELLOW);
		final double mult = heads == guessHeads ? 2.0 : 0.0;

		IntConsumer tick = t -> {
			if (t % 4 == 0) {
				boolean showHeads = (t / 4) % 2 == 0;
				overlay(Text.literal("Münze dreht sich... " + (showHeads ? "Kopf" : "Zahl")).formatted(Formatting.GOLD));
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f + (t / 40.0f));
			}
		};
		return new Game(mult, result, 40, tick);
	}

	private static double slotsMultiplier(int a, int b, int c) {
		if (a == b && b == c) {
			return (a == SYMBOLS.length - 1) ? 25.0 : 10.0; // 7-7-7 = Jackpot
		} else if (a == b || b == c || a == c) {
			return 1.5;
		}
		return 0.0;
	}

	private static Game slots() {
		final int a = RNG.nextInt(SYMBOLS.length);
		final int b = RNG.nextInt(SYMBOLS.length);
		final int c = RNG.nextInt(SYMBOLS.length);
		final int stop1 = 20, stop2 = 35, stop3 = 50;

		IntConsumer tick = t -> {
			boolean stopping = (t == stop1 || t == stop2 || t == stop3);
			if (t % 2 == 0 || stopping) {
				int r1 = t >= stop1 ? a : RNG.nextInt(SYMBOLS.length);
				int r2 = t >= stop2 ? b : RNG.nextInt(SYMBOLS.length);
				int r3 = t >= stop3 ? c : RNG.nextInt(SYMBOLS.length);
				overlay(reels(r1, r2, r3));
			}
			if (t == stop1) {
				sound(SoundEvents.BLOCK_LEVER_CLICK, 0.8f);
			} else if (t == stop2) {
				sound(SoundEvents.BLOCK_LEVER_CLICK, 1.0f);
			} else if (t == stop3) {
				sound(SoundEvents.BLOCK_LEVER_CLICK, 1.3f);
			} else if (t % 2 == 0 && t < stop3) {
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.6f);
			}
		};
		return new Game(slotsMultiplier(a, b, c), reels(a, b, c), stop3, tick);
	}

	// Du und der Dealer wuerfeln je einen W6. Hoeher gewinnt, Gleichstand = Einsatz zurueck.
	private static Game dice() {
		final int you = RNG.nextInt(6) + 1;
		final int dealer = RNG.nextInt(6) + 1;
		final double mult = you > dealer ? 2.0 : (you == dealer ? 1.0 : 0.0);
		final Text result = Text.literal("Du: " + you + "  |  Dealer: " + dealer).formatted(Formatting.WHITE);

		IntConsumer tick = t -> {
			if (t % 2 == 0 || t == 20) {
				String youStr = t >= 20 ? String.valueOf(you) : String.valueOf(RNG.nextInt(6) + 1);
				String dealerStr = t >= 40 ? String.valueOf(dealer) : (t >= 20 ? String.valueOf(RNG.nextInt(6) + 1) : "?");
				overlay(Text.literal("Du: " + youStr + "  |  Dealer: " + dealerStr).formatted(Formatting.WHITE));
			}
			if (t == 20 || t == 40) {
				sound(SoundEvents.BLOCK_LEVER_CLICK, 1.2f);
			} else if (t % 2 == 0) {
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.4f);
			}
		};
		return new Game(mult, result, 40, tick);
	}

	private static Game roulette(String pick) {
		final int n = RNG.nextInt(37); // 0-36
		final String color = rouletteColor(n);
		final Text result = Text.literal("Kugel landet auf " + n + " (" + color + ")").formatted(rouletteFormat(n));
		double m = 0.0;
		if (pick.equals(color)) {
			m = color.equals("gruen") ? 14.0 : 2.0;
		}
		final double mult = m;

		IntConsumer tick = t -> {
			// Das Rad wird langsamer: erst alle 2 Ticks, dann 3, dann 5
			boolean show = (t < 30 && t % 2 == 0) || (t >= 30 && t < 45 && t % 3 == 0) || (t >= 45 && t % 5 == 0);
			if (show) {
				int shown = (t >= 55) ? n : RNG.nextInt(37);
				overlay(Text.literal("Rad dreht sich... " + shown).formatted(rouletteFormat(shown)));
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.8f - (t / 60.0f));
			}
		};
		return new Game(mult, result, 60, tick);
	}

	// Gluecksrad: 12 Felder, der Zeiger wird immer langsamer
	private static String wheelLabel(double m) {
		if (m == 0) return "Niete";
		return m == Math.floor(m) ? "x" + (int) m : "x" + m;
	}

	private static Game wheel() {
		final int target = RNG.nextInt(WHEEL.length);
		final int n = WHEEL.length * 2 + target; // zwei volle Runden + Weg bis zum Ziel
		final int duration = 70;
		final int[] times = new int[n];
		for (int k = 1; k <= n; k++) {
			times[k - 1] = (int) Math.round(duration * Math.pow((double) k / n, 1.6));
		}
		final double mult = WHEEL[target];
		final Text result = Text.literal("Glücksrad: " + wheelLabel(mult)).formatted(Formatting.GOLD);

		IntConsumer tick = t -> {
			int advanced = 0;
			boolean moved = false;
			for (int ti : times) {
				if (ti <= t) advanced++;
				if (ti == t) moved = true;
			}
			if (moved || t == 1) {
				double shown = WHEEL[advanced % WHEEL.length];
				overlay(Text.literal("Glücksrad: " + wheelLabel(shown)).formatted(Formatting.GOLD));
			}
			if (moved) {
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.2f + (t / 140.0f));
			}
		};
		return new Game(mult, result, duration, tick);
	}

	// Hoch/Tief: Wird die zweite Karte hoeher oder niedriger? Gleichstand verliert.
	private static Game highLow(boolean high) {
		final int c1 = RNG.nextInt(13) + 1;
		final int c2 = RNG.nextInt(13) + 1;
		final boolean win = high ? c2 > c1 : c2 < c1;
		final Text result = Text.literal("Karte 1: " + cardName(c1) + "  |  Karte 2: " + cardName(c2)).formatted(Formatting.WHITE);

		IntConsumer tick = t -> {
			if (t % 3 == 0 && t < 30) {
				overlay(Text.literal("Karte 1: " + cardName(c1) + "  |  Karte 2: " + cardName(RNG.nextInt(13) + 1)).formatted(Formatting.WHITE));
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f + (t / 60.0f));
			}
		};
		return new Game(win ? 2.0 : 0.0, result, 30, tick);
	}

	// ---------- Crash ----------

	private static double crashMult(int ticks) {
		return Math.pow(1.012, ticks);
	}

	private static int startCrash(CommandContext<FabricClientCommandSource> ctx) {
		return startCrashRound(LongArgumentType.getLong(ctx, "einsatz"));
	}

	static int startCrashRound(long bet) {
		if (isBusy()) {
			chat("Beende erst deine laufende Runde!", Formatting.YELLOW);
			return 0;
		}
		if (bet > balance) {
			chat("Nicht genug Spielgeld! Du hast nur " + balance + ".", Formatting.RED);
			return 0;
		}
		balance -= bet;
		save();
		crashBet = bet;
		crashTicks = 0;
		crashPoint = Math.min(200.0, Math.max(1.0, 0.96 / (1.0 - RNG.nextDouble())));
		crashActive = true;
		chat("Crash gestartet! Zahle mit /gamble cashout aus, bevor es crasht.", Formatting.AQUA);
		return 1;
	}

	private static void tickCrash() {
		crashTicks++;
		double m = crashMult(crashTicks);
		if (m >= crashPoint) {
			crashActive = false;
			overlay(Text.literal("CRASH bei x" + fmt(crashPoint)).formatted(Formatting.RED));
			chat("CRASH bei x" + fmt(crashPoint) + "!", Formatting.RED);
			payoutAndReport(crashBet, 0.0);
			return;
		}
		if (crashTicks % 2 == 0) {
			overlay(Text.literal("Multiplikator: x" + fmt(m) + "   (/gamble cashout)").formatted(Formatting.GREEN));
		}
		if (crashTicks % 10 == 0) {
			sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f + (float) Math.min(1.0, m / 10.0));
		}
	}

	private static int cashout() {
		if (!crashActive) {
			chat("Keine laufende Crash-Runde. Starte mit /gamble crash <einsatz>.", Formatting.YELLOW);
			return 0;
		}
		double m = crashMult(crashTicks);
		crashActive = false;
		overlay(Text.literal("Ausgezahlt bei x" + fmt(m)).formatted(Formatting.GREEN));
		chat("Ausgezahlt bei x" + fmt(m) + " (Crash wäre bei x" + fmt(crashPoint) + " gewesen)", Formatting.AQUA);
		payoutAndReport(crashBet, m);
		return 1;
	}

	// ---------- Blackjack ----------

	private static int draw() {
		return RNG.nextInt(13) + 1;
	}

	private static int total(List<Integer> cards) {
		int sum = 0;
		int aces = 0;
		for (int c : cards) {
			if (c == 1) {
				aces++;
				sum += 11;
			} else {
				sum += Math.min(c, 10);
			}
		}
		while (sum > 21 && aces > 0) {
			sum -= 10;
			aces--;
		}
		return sum;
	}

	private static String handText(List<Integer> cards) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cards.size(); i++) {
			if (i > 0) sb.append(", ");
			sb.append(cardName(cards.get(i)));
		}
		return sb + " (" + total(cards) + ")";
	}

	private static int startBlackjack(CommandContext<FabricClientCommandSource> ctx) {
		return startBlackjackRound(LongArgumentType.getLong(ctx, "einsatz"));
	}

	static int startBlackjackRound(long bet) {
		if (isBusy()) {
			chat("Beende erst deine laufende Runde!", Formatting.YELLOW);
			return 0;
		}
		if (bet > balance) {
			chat("Nicht genug Spielgeld! Du hast nur " + balance + ".", Formatting.RED);
			return 0;
		}
		balance -= bet;
		save();
		bjBet = bet;
		bjPlayer.clear();
		bjDealer.clear();
		bjPlayer.add(draw());
		bjDealer.add(draw());
		bjPlayer.add(draw());
		bjDealer.add(draw());
		bjActive = true;
		sound(SoundEvents.BLOCK_LEVER_CLICK, 1.2f);

		chat("Blackjack – deine Karten: " + handText(bjPlayer), Formatting.WHITE);
		chat("Dealer zeigt: " + cardName(bjDealer.get(0)) + ", ?", Formatting.GRAY);

		if (total(bjPlayer) == 21) {
			chat("Blackjack!", Formatting.GOLD);
			chat("Dealer: " + handText(bjDealer), Formatting.GRAY);
			bjActive = false;
			payoutAndReport(bjBet, total(bjDealer) == 21 ? 1.0 : 2.5);
		} else {
			chat("/gamble hit = Karte ziehen, /gamble stand = bleiben", Formatting.AQUA);
		}
		return 1;
	}

	private static int bjHit() {
		if (!bjActive) {
			chat("Keine laufende Blackjack-Runde. Starte mit /gamble blackjack <einsatz>.", Formatting.YELLOW);
			return 0;
		}
		bjPlayer.add(draw());
		sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.3f);
		chat("Du ziehst: " + cardName(bjPlayer.get(bjPlayer.size() - 1)) + " → " + handText(bjPlayer), Formatting.WHITE);

		int t = total(bjPlayer);
		if (t > 21) {
			bjActive = false;
			chat("Überkauft!", Formatting.RED);
			payoutAndReport(bjBet, 0.0);
		} else if (t == 21) {
			dealerPlay();
		} else {
			chat("/gamble hit oder /gamble stand", Formatting.AQUA);
		}
		return 1;
	}

	private static int bjStand() {
		if (!bjActive) {
			chat("Keine laufende Blackjack-Runde. Starte mit /gamble blackjack <einsatz>.", Formatting.YELLOW);
			return 0;
		}
		dealerPlay();
		return 1;
	}

	private static void dealerPlay() {
		while (total(bjDealer) < 17) {
			bjDealer.add(draw());
		}
		int p = total(bjPlayer);
		int d = total(bjDealer);
		chat("Dealer: " + handText(bjDealer), Formatting.GRAY);

		double mult;
		if (d > 21 || p > d) {
			mult = 2.0;
		} else if (p == d) {
			mult = 1.0;
		} else {
			mult = 0.0;
		}
		bjActive = false;
		payoutAndReport(bjBet, mult);
	}

	// ---------- Ablauf ----------

	private static int play(CommandContext<FabricClientCommandSource> ctx, Supplier<Game> gameSupplier) {
		return startGame(LongArgumentType.getLong(ctx, "einsatz"), gameSupplier);
	}

	static int startGame(long bet, Supplier<Game> gameSupplier) {
		if (isBusy()) {
			chat("Beende erst deine laufende Runde!", Formatting.YELLOW);
			return 0;
		}
		if (bet > balance) {
			chat("Nicht genug Spielgeld! Du hast nur " + balance + ".", Formatting.RED);
			return 0;
		}

		balance -= bet;
		save();
		runningBet = bet;
		runningTick = 0;
		running = gameSupplier.get();
		return 1;
	}

	private static void tickAll() {
		if (crashActive) {
			tickCrash();
		}
		tickAnimation();
	}

	private static void tickAnimation() {
		if (running == null) {
			return;
		}
		runningTick++;
		if (runningTick <= running.duration()) {
			running.tick().accept(runningTick);
		}
		if (runningTick >= running.duration()) {
			finishRound();
		}
	}

	private static void finishRound() {
		Game game = running;
		long bet = runningBet;
		running = null;

		overlay(game.result());
		chat(game.result());
		payoutAndReport(bet, game.multiplier());
	}

	/** Zahlt aus, meldet Gewinn/Verlust und spielt den passenden Sound. */
	private static void payoutAndReport(long bet, double multiplier) {
		long payout = Math.round(bet * multiplier);
		boolean ringUsed = false;
		if (payout == 0 && rings > 0 && bet > 0) {
			rings--;
			payout = Math.max(1, bet / 2);
			ringUsed = true;
		}
		balance += payout;
		save();

		if (payout > bet) {
			chat("Gewonnen! +" + (payout - bet) + " Spielgeld", Formatting.GREEN);
			SoundEvent custom = soundFor(activeSound);
			if (custom != null) {
				sound(custom, 1.0f);
			} else if (multiplier >= 10.0) {
				sound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f);
			} else {
				sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f);
			}
		} else if (payout == bet) {
			chat("Unentschieden – Einsatz zurück.", Formatting.YELLOW);
			sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f);
		} else {
			if (ringUsed) {
				chat("Rettungsring benutzt: Die Hälfte vom Einsatz kommt zurück.", Formatting.AQUA);
			}
			chat("Verloren! -" + (bet - payout) + " Spielgeld", Formatting.RED);
			sound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f);
		}
		chat(balanceText());

		if (balance <= 0) {
			chat("Pleite! Mit /gamble reset startest du neu.", Formatting.DARK_RED);
		}
	}

	private static int help() {
		chat("=== Fun Gamble (nur für dich sichtbar) ===", Formatting.GOLD);
		chat("/gamble  oder  /gamble menu  →  Casino-Menü", Formatting.WHITE);
		chat("/gamble balance", Formatting.WHITE);
		chat("/gamble coinflip <einsatz> <kopf|zahl>", Formatting.WHITE);
		chat("/gamble slots <einsatz>", Formatting.WHITE);
		chat("/gamble dice <einsatz>", Formatting.WHITE);
		chat("/gamble roulette <einsatz> <rot|schwarz|gruen>", Formatting.WHITE);
		chat("/gamble wheel <einsatz>", Formatting.WHITE);
		chat("/gamble highlow <einsatz> <hoch|tief>", Formatting.WHITE);
		chat("/gamble crash <einsatz>  →  /gamble cashout", Formatting.WHITE);
		chat("/gamble blackjack <einsatz>  →  /gamble hit | stand", Formatting.WHITE);
		chat("/gamble shop  →  /gamble buy <item>  |  equip <item>  |  unequip <title|sound>", Formatting.WHITE);
		chat("/gamble reset", Formatting.WHITE);
		return 1;
	}

	private static int showBalance() {
		chat(balanceText());
		return 1;
	}

	private static int reset() {
		if (isBusy()) {
			chat("Beende erst deine laufende Runde!", Formatting.YELLOW);
			return 0;
		}
		balance = START_MONEY;
		save();
		chat("Zurückgesetzt auf " + START_MONEY + " Spielgeld.", Formatting.YELLOW);
		return 1;
	}

	// ---------- Schnittstelle fuer die Menues ----------

	private static int openMenu() {
		MinecraftClient mc = MinecraftClient.getInstance();
		// Erst im naechsten Tick oeffnen, sonst schliesst der Chat das Fenster wieder
		mc.send(() -> mc.setScreen(new GambleScreen()));
		return 1;
	}

	static long getBalance() {
		return balance;
	}

	static Text statusLine1() {
		if (running != null || crashActive) {
			return lastOverlay;
		}
		return recentLine(1);
	}

	static Text statusLine2() {
		if (running != null || crashActive) {
			return recentLine(1);
		}
		return recentLine(2);
	}

	/** n = 1: letzte Chat-Zeile, n = 2: die davor. */
	private static Text recentLine(int n) {
		List<Text> list = new ArrayList<>(recent);
		int index = list.size() - n;
		return index >= 0 ? list.get(index) : Text.empty();
	}

	static void uiSlots(long bet) { startGame(bet, FunGambleClient::slots); }
	static void uiDice(long bet) { startGame(bet, FunGambleClient::dice); }
	static void uiCoin(long bet, boolean heads) { startGame(bet, () -> coinflip(heads)); }
	static void uiRoulette(long bet, String color) { startGame(bet, () -> roulette(color)); }
	static void uiWheel(long bet) { startGame(bet, FunGambleClient::wheel); }
	static void uiHighLow(long bet, boolean high) { startGame(bet, () -> highLow(high)); }
	static void uiCrash(long bet) { startCrashRound(bet); }
	static void uiBlackjack(long bet) { startBlackjackRound(bet); }
	static void uiCashout() { cashout(); }
	static void uiHit() { bjHit(); }
	static void uiStand() { bjStand(); }

	static Text shopLabel(Item item) {
		String status;
		Formatting color;
		if (item.type().equals("ring")) {
			status = item.price() + " Spielgeld (du hast: " + rings + ")";
			color = Formatting.WHITE;
		} else if (item.id().equals(activeTitle) || item.id().equals(activeSound)) {
			status = "[aktiv]";
			color = Formatting.GREEN;
		} else if (owned.contains(item.id())) {
			status = "[gekauft]";
			color = Formatting.YELLOW;
		} else {
			status = item.price() + " Spielgeld";
			color = Formatting.WHITE;
		}
		return Text.literal(item.name() + " – " + status).formatted(color);
	}

	static void shopClick(Item item) {
		if (item.type().equals("ring")) {
			buy(item.id());
		} else if (item.id().equals(activeTitle) || item.id().equals(activeSound)) {
			unequip(item.type());
		} else if (owned.contains(item.id())) {
			equip(item.id());
		} else {
			buy(item.id());
		}
	}

	static Text balanceLabel() {
		return balanceText();
	}

	// ---------- Shop ----------

	private static Item findItem(String id) {
		for (Item item : ITEMS) {
			if (item.id().equals(id)) return item;
		}
		return null;
	}

	private static SoundEvent soundFor(String id) {
		return switch (id) {
			case "feuerwerk" -> SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST;
			case "glocke" -> SoundEvents.BLOCK_BELL_USE;
			case "donner" -> SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER;
			default -> null;
		};
	}

	private static Text balanceText() {
		Item title = findItem(activeTitle);
		String prefix = title != null ? "[" + title.name() + "] " : "";
		return Text.literal(prefix + "Kontostand: " + balance + " Spielgeld").formatted(Formatting.GOLD);
	}

	private static int showShop() {
		chat("=== Spielgeld-Shop ===", Formatting.GOLD);
		chat(balanceText());
		for (Item item : ITEMS) {
			String status;
			Formatting color;
			if (item.type().equals("ring")) {
				status = item.price() + " Spielgeld (du hast: " + rings + ")";
				color = Formatting.WHITE;
			} else if (item.id().equals(activeTitle) || item.id().equals(activeSound)) {
				status = "[aktiv]";
				color = Formatting.GREEN;
			} else if (owned.contains(item.id())) {
				status = "[gekauft]";
				color = Formatting.YELLOW;
			} else {
				status = item.price() + " Spielgeld";
				color = Formatting.WHITE;
			}
			chat(item.name() + " (" + item.desc() + ") – " + status + "  →  /gamble buy " + item.id(), color);
		}
		chat("Ausrüsten: /gamble equip <item>   Ablegen: /gamble unequip <title|sound>", Formatting.GRAY);
		return 1;
	}

	private static int buy(String id) {
		if (isBusy()) {
			chat("Beende erst deine laufende Runde!", Formatting.YELLOW);
			return 0;
		}
		Item item = findItem(id);
		if (item == null) {
			chat("Dieses Item gibt es nicht.", Formatting.RED);
			return 0;
		}
		boolean ring = item.type().equals("ring");
		if (!ring && owned.contains(id)) {
			chat("Das hast du schon! Rüste es mit /gamble equip " + id + " aus.", Formatting.YELLOW);
			return 0;
		}
		if (balance < item.price()) {
			chat("Zu wenig Spielgeld! " + item.name() + " kostet " + item.price() + ", du hast " + balance + ".", Formatting.RED);
			return 0;
		}
		balance -= item.price();
		if (ring) {
			rings++;
			chat("Gekauft: " + item.name() + " (jetzt: " + rings + ")", Formatting.GREEN);
		} else {
			owned.add(id);
			if (item.type().equals("title")) {
				activeTitle = id;
			} else {
				activeSound = id;
			}
			chat("Gekauft und ausgerüstet: " + item.name(), Formatting.GREEN);
		}
		save();
		sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
		chat(balanceText());
		return 1;
	}

	private static int equip(String id) {
		Item item = findItem(id);
		if (item == null || item.type().equals("ring")) {
			chat("Dieses Item kann man nicht ausrüsten.", Formatting.RED);
			return 0;
		}
		if (!owned.contains(id)) {
			chat("Das gehört dir noch nicht. Kaufe es mit /gamble buy " + id + ".", Formatting.YELLOW);
			return 0;
		}
		if (item.type().equals("title")) {
			activeTitle = id;
		} else {
			activeSound = id;
		}
		save();
		chat("Ausgerüstet: " + item.name(), Formatting.GREEN);
		return 1;
	}

	private static int unequip(String type) {
		if (type.equals("title")) {
			activeTitle = "";
		} else {
			activeSound = "";
		}
		save();
		chat("Abgelegt.", Formatting.YELLOW);
		return 1;
	}

	// ---------- Speichern ----------

	private static void load() {
		try {
			if (!Files.exists(saveFile)) {
				return;
			}
			String text = Files.readString(saveFile).trim();
			if (text.matches("-?\\d+")) {
				// altes Format: nur der Kontostand
				balance = Long.parseLong(text);
				return;
			}
			Properties p = new Properties();
			p.load(new StringReader(text));
			balance = Long.parseLong(p.getProperty("balance", String.valueOf(START_MONEY)));
			owned.clear();
			for (String id : p.getProperty("owned", "").split(",")) {
				if (!id.isBlank() && findItem(id.trim()) != null) {
					owned.add(id.trim());
				}
			}
			activeTitle = p.getProperty("title", "");
			activeSound = p.getProperty("sound", "");
			rings = Integer.parseInt(p.getProperty("rings", "0"));
		} catch (Exception e) {
			balance = START_MONEY;
		}
	}

	private static void save() {
		try {
			Properties p = new Properties();
			p.setProperty("balance", Long.toString(balance));
			p.setProperty("owned", String.join(",", owned));
			p.setProperty("title", activeTitle);
			p.setProperty("sound", activeSound);
			p.setProperty("rings", Integer.toString(rings));
			StringWriter sw = new StringWriter();
			p.store(sw, null);
			Files.writeString(saveFile, sw.toString());
		} catch (Exception ignored) {
		}
	}
}
