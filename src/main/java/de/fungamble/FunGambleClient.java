package de.fungamble;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Reine Client-Mod: Alles passiert nur bei dir lokal.
 * Der Server (und andere Spieler) bekommen davon nichts mit.
 * Spielgeld ist nicht echt und hat nichts mit Items im Spiel zu tun.
 */
public class FunGambleClient implements ClientModInitializer {

	private static final long START_MONEY = 1000;
	private static final Random RNG = new Random();
	private static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
	private static final String[] SYMBOLS = {"Kirsche", "Zitrone", "Glocke", "Stern", "Diamant", "7"};
	private static final Formatting[] SYMBOL_COLORS = {
		Formatting.RED, Formatting.YELLOW, Formatting.GOLD, Formatting.AQUA, Formatting.BLUE, Formatting.DARK_RED
	};

	private static long balance = START_MONEY;
	private static Path saveFile;

	private record Result(double multiplier, Text description) {}

	@Override
	public void onInitializeClient() {
		saveFile = FabricLoader.getInstance().getConfigDir().resolve("fungamble.txt");
		load();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("gamble")
				.executes(ctx -> help(ctx))
				.then(ClientCommandManager.literal("balance").executes(ctx -> showBalance(ctx)))
				.then(ClientCommandManager.literal("reset").executes(ctx -> reset(ctx)))
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
					.then(ClientCommandManager.literal("gruen").executes(ctx -> play(ctx, () -> roulette("gruen")))))));
		});
	}

	// Einsatz-Argument: /gamble <spiel> <einsatz> ...
	private static RequiredArgumentBuilder<FabricClientCommandSource, Long> bet() {
		return ClientCommandManager.argument("einsatz", LongArgumentType.longArg(1));
	}

	// ---------- Spiele ----------

	private static Result coinflip(boolean guessHeads) {
		boolean heads = RNG.nextBoolean();
		Text t = Text.literal("Münze: " + (heads ? "Kopf" : "Zahl")).formatted(Formatting.YELLOW);
		return new Result(heads == guessHeads ? 2.0 : 0.0, t);
	}

	private static Result slots() {
		int a = RNG.nextInt(SYMBOLS.length);
		int b = RNG.nextInt(SYMBOLS.length);
		int c = RNG.nextInt(SYMBOLS.length);

		Text reels = Text.literal("[ ")
			.append(Text.literal(SYMBOLS[a]).formatted(SYMBOL_COLORS[a]))
			.append(" | ")
			.append(Text.literal(SYMBOLS[b]).formatted(SYMBOL_COLORS[b]))
			.append(" | ")
			.append(Text.literal(SYMBOLS[c]).formatted(SYMBOL_COLORS[c]))
			.append(" ]");

		double mult;
		if (a == b && b == c) {
			mult = (a == SYMBOLS.length - 1) ? 25.0 : 10.0; // 7-7-7 = Jackpot
		} else if (a == b || b == c || a == c) {
			mult = 1.5;
		} else {
			mult = 0.0;
		}
		return new Result(mult, reels);
	}

	// Du und der Dealer würfeln je einen W6. Höher gewinnt, Gleichstand = Einsatz zurück.
	private static Result dice() {
		int you = RNG.nextInt(6) + 1;
		int dealer = RNG.nextInt(6) + 1;
		Text t = Text.literal("Du: " + you + "  |  Dealer: " + dealer).formatted(Formatting.WHITE);
		double mult = you > dealer ? 2.0 : (you == dealer ? 1.0 : 0.0);
		return new Result(mult, t);
	}

	private static Result roulette(String pick) {
		int n = RNG.nextInt(37); // 0-36
		String color = n == 0 ? "gruen" : (RED.contains(n) ? "rot" : "schwarz");
		Formatting f = color.equals("rot") ? Formatting.RED : (color.equals("gruen") ? Formatting.GREEN : Formatting.GRAY);
		Text t = Text.literal("Kugel landet auf " + n + " (" + color + ")").formatted(f);
		double mult = 0.0;
		if (pick.equals(color)) {
			mult = color.equals("gruen") ? 14.0 : 2.0;
		}
		return new Result(mult, t);
	}

	// ---------- Ablauf ----------

	private static int play(CommandContext<FabricClientCommandSource> ctx, Supplier<Result> game) {
		long bet = LongArgumentType.getLong(ctx, "einsatz");
		if (bet > balance) {
			msg(ctx, Text.literal("Nicht genug Spielgeld! Du hast nur " + balance + ".").formatted(Formatting.RED));
			return 0;
		}

		balance -= bet;
		Result r = game.get();
		long payout = Math.round(bet * r.multiplier());
		balance += payout;
		save();

		msg(ctx, r.description());
		if (payout > bet) {
			msg(ctx, Text.literal("Gewonnen! +" + (payout - bet) + " Spielgeld").formatted(Formatting.GREEN));
		} else if (payout == bet) {
			msg(ctx, Text.literal("Unentschieden – Einsatz zurück.").formatted(Formatting.YELLOW));
		} else {
			msg(ctx, Text.literal("Verloren! -" + (bet - payout) + " Spielgeld").formatted(Formatting.RED));
		}
		msg(ctx, Text.literal("Kontostand: " + balance).formatted(Formatting.GOLD));

		if (balance <= 0) {
			msg(ctx, Text.literal("Pleite! Mit /gamble reset startest du neu.").formatted(Formatting.DARK_RED));
		}
		return 1;
	}

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		msg(ctx, Text.literal("=== Fun Gamble (nur für dich sichtbar) ===").formatted(Formatting.GOLD));
		msg(ctx, Text.literal("/gamble balance"));
		msg(ctx, Text.literal("/gamble coinflip <einsatz> <kopf|zahl>"));
		msg(ctx, Text.literal("/gamble slots <einsatz>"));
		msg(ctx, Text.literal("/gamble dice <einsatz>"));
		msg(ctx, Text.literal("/gamble roulette <einsatz> <rot|schwarz|gruen>"));
		msg(ctx, Text.literal("/gamble reset"));
		return 1;
	}

	private static int showBalance(CommandContext<FabricClientCommandSource> ctx) {
		msg(ctx, Text.literal("Kontostand: " + balance + " Spielgeld").formatted(Formatting.GOLD));
		return 1;
	}

	private static int reset(CommandContext<FabricClientCommandSource> ctx) {
		balance = START_MONEY;
		save();
		msg(ctx, Text.literal("Zurückgesetzt auf " + START_MONEY + " Spielgeld.").formatted(Formatting.YELLOW));
		return 1;
	}

	// sendFeedback zeigt die Nachricht nur lokal im eigenen Chat an – sie wird nie an den Server gesendet.
	private static void msg(CommandContext<FabricClientCommandSource> ctx, Text text) {
		ctx.getSource().sendFeedback(text);
	}

	// ---------- Speichern ----------

	private static void load() {
		try {
			if (Files.exists(saveFile)) {
				balance = Long.parseLong(Files.readString(saveFile).trim());
			}
		} catch (Exception e) {
			balance = START_MONEY;
		}
	}

	private static void save() {
		try {
			Files.writeString(saveFile, Long.toString(balance));
		} catch (Exception ignored) {
		}
	}
}
