package de.fungamble;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
 * Version 2.3: ueber 25 Spiele, Menues, Shop, Daily, Memory, Texturpack-Bilder, Kosmetik und 3D-Huete.
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
		new Item("rettungsring", "Rettungsring", "ring", 300, "Bei einer Niete bekommst du 50 % vom Einsatz zurück (einmalig)"),
		// Kosmetik: Farben (Kontostand und Meldungen)
		new Item("c_smaragd", "Smaragd-Grün", "color", 1000, "Farbe"),
		new Item("c_ozean", "Ozean-Blau", "color", 1000, "Farbe"),
		new Item("c_rubin", "Rubin-Rot", "color", 1000, "Farbe"),
		new Item("c_violett", "Violett", "color", 1500, "Farbe"),
		new Item("c_regenbogen", "Regenbogen (animiert)", "color", 5000, "Farbe"),
		// Kosmetik: Rahmen um den Kontostand
		new Item("f_tilde", "Tilde-Rahmen  ~ ~", "frame", 800, "Rahmen"),
		new Item("f_sterne", "Sterne-Rahmen  * *", "frame", 800, "Rahmen"),
		new Item("f_pfeile", "Pfeil-Rahmen  << >>", "frame", 1200, "Rahmen"),
		new Item("f_klammern", "Klammer-Rahmen  [[ ]]", "frame", 1200, "Rahmen"),
		new Item("f_linien", "Linien-Rahmen  === ===", "frame", 2000, "Rahmen"),
		// Kosmetik: Gewinn-Banner
		new Item("b_konfetti", "Konfetti-Banner", "banner", 2000, "Gewinn-Banner"),
		new Item("b_gold", "Gold-Banner", "banner", 2500, "Gewinn-Banner"),
		new Item("b_feuer", "Feuer-Banner", "banner", 3000, "Gewinn-Banner"),
		// Kosmetik: Kartenruecken (brauchen das Texturpack)
		new Item("k_rot", "Kartenrücken Rot (Texturpack)", "back", 1500, "Kartenrücken"),
		new Item("k_gold", "Kartenrücken Schwarz-Gold (Texturpack)", "back", 3000, "Kartenrücken"),
		// Kosmetik: 3D-Huete (Texturpack noetig, der Hut wird per /give geholt)
		new Item("hat_party", "Partyhut", "hat", 1000, "Hut"),
		new Item("hat_propeller", "Propellermütze", "hat", 1500, "Hut"),
		new Item("hat_santa", "Weihnachtsmütze", "hat", 2000, "Hut"),
		new Item("hat_cowboy", "Cowboyhut", "hat", 2500, "Hut"),
		new Item("hat_zylinder", "Zylinder", "hat", 3000, "Hut"),
		new Item("hat_pirat", "Piratenhut", "hat", 4000, "Hut"),
		new Item("hat_magier", "Zauberhut", "hat", 6000, "Hut"),
		new Item("hat_krone", "Krone", "hat", 10000, "Hut")
	);

	static final LinkedHashSet<String> owned = new LinkedHashSet<>();
	static String activeTitle = "";
	static String activeSound = "";
	static String activeColor = "";
	static String activeFrame = "";
	static String activeBanner = "";
	static String activeBack = "";
	static int rings = 0;

	// Plinko / Rubbellos
	private static final double[] PLINKO = {10, 3, 1.4, 0.7, 0.3, 0.7, 1.4, 3, 10};
	private static final String[] RPS = {"Stein", "Schere", "Papier"};

	// Reaktionstest (0 = aus, 1 = warten, 2 = jetzt!)
	private static int reflexState = 0;
	private static long reflexBet = 0;
	private static int reflexWait = 0;
	private static int reflexGo = 0;

	// Tower
	private static boolean towerActive = false;
	private static long towerBet = 0;
	private static int towerLevel = 0;
	private static final int TOWER_MAX = 10;
	private static final int[] towerBomb = new int[TOWER_MAX];

	// Mines
	private static final int MINES = 3;
	private static boolean minesActive = false;
	private static boolean minesDone = false;
	private static long minesBet = 0;
	private static int minesSafe = 0;
	private static final boolean[] minesMine = new boolean[25];
	private static final boolean[] minesOpen = new boolean[25];

	// Kartenleiter (Hoch/Tief-Serie)
	private static boolean ladderActive = false;
	private static long ladderBet = 0;
	private static int ladderCard = 0;
	private static double ladderMult = 1.0;

	// Muenz-Serie
	private static boolean streakActive = false;
	private static long streakBet = 0;
	private static double streakMult = 1.0;
	private static int streakCount = 0;

	// Video-Poker (0 = aus, 1 = ausgeteilt, 2 = fertig)
	private static int pokerState = 0;
	private static long pokerBet = 0;
	private static final int[] pokerHand = new int[5];
	private static final boolean[] pokerHold = new boolean[5];
	private static final List<Integer> pokerDeck = new ArrayList<>();
	private static final String[] POKER_NAMES = {"Nichts", "Paar (Buben oder besser)", "Zwei Paare", "Drilling", "Straße",
		"Flush", "Full House", "Vierling", "Straight Flush", "Royal Flush"};
	private static final int[] POKER_PAY = {0, 1, 2, 3, 4, 6, 9, 25, 50, 250};

	// Wuerfel 100
	static final int[] DICE100_THRESHOLDS = {10, 25, 50, 75, 90};
	static int dice100Index = 2;

	// Pferderennen
	private static final String[] RACE_NAMES = {"Blitz", "Sturm", "Komet", "Fuchs", "Pfeil"};
	private static final double[] RACE_P = {0.38, 0.27, 0.19, 0.12, 0.04};
	private static final double[] RACE_ODDS = {2.5, 3.5, 5.0, 7.9, 23.0};

	// Bild-Symbole aus dem Texturpack (private Unicode-Zeichen, ohne Pack nur leere Kaestchen)
	private static final String[] ICON_SLOT = {"\uE000", "\uE001", "\uE002", "\uE003", "\uE004", "\uE005"};
	private static final String[] ICON_DIE = {"\uE008", "\uE009", "\uE00A", "\uE00B", "\uE00C", "\uE00D"};
	private static final String ICON_BOMB = "\uE00E";
	private static final String ICON_GEM = "\uE00F";
	private static final String[] ICON_SUIT = {"\uE010", "\uE011", "\uE012", "\uE013"};
	private static final String[] ICON_BIG = {"\uE100", "\uE101", "\uE102", "\uE103", "\uE104", "\uE105"};
	private static final String ICON_BIG_BACK = "\uE114";
	private static long packCheckedAt = 0;
	private static boolean packCached = false;

	// Memory (6 Paare, 12 Karten)
	private static boolean memoryActive = false;
	private static long memoryBet = 0;
	private static final int[] memoryCards = new int[12];
	private static final boolean[] memoryMatched = new boolean[12];
	private static int memoryFirst = -1;
	private static int memorySecond = -1;
	private static int memoryMoves = 0;
	private static int memoryPairs = 0;
	private static int memoryHide = 0;

	// Taegliche Belohnung
	private static String lastDaily = "";
	private static int dailyStreak = 0;

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
			root.then(ClientCommandManager.literal("shop").executes(ctx -> openScreenByName("shop")));
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
			LiteralArgumentBuilder<FabricClientCommandSource> unequipNode = ClientCommandManager.literal("unequip");
			for (String type : new String[]{"title", "sound", "color", "frame", "banner", "back"}) {
				unequipNode.then(ClientCommandManager.literal(type).executes(ctx -> unequip(type)));
			}
			root.then(unequipNode);

			// Mini-Games
			root.then(ClientCommandManager.literal("rps").then(bet()
				.then(ClientCommandManager.literal("stein").executes(ctx -> play(ctx, () -> rps(0))))
				.then(ClientCommandManager.literal("schere").executes(ctx -> play(ctx, () -> rps(1))))
				.then(ClientCommandManager.literal("papier").executes(ctx -> play(ctx, () -> rps(2))))));
			root.then(ClientCommandManager.literal("plinko").then(bet()
				.executes(ctx -> play(ctx, FunGambleClient::plinko))));
			root.then(ClientCommandManager.literal("scratch").then(bet()
				.executes(ctx -> play(ctx, FunGambleClient::scratch))));
			root.then(ClientCommandManager.literal("reflex").then(bet()
				.executes(ctx -> startReflex(LongArgumentType.getLong(ctx, "einsatz")))));
			root.then(ClientCommandManager.literal("go").executes(ctx -> reflexGoNow()));
			root.then(ClientCommandManager.literal("tower").then(bet()
				.executes(ctx -> startTower(LongArgumentType.getLong(ctx, "einsatz")))));
			root.then(ClientCommandManager.literal("door")
				.then(ClientCommandManager.literal("1").executes(ctx -> towerDoor(1)))
				.then(ClientCommandManager.literal("2").executes(ctx -> towerDoor(2)))
				.then(ClientCommandManager.literal("3").executes(ctx -> towerDoor(3))));
			root.then(ClientCommandManager.literal("towercash").executes(ctx -> towerCash()));
			root.then(ClientCommandManager.literal("mines").executes(ctx -> openScreenByName("mines")));
			root.then(ClientCommandManager.literal("minigames").executes(ctx -> openScreenByName("sonstiges")));
			root.then(ClientCommandManager.literal("daily").executes(ctx -> daily()));

			// Menues: /gamble menu <name>
			LiteralArgumentBuilder<FabricClientCommandSource> menuNode =
				ClientCommandManager.literal("menu").executes(ctx -> openMenu());
			for (String n : new String[]{"automaten", "wuerfel", "karten", "risiko", "sonstiges", "rennen",
				"blackjack", "poker", "mines", "memory", "shop"}) {
				menuNode.then(ClientCommandManager.literal(n).executes(ctx -> openScreenByName(n)));
			}
			root.then(menuNode);

			// Weitere Spiele
			root.then(lit("dice100").then(bet()
				.then(lit("ueber").then(intArg("schwelle", 2, 98).executes(ctx -> {
					int th = IntegerArgumentType.getInteger(ctx, "schwelle");
					return play(ctx, () -> dice100(true, th));
				})))
				.then(lit("unter").then(intArg("schwelle", 2, 98).executes(ctx -> {
					int th = IntegerArgumentType.getInteger(ctx, "schwelle");
					return play(ctx, () -> dice100(false, th));
				})))));
			root.then(lit("lotto").then(bet().then(intArg("zahl", 1, 10).executes(ctx -> {
				int n = IntegerArgumentType.getInteger(ctx, "zahl");
				return play(ctx, () -> lotto(n));
			}))));
			root.then(lit("baccarat").then(bet()
				.then(lit("spieler").executes(ctx -> play(ctx, () -> baccarat(0))))
				.then(lit("bank").executes(ctx -> play(ctx, () -> baccarat(1))))
				.then(lit("tie").executes(ctx -> play(ctx, () -> baccarat(2))))));
			root.then(lit("craps").then(bet().executes(ctx -> play(ctx, FunGambleClient::craps))));
			root.then(lit("cups").then(bet().then(intArg("becher", 1, 3).executes(ctx -> {
				int n = IntegerArgumentType.getInteger(ctx, "becher");
				return play(ctx, () -> cups(n - 1));
			}))));
			root.then(lit("chests").then(bet().then(intArg("kiste", 1, 4).executes(ctx -> {
				int n = IntegerArgumentType.getInteger(ctx, "kiste");
				return play(ctx, () -> chests(n - 1));
			}))));
			root.then(lit("race").then(bet().then(intArg("pferd", 1, 5).executes(ctx -> {
				int n = IntegerArgumentType.getInteger(ctx, "pferd");
				return play(ctx, () -> race(n - 1));
			}))));
			root.then(lit("sicbo").then(bet()
				.then(lit("gross").executes(ctx -> play(ctx, () -> sicBo(true))))
				.then(lit("klein").executes(ctx -> play(ctx, () -> sicBo(false))))));

			// Video-Poker
			root.then(lit("poker").then(bet().executes(ctx -> pokerDeal(LongArgumentType.getLong(ctx, "einsatz")))));
			root.then(lit("hold").then(intArg("karte", 1, 5).executes(ctx ->
				pokerToggle(IntegerArgumentType.getInteger(ctx, "karte") - 1))));
			root.then(lit("draw").executes(ctx -> pokerDraw()));

			// Kartenleiter und Muenz-Serie
			root.then(lit("ladder").then(bet().executes(ctx -> ladderStart(LongArgumentType.getLong(ctx, "einsatz")))));
			root.then(lit("up").executes(ctx -> ladderGuess(true)));
			root.then(lit("down").executes(ctx -> ladderGuess(false)));
			root.then(lit("laddercash").executes(ctx -> ladderCash()));
			root.then(lit("streak").then(bet().executes(ctx -> streakStart(LongArgumentType.getLong(ctx, "einsatz")))));
			root.then(lit("flip").executes(ctx -> streakFlip()));
			root.then(lit("streakcash").executes(ctx -> streakCash()));

			// Memory und Texturpack
			root.then(lit("memory").then(bet().executes(ctx -> memoryStart(LongArgumentType.getLong(ctx, "einsatz")))));
			root.then(lit("pick").then(intArg("karte", 1, 12).executes(ctx ->
				memoryFlip(IntegerArgumentType.getInteger(ctx, "karte") - 1))));
			root.then(lit("pack").executes(ctx -> packInfo()));

			// Blackjack: Verdoppeln
			root.then(lit("double").executes(ctx -> bjDouble()));

			dispatcher.register(root);
		});
	}

	// Einsatz-Argument: /gamble <spiel> <einsatz> ...
	private static RequiredArgumentBuilder<FabricClientCommandSource, Long> bet() {
		return ClientCommandManager.argument("einsatz", LongArgumentType.longArg(1));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> lit(String name) {
		return ClientCommandManager.literal(name);
	}

	private static RequiredArgumentBuilder<FabricClientCommandSource, Integer> intArg(String name, int min, int max) {
		return ClientCommandManager.argument(name, IntegerArgumentType.integer(min, max));
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

	/** Wie chat(), wird aber nicht in den Statuszeilen der Menues angezeigt (der Kontostand steht dort schon oben). */
	private static void chatOnly(Text text) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			mc.player.sendMessage(text, false);
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
		return running != null || crashActive || bjActive || reflexState != 0 || towerActive || minesActive
			|| ladderActive || streakActive || pokerState == 1 || memoryActive;
	}

	private static Text reel(int s) {
		if (packActive()) {
			return Text.literal(ICON_SLOT[s]);
		}
		return Text.literal(SYMBOLS[s]).formatted(SYMBOL_COLORS[s]);
	}

	/** Erkennt, ob das Fun-Gamble-Texturpack aktiv ist (Ergebnis wird kurz zwischengespeichert). */
	static boolean packActive() {
		long now = System.currentTimeMillis();
		if (now - packCheckedAt > 1500) {
			packCheckedAt = now;
			try {
				MinecraftClient mc = MinecraftClient.getInstance();
				packCached = mc.getResourceManager()
					.getResource(Identifier.of("fungamble", "textures/font/icons.png")).isPresent();
			} catch (Exception e) {
				packCached = false;
			}
		}
		return packCached;
	}

	static Text packStatusText() {
		if (packActive()) {
			return Text.literal("Texturpack aktiv – Bilder werden angezeigt").formatted(Formatting.GREEN);
		}
		return Text.literal("Kein Texturpack erkannt – es gibt nur Text (siehe /gamble pack)").formatted(Formatting.YELLOW);
	}

	private static String die(int n) {
		return packActive() ? ICON_DIE[n - 1] : String.valueOf(n);
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
		final Text result = Text.literal("Du: " + die(you) + "  |  Dealer: " + die(dealer)).formatted(Formatting.WHITE);

		IntConsumer tick = t -> {
			if (t % 2 == 0 || t == 20) {
				String youStr = t >= 20 ? die(you) : die(RNG.nextInt(6) + 1);
				String dealerStr = t >= 40 ? die(dealer) : (t >= 20 ? die(RNG.nextInt(6) + 1) : "?");
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

	// ---------- Mini-Games ----------

	// Schere, Stein, Papier gegen den Computer (0 = Stein, 1 = Schere, 2 = Papier)
	private static Game rps(int pick) {
		final int opp = RNG.nextInt(3);
		final double mult;
		if (opp == pick) {
			mult = 1.0;
		} else if ((opp - pick + 3) % 3 == 1) {
			mult = 1.9;
		} else {
			mult = 0.0;
		}
		final Text result = Text.literal("Du: " + RPS[pick] + "  |  Gegner: " + RPS[opp]).formatted(Formatting.WHITE);

		IntConsumer tick = t -> {
			if (t % 3 == 0 && t < 30) {
				overlay(Text.literal("Du: " + RPS[pick] + "  |  Gegner: " + RPS[RNG.nextInt(3)] + " ...").formatted(Formatting.WHITE));
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f + (t / 60.0f));
			}
		};
		return new Game(mult, result, 30, tick);
	}

	// Plinko: Eine Kugel faellt durch 8 Reihen und landet in einem Fach
	private static Game plinko() {
		final int rows = 8;
		final boolean[] right = new boolean[rows];
		int p = 0;
		for (int i = 0; i < rows; i++) {
			right[i] = RNG.nextBoolean();
			if (right[i]) p++;
		}
		final double mult = PLINKO[p];

		StringBuilder full = new StringBuilder();
		for (int i = 0; i < rows; i++) {
			full.append(right[i] ? ">" : "<");
		}
		final Text result = Text.literal("Plinko: Fach " + wheelLabel(mult) + "  (" + full + ")").formatted(Formatting.GOLD);

		IntConsumer tick = t -> {
			if (t % 6 == 0) {
				int step = Math.min(rows, t / 6);
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < step; i++) {
					sb.append(right[i] ? ">" : "<").append(' ');
				}
				overlay(Text.literal("Plinko: " + sb + "(Reihe " + step + "/" + rows + ")").formatted(Formatting.GOLD));
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f + 0.07f * step);
			}
		};
		return new Game(mult, result, rows * 6 + 6, tick);
	}

	// Rubbellos: 6 Felder, drei oder mehr gleiche Symbole gewinnen
	private static double scratchMultiplier(int best) {
		return switch (best) {
			case 3 -> 1.9;
			case 4 -> 5.0;
			case 5 -> 25.0;
			case 6 -> 100.0;
			default -> 0.0;
		};
	}

	private static Text scratchText(int[] fields, int revealed) {
		var t = Text.literal("Rubbellos: [ ");
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) t.append(" | ");
			if (i < revealed) {
				t.append(reel(fields[i]));
			} else {
				t.append(Text.literal("?").formatted(Formatting.GRAY));
			}
		}
		t.append(" ]");
		return t;
	}

	private static Game scratch() {
		final int[] fields = new int[6];
		final int[] counts = new int[SYMBOLS.length];
		int best = 0;
		for (int i = 0; i < fields.length; i++) {
			fields[i] = RNG.nextInt(SYMBOLS.length);
			counts[fields[i]]++;
			best = Math.max(best, counts[fields[i]]);
		}
		final double mult = scratchMultiplier(best);

		IntConsumer tick = t -> {
			if (t % 8 == 0) {
				int revealed = Math.min(fields.length, t / 8);
				overlay(scratchText(fields, revealed));
				sound(SoundEvents.BLOCK_LEVER_CLICK, 0.9f + 0.05f * revealed);
			}
		};
		return new Game(mult, scratchText(fields, fields.length), fields.length * 8 + 4, tick);
	}

	// Reaktionstest: Warte auf JETZT und druecke dann so schnell wie moeglich
	static int startReflex(long bet) {
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
		reflexBet = bet;
		reflexState = 1;
		reflexWait = 30 + RNG.nextInt(60);
		reflexGo = 0;
		overlay(Text.literal("Warte...").formatted(Formatting.YELLOW));
		chat("Reaktionstest! Drücke erst bei JETZT! (Menü: Button \"JETZT!\" oder /gamble go). Zu früh = verloren.", Formatting.AQUA);
		return 1;
	}

	private static void tickReflex() {
		if (reflexState == 1) {
			reflexWait--;
			if (reflexWait <= 0) {
				reflexState = 2;
				reflexGo = 0;
				overlay(Text.literal("JETZT!").formatted(Formatting.GREEN));
				sound(SoundEvents.BLOCK_LEVER_CLICK, 1.6f);
			}
		} else if (reflexState == 2) {
			reflexGo++;
			if (reflexGo > 40) {
				reflexState = 0;
				overlay(Text.literal("Zu langsam!").formatted(Formatting.RED));
				chat("Zu langsam!", Formatting.RED);
				payoutAndReport(reflexBet, 0.0);
			}
		}
	}

	private static int reflexGoNow() {
		if (reflexState == 0) {
			chat("Kein Reaktionstest aktiv. Starte einen im Menü oder mit /gamble reflex <einsatz>.", Formatting.YELLOW);
			return 0;
		}
		if (reflexState == 1) {
			reflexState = 0;
			overlay(Text.literal("Zu früh!").formatted(Formatting.RED));
			chat("Zu früh!", Formatting.RED);
			payoutAndReport(reflexBet, 0.0);
			return 1;
		}
		int ms = reflexGo * 50;
		double mult;
		if (reflexGo <= 5) {
			mult = 3.0;
		} else if (reflexGo <= 8) {
			mult = 2.0;
		} else if (reflexGo <= 12) {
			mult = 1.0;
		} else if (reflexGo <= 20) {
			mult = 0.5;
		} else {
			mult = 0.0;
		}
		reflexState = 0;
		overlay(Text.literal("Reaktionszeit: ca. " + ms + " ms").formatted(Formatting.GREEN));
		chat("Reaktionszeit: ca. " + ms + " ms", Formatting.AQUA);
		payoutAndReport(reflexBet, mult);
		return 1;
	}

	// Tower: Pro Stufe drei Tueren, hinter einer ist eine Falle
	private static double towerMult(int level) {
		return Math.pow(1.4, level);
	}

	static int startTower(long bet) {
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
		towerBet = bet;
		towerLevel = 0;
		for (int i = 0; i < TOWER_MAX; i++) {
			towerBomb[i] = RNG.nextInt(3);
		}
		towerActive = true;
		chat("Tower gestartet! Wähle Tür 1, 2 oder 3 (/gamble door <1|2|3>). Hinter einer Tür ist eine Falle.", Formatting.AQUA);
		return 1;
	}

	static int towerDoor(int door) {
		if (!towerActive) {
			chat("Kein laufender Tower. Starte mit /gamble tower <einsatz>.", Formatting.YELLOW);
			return 0;
		}
		if (towerBomb[towerLevel] == door - 1) {
			towerActive = false;
			chat("BOOM! Hinter Tür " + door + " war die Falle.", Formatting.RED);
			payoutAndReport(towerBet, 0.0);
			return 1;
		}
		towerLevel++;
		sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f + 0.05f * towerLevel);
		double m = towerMult(towerLevel);
		if (towerLevel >= TOWER_MAX) {
			towerActive = false;
			chat("Die Spitze ist erreicht!", Formatting.GOLD);
			payoutAndReport(towerBet, m);
		} else {
			chat("Sicher! Stufe " + towerLevel + "/" + TOWER_MAX + " – x" + fmt(m) + "  (nächste Tür oder Cashout)", Formatting.GREEN);
		}
		return 1;
	}

	static int towerCash() {
		if (!towerActive) {
			chat("Kein laufender Tower.", Formatting.YELLOW);
			return 0;
		}
		if (towerLevel == 0) {
			chat("Wähle erst eine Tür!", Formatting.YELLOW);
			return 0;
		}
		towerActive = false;
		payoutAndReport(towerBet, towerMult(towerLevel));
		return 1;
	}

	// Mines: 5x5 Felder, 3 Minen. Jedes sichere Feld erhoeht den Multiplikator.
	private static double minesMultiplier(int safe) {
		if (safe <= 0) {
			return 1.0;
		}
		double m = 1.0;
		for (int i = 0; i < safe; i++) {
			m *= (25.0 - i) / (25.0 - MINES - i);
		}
		return 0.96 * m;
	}

	static int minesStart(long bet) {
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
		minesBet = bet;
		minesSafe = 0;
		for (int i = 0; i < 25; i++) {
			minesMine[i] = false;
			minesOpen[i] = false;
		}
		int placed = 0;
		while (placed < MINES) {
			int i = RNG.nextInt(25);
			if (!minesMine[i]) {
				minesMine[i] = true;
				placed++;
			}
		}
		minesActive = true;
		minesDone = false;
		chat("Mines gestartet: " + MINES + " Minen auf 25 Feldern. Decke Felder auf und zahle rechtzeitig aus!", Formatting.AQUA);
		return 1;
	}

	static int minesReveal(int index) {
		if (!minesActive) {
			chat("Keine laufende Mines-Runde. Starte eine mit dem Button \"Mines starten\".", Formatting.YELLOW);
			return 0;
		}
		if (minesOpen[index]) {
			return 0;
		}
		minesOpen[index] = true;
		if (minesMine[index]) {
			minesActive = false;
			minesDone = true;
			chat("BOOM! Das war eine Mine.", Formatting.RED);
			payoutAndReport(minesBet, 0.0);
			return 1;
		}
		minesSafe++;
		sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f + 0.03f * minesSafe);
		if (minesSafe >= 25 - MINES) {
			minesActive = false;
			minesDone = true;
			chat("Alle sicheren Felder gefunden!", Formatting.GOLD);
			payoutAndReport(minesBet, minesMultiplier(minesSafe));
		} else {
			chat("Sicher! x" + fmt(minesMultiplier(minesSafe)) + "  – weiter oder Cashout", Formatting.GREEN);
		}
		return 1;
	}

	static int minesCash() {
		if (!minesActive) {
			chat("Keine laufende Mines-Runde.", Formatting.YELLOW);
			return 0;
		}
		if (minesSafe == 0) {
			chat("Decke erst ein Feld auf!", Formatting.YELLOW);
			return 0;
		}
		minesActive = false;
		minesDone = true;
		payoutAndReport(minesBet, minesMultiplier(minesSafe));
		return 1;
	}

	static Text minesCellLabel(int i) {
		boolean pack = packActive();
		if (minesOpen[i] && minesMine[i]) {
			return pack ? Text.literal(ICON_BOMB) : Text.literal("BOOM").formatted(Formatting.RED);
		}
		if (minesOpen[i]) {
			return pack ? Text.literal(ICON_GEM) : Text.literal("Gem").formatted(Formatting.AQUA);
		}
		if (minesDone && minesMine[i]) {
			return pack ? Text.literal(ICON_BOMB) : Text.literal("Mine").formatted(Formatting.GRAY);
		}
		return Text.literal("?");
	}

	static Text minesCashLabel() {
		if (minesActive && minesSafe > 0) {
			return Text.literal("Cashout x" + fmt(minesMultiplier(minesSafe))).formatted(Formatting.GREEN);
		}
		return Text.literal("Cashout");
	}

	// Taegliche Belohnung
	static int daily() {
		String today = LocalDate.now().toString();
		if (today.equals(lastDaily)) {
			chat("Die heutige Belohnung hast du schon abgeholt. Komm morgen wieder!", Formatting.YELLOW);
			return 0;
		}
		if (LocalDate.now().minusDays(1).toString().equals(lastDaily)) {
			dailyStreak++;
		} else {
			dailyStreak = 1;
		}
		lastDaily = today;
		long reward = 200 + 50L * Math.min(dailyStreak, 10);
		balance += reward;
		save();
		sound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f);
		chat("Tägliche Belohnung: +" + reward + " Spielgeld (Serie: " + dailyStreak + " Tage)", Formatting.GREEN);
		chatOnly(balanceText());
		return 1;
	}

	// ---------- Weitere Spiele mit Animation ----------

	private static int d6() {
		return RNG.nextInt(6) + 1;
	}

	// Wuerfel 100: Eine Zahl von 1 bis 100, du tippst ueber oder unter einer Schwelle
	static double dice100Multiplier(boolean over, int threshold) {
		double chance = over ? 100 - threshold : threshold;
		return Math.round(96.0 / chance * 100.0) / 100.0;
	}

	private static Game dice100(boolean over, int threshold) {
		final int roll = RNG.nextInt(100) + 1;
		final boolean win = over ? roll > threshold : roll <= threshold;
		final double mult = win ? dice100Multiplier(over, threshold) : 0.0;
		final String cond = (over ? "über " : "bis ") + threshold;
		final Text result = Text.literal("Würfel 100: " + roll + "  (Ziel: " + cond + ")")
			.formatted(win ? Formatting.GREEN : Formatting.RED);

		IntConsumer tick = t -> {
			if (t % 2 == 0) {
				overlay(Text.literal("Würfel 100: " + (RNG.nextInt(100) + 1) + " ...  (Ziel: " + cond + ")").formatted(Formatting.WHITE));
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f + (t / 60.0f));
			}
		};
		return new Game(mult, result, 30, tick);
	}

	// Lotto: Rate eine Zahl von 1 bis 10
	private static Game lotto(int pick) {
		final int drawn = RNG.nextInt(10) + 1;
		final double mult = drawn == pick ? 9.0 : 0.0;
		final Text result = Text.literal("Gezogen: " + drawn + "  (dein Tipp: " + pick + ")").formatted(Formatting.GOLD);

		IntConsumer tick = t -> {
			boolean show = (t < 30 && t % 2 == 0) || (t >= 30 && t < 45 && t % 3 == 0) || (t >= 45 && t % 5 == 0);
			if (show) {
				int shown = (t >= 55) ? drawn : RNG.nextInt(10) + 1;
				overlay(Text.literal("Lotto-Ziehung: " + shown).formatted(Formatting.GOLD));
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.8f - (t / 60.0f));
			}
		};
		return new Game(mult, result, 60, tick);
	}

	// Baccarat (vereinfacht): Spieler, Bank oder Unentschieden. Zwei Karten, hoechste Endziffer gewinnt.
	private static int bacValue(int rank) {
		return rank < 10 ? rank : 0;
	}

	private static Game baccarat(int side) {
		final int p1 = draw();
		final int p2 = draw();
		final int b1 = draw();
		final int b2 = draw();
		final int pt = (bacValue(p1) + bacValue(p2)) % 10;
		final int bt = (bacValue(b1) + bacValue(b2)) % 10;
		final int winner = pt > bt ? 0 : (bt > pt ? 1 : 2);
		final double mult = winner == side ? (side == 2 ? 9.0 : 2.1) : 0.0;
		final String pFull = cardName(p1) + ", " + cardName(p2) + " (" + pt + ")";
		final String bFull = cardName(b1) + ", " + cardName(b2) + " (" + bt + ")";
		final String outcome = winner == 0 ? "Spieler gewinnt" : (winner == 1 ? "Bank gewinnt" : "Unentschieden");
		final Text result = Text.literal("Spieler: " + pFull + "  |  Bank: " + bFull + "  →  " + outcome).formatted(Formatting.WHITE);

		IntConsumer tick = t -> {
			if (t % 10 == 0) {
				int stage = t / 10;
				String line;
				if (stage <= 1) {
					line = "Spieler: " + cardName(p1) + ", ?";
				} else if (stage == 2) {
					line = "Spieler: " + pFull;
				} else if (stage == 3) {
					line = "Spieler: " + pFull + "  |  Bank: " + cardName(b1) + ", ?";
				} else {
					line = "Spieler: " + pFull + "  |  Bank: " + bFull;
				}
				overlay(Text.literal(line).formatted(Formatting.WHITE));
				sound(SoundEvents.BLOCK_LEVER_CLICK, 1.0f + 0.1f * stage);
			}
		};
		return new Game(mult, result, 46, tick);
	}

	// Craps (Pass-Line): 7 oder 11 gewinnt sofort, 2/3/12 verliert, sonst wird ein Punkt gespielt
	private static Game craps() {
		final List<String> steps = new ArrayList<>();
		final int first = d6() + d6();
		boolean win;
		if (first == 7 || first == 11) {
			steps.add("Craps: Come-out " + first + " – Gewonnen!");
			win = true;
		} else if (first == 2 || first == 3 || first == 12) {
			steps.add("Craps: Come-out " + first + " – Verloren!");
			win = false;
		} else {
			steps.add("Craps: Come-out " + first + " – der Punkt ist " + first);
			win = false;
			boolean resolved = false;
			for (int i = 0; i < 15 && !resolved; i++) {
				int n = d6() + d6();
				if (n == first) {
					steps.add("Wurf " + n + " – Punkt getroffen! Gewonnen!");
					win = true;
					resolved = true;
				} else if (n == 7) {
					steps.add("Wurf 7 – Verloren!");
					resolved = true;
				} else {
					steps.add("Wurf " + n + " – weiter...");
				}
			}
			if (!resolved) {
				steps.add("Zu lange gewürfelt – Verloren.");
			}
		}
		final boolean won = win;
		final Text result = Text.literal(steps.get(steps.size() - 1)).formatted(won ? Formatting.GREEN : Formatting.RED);

		IntConsumer tick = t -> {
			if (t == 1) {
				overlay(Text.literal("Craps: Würfel rollen...").formatted(Formatting.GOLD));
			}
			if (t % 12 == 0) {
				int idx = t / 12 - 1;
				if (idx >= 0 && idx < steps.size()) {
					overlay(Text.literal(steps.get(idx)).formatted(Formatting.WHITE));
					sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.2f);
				}
			}
		};
		return new Game(won ? 1.95 : 0.0, result, steps.size() * 12 + 8, tick);
	}

	// Huetchenspiel: Unter welchem Becher ist der Ball?
	private static Game cups(int pick) {
		final int ball = RNG.nextInt(3);
		final double mult = pick == ball ? 2.85 : 0.0;
		final Text result = Text.literal("Der Ball war unter Becher " + (ball + 1) + "  (dein Tipp: " + (pick + 1) + ")").formatted(Formatting.GOLD);

		IntConsumer tick = t -> {
			if (t % 4 == 0 && t < 44) {
				String dots = ".".repeat(1 + (t / 4) % 3);
				overlay(Text.literal("Becher werden gemischt" + dots).formatted(Formatting.GOLD));
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.8f + (t / 80.0f));
			}
		};
		return new Game(mult, result, 50, tick);
	}

	// Schatzkisten: vier Kisten mit verschiedenem Inhalt
	private static Game chests(int pick) {
		List<Double> values = new ArrayList<>(List.of(0.0, 0.5, 1.0, 2.4));
		Collections.shuffle(values, RNG);
		final double[] contents = new double[4];
		StringBuilder all = new StringBuilder();
		for (int i = 0; i < 4; i++) {
			contents[i] = values.get(i);
			if (i > 0) all.append(", ");
			all.append(wheelLabel(contents[i]));
		}
		final double mult = contents[pick];
		final Text result = Text.literal("Kiste " + (pick + 1) + ": " + wheelLabel(mult) + "  (Inhalt: " + all + ")").formatted(Formatting.GOLD);

		IntConsumer tick = t -> {
			if (t % 6 == 0 && t < 36) {
				overlay(Text.literal("Kiste " + (pick + 1) + " öffnet sich" + ".".repeat(1 + (t / 6) % 3)).formatted(Formatting.GOLD));
				sound(SoundEvents.BLOCK_LEVER_CLICK, 0.8f + (t / 80.0f));
			}
		};
		return new Game(mult, result, 36, tick);
	}

	// Pferderennen: Fuenf Pferde mit unterschiedlichen Quoten
	static String raceLabel(int i) {
		double o = RACE_ODDS[i];
		return RACE_NAMES[i] + " x" + (o == Math.floor(o) ? String.valueOf((int) o) : String.valueOf(o));
	}

	private static Game race(int horse) {
		double r = RNG.nextDouble();
		int w = RACE_P.length - 1;
		double acc = 0;
		for (int i = 0; i < RACE_P.length; i++) {
			acc += RACE_P[i];
			if (r < acc) {
				w = i;
				break;
			}
		}
		final int winner = w;
		final int[] finish = new int[RACE_NAMES.length];
		for (int i = 0; i < finish.length; i++) {
			finish[i] = (i == winner) ? 60 : 63 + RNG.nextInt(14);
		}
		final double mult = horse == winner ? RACE_ODDS[horse] : 0.0;
		final Text result = Text.literal("Sieger: " + RACE_NAMES[winner] + "  (dein Pferd: " + RACE_NAMES[horse] + ")")
			.formatted(horse == winner ? Formatting.GREEN : Formatting.RED);

		IntConsumer tick = t -> {
			if (t % 4 == 0) {
				int leader = 0;
				double best = -1;
				for (int i = 0; i < finish.length; i++) {
					double progress = (double) t / finish[i];
					if (t < 56) {
						progress += (RNG.nextDouble() - 0.5) * 0.12;
					}
					if (progress > best) {
						best = progress;
						leader = i;
					}
				}
				overlay(Text.literal("Rennen läuft! " + RACE_NAMES[leader] + " führt").formatted(Formatting.GOLD));
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f + (t / 120.0f));
			}
		};
		return new Game(mult, result, 66, tick);
	}

	// Sic Bo (Gross/Klein): drei Wuerfel, ein Dreierpasch verliert immer
	private static Game sicBo(boolean big) {
		final int a = d6();
		final int b = d6();
		final int c = d6();
		final int sum = a + b + c;
		final boolean triple = a == b && b == c;
		final boolean win = !triple && (big ? sum >= 11 : sum <= 10);
		final Text result = Text.literal("Sic Bo: " + a + " + " + b + " + " + c + " = " + sum
			+ (triple ? "  (Dreierpasch – Bank gewinnt)" : "")).formatted(Formatting.WHITE);

		IntConsumer tick = t -> {
			if (t % 3 == 0 && t < 30) {
				overlay(Text.literal("Sic Bo: " + d6() + " + " + d6() + " + " + d6()).formatted(Formatting.WHITE));
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f + (t / 60.0f));
			}
		};
		return new Game(win ? 2.0 : 0.0, result, 30, tick);
	}

	// ---------- Kartenleiter (Hoch/Tief-Serie) ----------

	private static String ladderInfo() {
		int high = 13 - ladderCard;
		int low = ladderCard - 1;
		String h = high > 0 ? "x" + fmt(0.96 * 13.0 / high) : "unmöglich";
		String l = low > 0 ? "x" + fmt(0.96 * 13.0 / low) : "unmöglich";
		return "Karte: " + cardName(ladderCard) + "  |  Hoch: " + h + "  |  Tief: " + l + "  |  aktuell: x" + fmt(ladderMult);
	}

	static int ladderStart(long bet) {
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
		ladderBet = bet;
		ladderMult = 1.0;
		ladderCard = draw();
		ladderActive = true;
		chat("Kartenleiter gestartet! Wird die nächste Karte höher oder tiefer? Gleichstand verliert.", Formatting.AQUA);
		chat(ladderInfo(), Formatting.WHITE);
		return 1;
	}

	static int ladderGuess(boolean high) {
		if (!ladderActive) {
			chat("Keine laufende Kartenleiter. Starte mit /gamble ladder <einsatz>.", Formatting.YELLOW);
			return 0;
		}
		int count = high ? 13 - ladderCard : ladderCard - 1;
		int next = draw();
		boolean win = high ? next > ladderCard : next < ladderCard;
		sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.2f);
		if (!win) {
			ladderActive = false;
			chat("Nächste Karte: " + cardName(next) + " – verloren!", Formatting.RED);
			payoutAndReport(ladderBet, 0.0);
			return 1;
		}
		ladderMult *= 0.96 * 13.0 / count;
		ladderCard = next;
		chat("Richtig! Nächste Karte: " + cardName(next), Formatting.GREEN);
		chat(ladderInfo(), Formatting.WHITE);
		return 1;
	}

	static int ladderCash() {
		if (!ladderActive) {
			chat("Keine laufende Kartenleiter.", Formatting.YELLOW);
			return 0;
		}
		if (ladderMult <= 1.0) {
			chat("Rate erst mindestens einmal!", Formatting.YELLOW);
			return 0;
		}
		ladderActive = false;
		payoutAndReport(ladderBet, ladderMult);
		return 1;
	}

	// ---------- Muenz-Serie ----------

	static int streakStart(long bet) {
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
		streakBet = bet;
		streakMult = 1.0;
		streakCount = 0;
		streakActive = true;
		chat("Münz-Serie gestartet! Jeder Kopf verdoppelt (fast) deinen Gewinn, bei Zahl ist alles weg.", Formatting.AQUA);
		return 1;
	}

	static int streakFlip() {
		if (!streakActive) {
			chat("Keine laufende Münz-Serie. Starte mit /gamble streak <einsatz>.", Formatting.YELLOW);
			return 0;
		}
		if (RNG.nextBoolean()) {
			streakCount++;
			streakMult *= 1.9;
			sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f + 0.05f * streakCount);
			chat("Kopf! Serie: " + streakCount + "  –  x" + fmt(streakMult) + "  (weiter werfen oder auszahlen)", Formatting.GREEN);
		} else {
			streakActive = false;
			chat("Zahl! Die Serie ist vorbei.", Formatting.RED);
			payoutAndReport(streakBet, 0.0);
		}
		return 1;
	}

	static int streakCash() {
		if (!streakActive) {
			chat("Keine laufende Münz-Serie.", Formatting.YELLOW);
			return 0;
		}
		if (streakCount == 0) {
			chat("Wirf erst mindestens einmal!", Formatting.YELLOW);
			return 0;
		}
		streakActive = false;
		payoutAndReport(streakBet, streakMult);
		return 1;
	}

	// ---------- Video-Poker (Jacks or Better) ----------

	private static int rankOf(int card) {
		return card % 13 + 1;
	}

	private static String suitName(int card) {
		if (packActive()) {
			return ICON_SUIT[card / 13];
		}
		return switch (card / 13) {
			case 0 -> "Pik";
			case 1 -> "Herz";
			case 2 -> "Karo";
			default -> "Kreuz";
		};
	}

	private static String pokerCardName(int card) {
		return cardName(rankOf(card)) + " " + suitName(card);
	}

	private static int pokerRank(int[] hand) {
		int[] cnt = new int[14];
		boolean flush = true;
		for (int c : hand) {
			cnt[rankOf(c)]++;
			if (c / 13 != hand[0] / 13) {
				flush = false;
			}
		}
		int pairs = 0;
		int trips = 0;
		int quads = 0;
		boolean highPair = false;
		int distinct = 0;
		int lo = 14;
		int hi = 0;
		for (int r = 1; r <= 13; r++) {
			if (cnt[r] > 0) {
				distinct++;
				lo = Math.min(lo, r);
				hi = Math.max(hi, r);
			}
			if (cnt[r] == 2) {
				pairs++;
				if (r == 1 || r >= 11) highPair = true;
			} else if (cnt[r] == 3) {
				trips++;
			} else if (cnt[r] == 4) {
				quads++;
			}
		}
		boolean straight = false;
		if (distinct == 5) {
			if (hi - lo == 4) {
				straight = true;
			} else if (cnt[1] > 0 && cnt[10] > 0 && cnt[11] > 0 && cnt[12] > 0 && cnt[13] > 0) {
				straight = true; // Ass hoch
			}
		}
		if (straight && flush && cnt[1] > 0 && cnt[13] > 0) return 9;
		if (straight && flush) return 8;
		if (quads > 0) return 7;
		if (trips > 0 && pairs == 1) return 6;
		if (flush) return 5;
		if (straight) return 4;
		if (trips > 0) return 3;
		if (pairs == 2) return 2;
		if (highPair) return 1;
		return 0;
	}

	static int pokerDeal(long bet) {
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
		pokerBet = bet;
		pokerDeck.clear();
		for (int i = 0; i < 52; i++) {
			pokerDeck.add(i);
		}
		Collections.shuffle(pokerDeck, RNG);
		for (int i = 0; i < 5; i++) {
			pokerHand[i] = pokerDeck.remove(0);
			pokerHold[i] = false;
		}
		pokerState = 1;
		sound(SoundEvents.BLOCK_LEVER_CLICK, 1.2f);
		chat("Video-Poker: Klicke Karten zum Halten, dann \"Tauschen\" (/gamble hold <1-5>, /gamble draw).", Formatting.AQUA);
		chat("Aktuell: " + POKER_NAMES[pokerRank(pokerHand)], Formatting.WHITE);
		return 1;
	}

	static int pokerToggle(int index) {
		if (pokerState != 1) {
			chat("Keine laufende Poker-Runde. Teile zuerst aus.", Formatting.YELLOW);
			return 0;
		}
		pokerHold[index] = !pokerHold[index];
		sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, pokerHold[index] ? 1.4f : 1.0f);
		return 1;
	}

	static int pokerDraw() {
		if (pokerState != 1) {
			chat("Keine laufende Poker-Runde. Teile zuerst aus.", Formatting.YELLOW);
			return 0;
		}
		for (int i = 0; i < 5; i++) {
			if (!pokerHold[i]) {
				pokerHand[i] = pokerDeck.remove(0);
			}
		}
		pokerState = 2;
		int rank = pokerRank(pokerHand);
		chat("Ergebnis: " + POKER_NAMES[rank], rank > 0 ? Formatting.GREEN : Formatting.GRAY);
		payoutAndReport(pokerBet, POKER_PAY[rank]);
		return 1;
	}

	static Text pokerCardLabel(int i) {
		if (pokerState == 0) {
			return Text.literal("Karte " + (i + 1) + ": –").formatted(Formatting.GRAY);
		}
		String text = "Karte " + (i + 1) + ": " + pokerCardName(pokerHand[i]);
		if (pokerState == 1 && pokerHold[i]) {
			return Text.literal(text + "   [HALTEN]").formatted(Formatting.GREEN);
		}
		return Text.literal(text).formatted(Formatting.WHITE);
	}

	// ---------- Blackjack-Anzeige und Verdoppeln ----------

	static Text bjPlayerText() {
		if (bjPlayer.isEmpty()) {
			return Text.literal("Deine Karten: –").formatted(Formatting.GRAY);
		}
		return Text.literal("Du: " + handText(bjPlayer)).formatted(Formatting.WHITE);
	}

	static Text bjDealerText() {
		if (bjDealer.isEmpty()) {
			return Text.literal("Dealer: –").formatted(Formatting.GRAY);
		}
		if (bjActive) {
			return Text.literal("Dealer: " + cardName(bjDealer.get(0)) + ", ?").formatted(Formatting.GRAY);
		}
		return Text.literal("Dealer: " + handText(bjDealer)).formatted(Formatting.GRAY);
	}

	static int bjDouble() {
		if (!bjActive) {
			chat("Keine laufende Blackjack-Runde. Starte mit /gamble blackjack <einsatz>.", Formatting.YELLOW);
			return 0;
		}
		if (bjPlayer.size() != 2) {
			chat("Verdoppeln geht nur mit den ersten zwei Karten.", Formatting.YELLOW);
			return 0;
		}
		if (balance < bjBet) {
			chat("Nicht genug Spielgeld zum Verdoppeln.", Formatting.RED);
			return 0;
		}
		balance -= bjBet;
		bjBet *= 2;
		save();
		bjPlayer.add(draw());
		sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.3f);
		chat("Verdoppelt! Du ziehst: " + cardName(bjPlayer.get(bjPlayer.size() - 1)) + " → " + handText(bjPlayer), Formatting.WHITE);
		if (total(bjPlayer) > 21) {
			bjActive = false;
			chat("Überkauft!", Formatting.RED);
			payoutAndReport(bjBet, 0.0);
		} else {
			dealerPlay();
		}
		return 1;
	}

	// ---------- Memory (mit Texturpack besonders huebsch) ----------

	private static double memoryMultiplier(int moves) {
		if (moves <= 7) return 4.0;
		if (moves <= 9) return 2.5;
		if (moves <= 11) return 1.5;
		if (moves <= 13) return 1.0;
		if (moves <= 16) return 0.5;
		return 0.0;
	}

	static int memoryStart(long bet) {
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
		memoryBet = bet;
		List<Integer> deck = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			deck.add(i);
			deck.add(i);
		}
		Collections.shuffle(deck, RNG);
		for (int i = 0; i < 12; i++) {
			memoryCards[i] = deck.get(i);
			memoryMatched[i] = false;
		}
		memoryFirst = -1;
		memorySecond = -1;
		memoryMoves = 0;
		memoryPairs = 0;
		memoryHide = 0;
		memoryActive = true;
		chat("Memory gestartet! Finde alle 6 Paare mit möglichst wenigen Zügen (bis 7 Züge = x4).", Formatting.AQUA);
		return 1;
	}

	static int memoryFlip(int index) {
		if (!memoryActive) {
			chat("Keine laufende Memory-Runde. Starte mit \"Neue Runde\".", Formatting.YELLOW);
			return 0;
		}
		if (memoryHide > 0 || memoryMatched[index] || index == memoryFirst) {
			return 0;
		}
		sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.2f);
		if (memoryFirst < 0) {
			memoryFirst = index;
			return 1;
		}
		memorySecond = index;
		memoryMoves++;
		if (memoryCards[memoryFirst] == memoryCards[memorySecond]) {
			memoryMatched[memoryFirst] = true;
			memoryMatched[memorySecond] = true;
			memoryFirst = -1;
			memorySecond = -1;
			memoryPairs++;
			sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f + 0.1f * memoryPairs);
			if (memoryPairs >= 6) {
				memoryActive = false;
				chat("Alle Paare gefunden in " + memoryMoves + " Zügen!", Formatting.GREEN);
				payoutAndReport(memoryBet, memoryMultiplier(memoryMoves));
			} else {
				chat("Paar gefunden! (" + memoryPairs + "/6, Züge: " + memoryMoves + ")", Formatting.GREEN);
			}
		} else {
			memoryHide = 20;
			chat("Kein Paar. Züge: " + memoryMoves, Formatting.GRAY);
		}
		return 1;
	}

	static Text memoryCellLabel(int i) {
		boolean shown = memoryMatched[i] || i == memoryFirst || i == memorySecond;
		boolean pack = packActive();
		if (!shown) {
			return pack ? Text.literal(backIcon()) : Text.literal("?").formatted(Formatting.GRAY);
		}
		int symbol = memoryCards[i];
		if (pack) {
			return Text.literal(ICON_BIG[symbol]);
		}
		return Text.literal(SYMBOLS[symbol]).formatted(SYMBOL_COLORS[symbol]);
	}

	private static int packInfo() {
		if (packActive()) {
			chat("Das Fun-Gamble-Texturpack ist aktiv. Slots, Würfel, Karten, Mines und Memory zeigen Bilder.", Formatting.GREEN);
		} else {
			chat("Kein Texturpack erkannt. Kopiere FunGamble-Texturpack.zip in den Ordner resourcepacks und aktiviere es", Formatting.YELLOW);
			chat("unter Optionen → Ressourcenpakete. Dann zeigen Slots, Würfel, Karten, Mines und Memory Bilder.", Formatting.YELLOW);
		}
		return 1;
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
		if (reflexState != 0) {
			tickReflex();
		}
		if (memoryHide > 0) {
			memoryHide--;
			if (memoryHide == 0) {
				memoryFirst = -1;
				memorySecond = -1;
			}
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
			showWinBanner(payout - bet);
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
		chatOnly(balanceText());

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
		chat("Karten: blackjack (hit, stand, double) | poker (hold, draw) | baccarat | ladder (up, down, laddercash)", Formatting.WHITE);
		chat("Würfel: dice100 | craps | sicbo | lotto | cups | chests | race | streak (flip, streakcash)", Formatting.WHITE);
		chat("Memory: /gamble memory <einsatz> | pick <1-12>   Texturpack-Info: /gamble pack", Formatting.WHITE);
		chat("Menüs: /gamble menu <automaten|wuerfel|karten|risiko|sonstiges|rennen|blackjack|poker|mines|shop>", Formatting.WHITE);
		chat("Mini-Games: /gamble rps | plinko | scratch | reflex (go) | tower (door, towercash) | mines | daily", Formatting.WHITE);
		chat("/gamble shop (Fenster)  |  buy <item>  |  equip <item>  |  unequip <title|sound|color|frame|banner|back>", Formatting.WHITE);
		chat("/gamble reset", Formatting.WHITE);
		return 1;
	}

	private static int showBalance() {
		chatOnly(balanceText());
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

	static CasinoScreen createScreen(String name) {
		return switch (name) {
			case "automaten" -> new SlotsScreen();
			case "wuerfel" -> new DiceScreen();
			case "karten" -> new CardsScreen();
			case "risiko" -> new RiskScreen();
			case "sonstiges" -> new MiscScreen();
			case "rennen" -> new RaceScreen();
			case "blackjack" -> new BlackjackScreen();
			case "poker" -> new PokerScreen();
			case "mines" -> new MinesScreen();
			case "memory" -> new MemoryScreen();
			case "shop" -> new ShopScreen();
			default -> new GambleScreen();
		};
	}

	private static int openScreenByName(String name) {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.send(() -> mc.setScreen(createScreen(name)));
		return 1;
	}

	static long getBalance() {
		return balance;
	}

	static Text statusLine1() {
		if (running != null || crashActive || reflexState != 0) {
			return lastOverlay;
		}
		return recentLine(1);
	}

	static Text statusLine2() {
		if (running != null || crashActive || reflexState != 0) {
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
	static void uiPlinko(long bet) { startGame(bet, FunGambleClient::plinko); }
	static void uiScratch(long bet) { startGame(bet, FunGambleClient::scratch); }
	static void uiRps(long bet, int pick) { startGame(bet, () -> rps(pick)); }
	static void uiReflex(long bet) { startReflex(bet); }
	static void uiGo() { reflexGoNow(); }
	static void uiTower(long bet) { startTower(bet); }
	static void uiDoor(int door) { towerDoor(door); }
	static void uiTowerCash() { towerCash(); }
	static void uiMinesStart(long bet) { minesStart(bet); }
	static void uiMinesReveal(int index) { minesReveal(index); }
	static void uiMinesCash() { minesCash(); }
	static void uiDaily() { daily(); }
	static void uiDice100(long bet, boolean over) { startGame(bet, () -> dice100(over, DICE100_THRESHOLDS[dice100Index])); }
	static void uiCycleDice100() { dice100Index = (dice100Index + 1) % DICE100_THRESHOLDS.length; }
	static void uiLotto(long bet, int n) { startGame(bet, () -> lotto(n)); }
	static void uiBaccarat(long bet, int side) { startGame(bet, () -> baccarat(side)); }
	static void uiCraps(long bet) { startGame(bet, FunGambleClient::craps); }
	static void uiCups(long bet, int pick) { startGame(bet, () -> cups(pick)); }
	static void uiChests(long bet, int pick) { startGame(bet, () -> chests(pick)); }
	static void uiRace(long bet, int horse) { startGame(bet, () -> race(horse)); }
	static void uiSicBo(long bet, boolean big) { startGame(bet, () -> sicBo(big)); }
	static void uiPokerDeal(long bet) { pokerDeal(bet); }
	static void uiPokerHold(int i) { pokerToggle(i); }
	static void uiPokerDraw() { pokerDraw(); }
	static void uiLadderStart(long bet) { ladderStart(bet); }
	static void uiLadderGuess(boolean high) { ladderGuess(high); }
	static void uiLadderCash() { ladderCash(); }
	static void uiStreakStart(long bet) { streakStart(bet); }
	static void uiStreakFlip() { streakFlip(); }
	static void uiStreakCash() { streakCash(); }
	static void uiBjDouble() { bjDouble(); }
	static void uiMemoryStart(long bet) { memoryStart(bet); }
	static void uiMemoryFlip(int i) { memoryFlip(i); }

	static Text dice100ThresholdLabel() {
		return Text.literal("Schwelle: " + DICE100_THRESHOLDS[dice100Index] + "  (klicken zum Ändern)").formatted(Formatting.AQUA);
	}

	static Text dice100Label(boolean over) {
		int th = DICE100_THRESHOLDS[dice100Index];
		return Text.literal((over ? "Über " : "Bis ") + th + "  (x" + fmt(dice100Multiplier(over, th)) + ")");
	}
	static void uiCashout() { cashout(); }
	static void uiHit() { bjHit(); }
	static void uiStand() { bjStand(); }

	static Text shopLabel(Item item) {
		String status;
		Formatting color;
		if (item.type().equals("ring")) {
			status = item.price() + " Spielgeld (du hast: " + rings + ")";
			color = Formatting.WHITE;
		} else if (isActive(item)) {
			status = "[aktiv]";
			color = Formatting.GREEN;
		} else if (owned.contains(item.id())) {
			status = item.type().equals("hat") ? "[gekauft – klicken zum Holen]" : "[gekauft]";
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
		} else if (isActive(item)) {
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

	// ---------- Kosmetik ----------

	private static final Formatting[] RAINBOW = {Formatting.RED, Formatting.GOLD, Formatting.YELLOW, Formatting.GREEN,
		Formatting.AQUA, Formatting.BLUE, Formatting.LIGHT_PURPLE};
	private static final Formatting[] FIRE = {Formatting.RED, Formatting.GOLD, Formatting.YELLOW};

	static String activeFor(String type) {
		return switch (type) {
			case "title" -> activeTitle;
			case "sound" -> activeSound;
			case "color" -> activeColor;
			case "frame" -> activeFrame;
			case "banner" -> activeBanner;
			case "back" -> activeBack;
			default -> "";
		};
	}

	private static void setActive(String type, String id) {
		switch (type) {
			case "title" -> activeTitle = id;
			case "sound" -> activeSound = id;
			case "color" -> activeColor = id;
			case "frame" -> activeFrame = id;
			case "banner" -> activeBanner = id;
			case "back" -> activeBack = id;
			default -> { }
		}
	}

	static boolean isActive(Item item) {
		return !item.type().equals("ring") && item.id().equals(activeFor(item.type()));
	}

	static List<Item> itemsOfType(String type) {
		List<Item> out = new ArrayList<>();
		for (Item item : ITEMS) {
			if (item.type().equals(type)) {
				out.add(item);
			}
		}
		return out;
	}

	/** Faerbt jeden Buchstaben der Reihe nach mit den Farben der Palette. */
	private static Text patternText(String s, Formatting[] palette, int offset) {
		var t = Text.literal("");
		for (int i = 0; i < s.length(); i++) {
			t.append(Text.literal(String.valueOf(s.charAt(i))).formatted(palette[Math.floorMod(i + offset, palette.length)]));
		}
		return t;
	}

	private static Formatting themeColor() {
		return switch (activeColor) {
			case "c_smaragd" -> Formatting.GREEN;
			case "c_ozean" -> Formatting.AQUA;
			case "c_rubin" -> Formatting.RED;
			case "c_violett" -> Formatting.LIGHT_PURPLE;
			default -> Formatting.GOLD;
		};
	}

	/** Text im gewaehlten Farbstil (der Regenbogen laeuft animiert durch). */
	private static Text styled(String s) {
		if (activeColor.equals("c_regenbogen")) {
			int offset = (int) ((System.currentTimeMillis() / 150) % RAINBOW.length);
			return patternText(s, RAINBOW, offset);
		}
		return Text.literal(s).formatted(themeColor());
	}

	private static String[] frameParts() {
		return switch (activeFrame) {
			case "f_tilde" -> new String[]{"~ ", " ~"};
			case "f_sterne" -> new String[]{"* ", " *"};
			case "f_pfeile" -> new String[]{"<< ", " >>"};
			case "f_klammern" -> new String[]{"[[ ", " ]]"};
			case "f_linien" -> new String[]{"=== ", " ==="};
			default -> new String[]{"", ""};
		};
	}

	private static void showWinBanner(long win) {
		int offset = (int) ((System.currentTimeMillis() / 100) % RAINBOW.length);
		Text banner;
		if (activeBanner.equals("b_konfetti")) {
			banner = patternText("*** GEWINN +" + win + " ***", RAINBOW, offset);
		} else if (activeBanner.equals("b_gold")) {
			banner = Text.literal("$$$ GEWINN +" + win + " $$$").formatted(Formatting.GOLD);
		} else if (activeBanner.equals("b_feuer")) {
			banner = patternText("^^^ GEWINN +" + win + " ^^^", FIRE, offset);
		} else {
			return;
		}
		overlay(banner);
		chat(banner);
	}

	private static String backIcon() {
		return switch (activeBack) {
			case "k_rot" -> "\uE116";
			case "k_gold" -> "\uE117";
			default -> ICON_BIG_BACK;
		};
	}

	// ---------- Shop ----------

	private static Text balanceText() {
		Item title = findItem(activeTitle);
		String[] frame = frameParts();
		String prefix = title != null ? "[" + title.name() + "] " : "";
		return styled(frame[0] + prefix + "Kontostand: " + balance + " Spielgeld" + frame[1]);
	}

	/** Holt den Hut per /give (Cheats oder OP noetig). Der Hut ist ein Papier mit Hut-Modell aus dem Texturpack. */
	private static int giveHat(String id) {
		Item item = findItem(id);
		if (item == null || !item.type().equals("hat")) {
			chat("Diesen Hut gibt es nicht.", Formatting.RED);
			return 0;
		}
		if (!owned.contains(id)) {
			chat("Kaufe den Hut zuerst im Shop.", Formatting.YELLOW);
			return 0;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.player.networkHandler == null) {
			chat("Du musst in einer Welt sein, um den Hut zu holen.", Formatting.YELLOW);
			return 0;
		}
		String command = "give @s minecraft:paper[minecraft:item_model=\"fungamble:" + id + "\","
			+ "minecraft:equippable={slot:\"head\"},minecraft:max_stack_size=1,"
			+ "minecraft:custom_name={text:\"" + item.name() + "\",italic:false,color:\"gold\"}]";
		mc.player.networkHandler.sendChatCommand(command);
		chat("Hut angefordert: " + item.name() + ". Rechtsklick mit dem Item in der Hand setzt ihn auf (Cheats/OP und Texturpack nötig).", Formatting.AQUA);
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
			if (item.type().equals("hat")) {
				chat("Gekauft: " + item.name(), Formatting.GREEN);
			} else {
				setActive(item.type(), id);
				chat("Gekauft und ausgerüstet: " + item.name(), Formatting.GREEN);
			}
		}
		save();
		sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
		chatOnly(balanceText());
		if (item.type().equals("hat")) {
			giveHat(id);
		}
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
		if (item.type().equals("hat")) {
			return giveHat(id);
		}
		setActive(item.type(), id);
		save();
		chat("Ausgerüstet: " + item.name(), Formatting.GREEN);
		return 1;
	}

	private static int unequip(String type) {
		setActive(type, "");
		save();
		chat("Abgelegt (Standard).", Formatting.YELLOW);
		return 1;
	}

	static void uiUnequip(String type) {
		unequip(type);
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
			activeColor = p.getProperty("color", "");
			activeFrame = p.getProperty("frame", "");
			activeBanner = p.getProperty("banner", "");
			activeBack = p.getProperty("back", "");
			rings = Integer.parseInt(p.getProperty("rings", "0"));
			lastDaily = p.getProperty("lastDaily", "");
			dailyStreak = Integer.parseInt(p.getProperty("streak", "0"));
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
			p.setProperty("color", activeColor);
			p.setProperty("frame", activeFrame);
			p.setProperty("banner", activeBanner);
			p.setProperty("back", activeBack);
			p.setProperty("rings", Integer.toString(rings));
			p.setProperty("lastDaily", lastDaily);
			p.setProperty("streak", Integer.toString(dailyStreak));
			StringWriter sw = new StringWriter();
			p.store(sw, null);
			Files.writeString(saveFile, sw.toString());
		} catch (Exception ignored) {
		}
	}
}
