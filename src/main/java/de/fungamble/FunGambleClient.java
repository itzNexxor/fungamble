package de.fungamble;

import com.mojang.brigadier.arguments.LongArgumentType;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.IntConsumer;

/**
 * Reine Client-Mod: Alles passiert nur bei dir lokal.
 * Der Server (und andere Spieler) bekommen davon nichts mit.
 * Spielgeld ist nicht echt und hat nichts mit Items im Spiel zu tun.
 *
 * Version 1.1: mit Animationen (Action-Bar ueber der Hotbar) und Sounds.
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

	// Laufende Runde (Animation)
	private static Game running = null;
	private static long runningBet = 0;
	private static int runningTick = 0;

	/** Ergebnis steht schon fest, die Animation zeigt es nur nach und nach. */
	private record Game(double multiplier, Text result, int duration, IntConsumer tick) {}

	@Override
	public void onInitializeClient() {
		saveFile = FabricLoader.getInstance().getConfigDir().resolve("fungamble.txt");
		load();

		ClientTickEvents.END_CLIENT_TICK.register(client -> tickAnimation());

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("gamble")
				.executes(ctx -> help())
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
					.then(ClientCommandManager.literal("gruen").executes(ctx -> play(ctx, () -> roulette("gruen")))))));
		});
	}

	// Einsatz-Argument: /gamble <spiel> <einsatz> ...
	private static RequiredArgumentBuilder<FabricClientCommandSource, Long> bet() {
		return ClientCommandManager.argument("einsatz", LongArgumentType.longArg(1));
	}

	// ---------- Hilfsfunktionen ----------

	private static void chat(Text text) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			mc.player.sendMessage(text, false); // nur lokal sichtbar
		}
	}

	private static void overlay(Text text) {
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

	// ---------- Spiele (mit Animation) ----------

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
		final int duration = 60;

		IntConsumer tick = t -> {
			// Das Rad wird langsamer: erst alle 2 Ticks, dann 3, dann 5
			boolean show = (t < 30 && t % 2 == 0) || (t >= 30 && t < 45 && t % 3 == 0) || (t >= 45 && t % 5 == 0);
			if (show) {
				int shown = (t >= 55) ? n : RNG.nextInt(37);
				overlay(Text.literal("Rad dreht sich... " + shown).formatted(rouletteFormat(shown)));
				float pitch = 1.8f - (t / 60.0f);
				sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, pitch);
			}
		};
		return new Game(mult, result, duration, tick);
	}

	// ---------- Ablauf ----------

	private static int play(CommandContext<FabricClientCommandSource> ctx, Supplier<Game> gameSupplier) {
		if (running != null) {
			chat(Text.literal("Warte kurz, die laufende Runde ist noch nicht fertig!").formatted(Formatting.YELLOW));
			return 0;
		}
		long bet = LongArgumentType.getLong(ctx, "einsatz");
		if (bet > balance) {
			chat(Text.literal("Nicht genug Spielgeld! Du hast nur " + balance + ".").formatted(Formatting.RED));
			return 0;
		}

		balance -= bet;
		save();
		runningBet = bet;
		runningTick = 0;
		running = gameSupplier.get();
		return 1;
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

		long payout = Math.round(bet * game.multiplier());
		balance += payout;
		save();

		overlay(game.result());
		chat(game.result());

		if (payout > bet) {
			chat(Text.literal("Gewonnen! +" + (payout - bet) + " Spielgeld").formatted(Formatting.GREEN));
			if (game.multiplier() >= 10.0) {
				sound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f);
			} else {
				sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f);
			}
		} else if (payout == bet) {
			chat(Text.literal("Unentschieden – Einsatz zurück.").formatted(Formatting.YELLOW));
			sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f);
		} else {
			chat(Text.literal("Verloren! -" + (bet - payout) + " Spielgeld").formatted(Formatting.RED));
			sound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f);
		}
		chat(Text.literal("Kontostand: " + balance).formatted(Formatting.GOLD));

		if (balance <= 0) {
			chat(Text.literal("Pleite! Mit /gamble reset startest du neu.").formatted(Formatting.DARK_RED));
		}
	}

	private static int help() {
		chat(Text.literal("=== Fun Gamble (nur für dich sichtbar) ===").formatted(Formatting.GOLD));
		chat(Text.literal("/gamble balance"));
		chat(Text.literal("/gamble coinflip <einsatz> <kopf|zahl>"));
		chat(Text.literal("/gamble slots <einsatz>"));
		chat(Text.literal("/gamble dice <einsatz>"));
		chat(Text.literal("/gamble roulette <einsatz> <rot|schwarz|gruen>"));
		chat(Text.literal("/gamble reset"));
		return 1;
	}

	private static int showBalance() {
		chat(Text.literal("Kontostand: " + balance + " Spielgeld").formatted(Formatting.GOLD));
		return 1;
	}

	private static int reset() {
		if (running != null) {
			chat(Text.literal("Warte, bis die laufende Runde fertig ist.").formatted(Formatting.YELLOW));
			return 0;
		}
		balance = START_MONEY;
		save();
		chat(Text.literal("Zurückgesetzt auf " + START_MONEY + " Spielgeld.").formatted(Formatting.YELLOW));
		return 1;
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
