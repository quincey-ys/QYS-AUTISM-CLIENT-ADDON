package com.quinceyys.utils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatGameGuesserModule {
    private static final Path CONFIG = Path.of("config", "qys-chat-game-guesser.properties");

    private static final Pattern MATH = Pattern.compile(
            "(?i)(-?\\d+(?:\\.\\d+)?)\\s*(\\+|plus|add|\\-|−|minus|subtract|\\*|x|×|times|multiply|/|÷|divide)\\s*(-?\\d+(?:\\.\\d+)?)"
    );
    private static final Pattern SCRAMBLE = Pattern.compile(
            "(?i)(?:unscramble|unjumble|scramble(?:d)? word|rearrange(?: the letters)?)(?:\s+(?:this|the|following))?(?:\s+word)?\s*[:：-]?\s*([a-z]{3,18})\b"
    );
    private static final Pattern REVERSE = Pattern.compile(
            "(?i)(?:reverse|backwards|backward)(?: this| the| that)?(?: word| text|string)?\\s*[:：-]\\s*([\\p{L}\\p{N}]+)\\b"
    );
    private static final Pattern TYPE = Pattern.compile(
            "(?i)(?:type|write|repeat)(?: this| the following| the word| after me)?\\s*[:：-]\\s*(.{1,48}?)\\s*[.!?]*$"
    );

    private static final List<String> WORDS = List.of(
            "minecraft", "creeper", "diamond", "netherite", "obsidian", "enderdragon",
            "enderman", "skeleton", "zombie", "villager", "elytra", "totem", "trident",
            "redstone", "lapis", "emerald", "amethyst", "copper", "iron", "gold",
            "pickaxe", "sword", "shield", "crossbow", "bow", "mace", "spear",
            "enchantment", "anvil", "beacon", "conduit", "shulker", "phantom",
            "blaze", "ghast", "wither", "warden", "breeze", "sniffer", "allay",
            "ravager", "pillager", "witch", "slime", "magma", "strider", "hoglin",
            "piglin", "nether", "end", "overworld", "stronghold", "mineshaft",
            "village", "monument", "ancientcity", "trialchambers", "bastion",
            "diamondblock", "crafting", "furnace", "hopper", "piston", "observer",
            "comparator", "repeater", "torch", "lantern", "campfire", "respawnanchor",
            "glowstone", "cryingobsidian", "endcrystal", "experience", "lapis lazuli",
            "apple", "bread", "cookie", "carrot", "potato", "pumpkin", "melon",
            "chicken", "cow", "sheep", "pig", "horse", "donkey", "wolf", "cat",
            "ocelot", "parrot", "turtle", "dolphin", "axolotl", "frog", "camel",
            "sniffer", "bamboo", "mangrove", "cherry", "palegarden", "deepdark",
            "netherite", "firework", "elytra", "totemofundying", "goldenapple",
            "enchantedgoldenapple", "experiencebottle", "enderpearl", "eyeofender",
            "dragonbreath", "shulkerbox", "enderchest", "bedrock", "craftingtable",
            "redstoneblock", "sticky piston", "bookshelf", "enchantingtable",
            "water", "lava", "snow", "ice", "stone", "dirt", "grass", "sand",
            "gravel", "quartz", "prismarine", "terracotta", "concrete", "wool",
            "cobblestone", "deepslate", "tuff", "calcite", "dripstone", "sculk",
            "experience", "survival", "creative", "adventure", "spectator"
    );

    private static final Map<String, List<String>> WORD_SIGNATURES = buildWordSignatures();
    private static final Map<String, String> TRIVIA = buildTrivia();

    public static boolean enabled = false;
    public static boolean autoSend = false;
    public static boolean solveMath = true;
    public static boolean solveScramble = true;
    public static boolean solveReverse = true;
    public static boolean solveTyping = true;
    public static boolean solveTrivia = true;
    public static boolean onlyRecognizedPrompts = true;
    public static int answerDelayMs = 900;
    public static int minimumSendIntervalMs = 1800;

    private static String pendingAnswer;
    private static String lastPrompt;
    private static long answerDueAt;
    private static long lastAnswerAt;

    private ChatGameGuesserModule() {}

    public static void initialize() {
        loadConfig();

        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                onIncomingMessage(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, boundChatType, timestamp) ->
                onIncomingMessage(message.getString()));

        ClientTickEvents.END_CLIENT_TICK.register(ChatGameGuesserModule::onEndTick);
    }

    private static void onIncomingMessage(String rawMessage) {
        if (!enabled || rawMessage == null || rawMessage.isBlank()) return;

        String message = stripFormatting(rawMessage).trim();
        if (message.isEmpty() || (onlyRecognizedPrompts && !looksLikeGamePrompt(message))) return;

        String answer = solve(message);
        if (answer == null || answer.isBlank()) return;

        long now = System.currentTimeMillis();
        if (message.equals(lastPrompt) && now - lastAnswerAt < 5000L) return;

        lastPrompt = message;
        pendingAnswer = answer;
        answerDueAt = now + answerDelayMs;
    }

    private static void onEndTick(Minecraft client) {
        if (pendingAnswer == null) return;
        if (client.player == null || client.level == null || !client.player.isAlive()) {
            pendingAnswer = null;
            return;
        }

        long now = System.currentTimeMillis();
        if (now < answerDueAt) return;

        String answer = pendingAnswer;
        pendingAnswer = null;

        if (now - lastAnswerAt < minimumSendIntervalMs) return;
        lastAnswerAt = now;

        if (autoSend) {
            client.player.connection.sendChat(answer);
        } else {
            client.player.sendOverlayMessage(
                    Component.literal("[QYS Guess] " + answer + "  (Auto Send is OFF)")
            );
        }
    }

    private static String solve(String message) {
        if (solveMath) {
            String answer = solveMath(message);
            if (answer != null) return answer;
        }
        if (solveScramble) {
            String answer = solveScramble(message);
            if (answer != null) return answer;
        }
        if (solveReverse) {
            String answer = solveReverse(message);
            if (answer != null) return answer;
        }
        if (solveTyping) {
            String answer = solveTyping(message);
            if (answer != null) return answer;
        }
        if (solveTrivia) {
            return solveTrivia(message);
        }
        return null;
    }

    private static boolean looksLikeGamePrompt(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("chat game")
                || lower.contains("unscramble")
                || lower.contains("unjumble")
                || lower.contains("scramble")
                || lower.contains("reverse")
                || lower.contains("backwards")
                || lower.contains("backward")
                || lower.contains("type this")
                || lower.contains("type the")
                || lower.contains("repeat after")
                || lower.contains("quick math")
                || lower.contains("solve")
                || lower.contains("calculate")
                || lower.contains("first to answer")
                || lower.contains("fastest to answer")
                || lower.contains("trivia")
                || lower.contains("what is")
                || lower.contains("who is")
                || lower.contains("which mob")
                || lower.contains("complete the");
    }

    private static String solveMath(String message) {
        Matcher matcher = MATH.matcher(message);
        if (!matcher.find()) return null;

        try {
            BigDecimal left = new BigDecimal(matcher.group(1));
            BigDecimal right = new BigDecimal(matcher.group(3));
            String operation = matcher.group(2).toLowerCase(Locale.ROOT);
            BigDecimal result;

            if (operation.equals("+") || operation.equals("plus") || operation.equals("add")) {
                result = left.add(right);
            } else if (operation.equals("-") || operation.equals("−")
                    || operation.equals("minus") || operation.equals("subtract")) {
                result = left.subtract(right);
            } else if (operation.equals("*") || operation.equals("x")
                    || operation.equals("×") || operation.equals("times")
                    || operation.equals("multiply")) {
                result = left.multiply(right);
            } else {
                if (right.compareTo(BigDecimal.ZERO) == 0) return null;
                result = left.divide(right, 6, java.math.RoundingMode.HALF_UP);
            }

            if (result.abs().compareTo(new BigDecimal("1000000000000")) > 0) return null;
            return result.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    private static String solveScramble(String message) {
        Matcher matcher = SCRAMBLE.matcher(message);
        if (!matcher.find()) return null;

        String scrambled = matcher.group(1).toLowerCase(Locale.ROOT);
        List<String> candidates = WORD_SIGNATURES.get(signature(scrambled));
        if (candidates == null || candidates.size() != 1) return null;
        return candidates.getFirst();
    }

    private static String solveReverse(String message) {
        Matcher matcher = REVERSE.matcher(message);
        if (!matcher.find()) return null;
        return new StringBuilder(matcher.group(1)).reverse().toString();
    }

    private static String solveTyping(String message) {
        if (!message.toLowerCase(Locale.ROOT).contains("type")
                && !message.toLowerCase(Locale.ROOT).contains("repeat after")) return null;

        Matcher matcher = TYPE.matcher(message);
        if (!matcher.find()) return null;
        String answer = matcher.group(1).trim();
        if (answer.isEmpty() || answer.length() > 48) return null;
        return answer;
    }

    private static String solveTrivia(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : TRIVIA.entrySet()) {
            if (lower.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private static boolean looksLikeTrivia(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("trivia") || lower.contains("who is")
                || lower.contains("which mob") || lower.contains("what mob")
                || lower.contains("what item") || lower.contains("which item")
                || lower.contains("what dimension");
    }

    private static String stripFormatting(String value) {
        return value.replaceAll("(?i)§[0-9A-FK-OR]", "").replaceAll("\\s+", " ").trim();
    }

    private static String signature(String word) {
        char[] chars = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "").toCharArray();
        java.util.Arrays.sort(chars);
        return new String(chars);
    }

    private static Map<String, List<String>> buildWordSignatures() {
        Map<String, List<String>> result = new HashMap<>();
        for (String word : WORDS) {
            if (word.contains(" ")) continue;
            result.computeIfAbsent(signature(word), key -> new ArrayList<>()).add(word);
        }
        return result;
    }

    private static Map<String, String> buildTrivia() {
        Map<String, String> result = new HashMap<>();
        result.put("what mob explodes", "Creeper");
        result.put("which mob explodes", "Creeper");
        result.put("what mob shoots exploding fireballs", "Ghast");
        result.put("which mob shoots exploding fireballs", "Ghast");
        result.put("what item revives", "Totem of Undying");
        result.put("which item can save you from death", "Totem of Undying");
        result.put("what dimension has ender dragon", "The End");
        result.put("which dimension is the ender dragon in", "The End");
        result.put("what material is used to make a nether portal", "Obsidian");
        result.put("which block makes a nether portal", "Obsidian");
        result.put("what mob trades with gold", "Piglin");
        result.put("which mob barters with gold", "Piglin");
        result.put("what tool mines stone", "Pickaxe");
        result.put("which tool is used to mine stone", "Pickaxe");
        result.put("what ore drops diamonds", "Diamond Ore");
        result.put("which ore drops diamonds", "Diamond Ore");
        result.put("what mob guards ancient cities", "Warden");
        result.put("which mob guards ancient cities", "Warden");
        result.put("what item is needed to activate an end portal", "Eye of Ender");
        result.put("which item activates the end portal", "Eye of Ender");
        result.put("who created minecraft", "Markus Persson");
        result.put("who is the creator of minecraft", "Markus Persson");
        result.put("what is the name of minecraft's final boss", "Ender Dragon");
        result.put("who is minecraft's final boss", "Ender Dragon");
        return result;
    }

    public static void loadConfig() {
        Properties p = new Properties();
        try {
            Files.createDirectories(CONFIG.getParent());
            if (Files.exists(CONFIG)) {
                try (InputStream input = Files.newInputStream(CONFIG)) {
                    p.load(input);
                }
            }

            enabled = bool(p, "enabled", false);
            autoSend = bool(p, "autoSend", false);
            solveMath = bool(p, "solveMath", true);
            solveScramble = bool(p, "solveScramble", true);
            solveReverse = bool(p, "solveReverse", true);
            solveTyping = bool(p, "solveTyping", true);
            solveTrivia = bool(p, "solveTrivia", true);
            onlyRecognizedPrompts = bool(p, "onlyRecognizedPrompts", true);
            answerDelayMs = integer(p, "answerDelayMs", 900, 0, 5000);
            minimumSendIntervalMs = integer(p, "minimumSendIntervalMs", 1800, 500, 10000);

            saveConfig();
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/ChatGuesser] Config load failed: " + exception.getMessage());
        }
    }

    public static void saveConfig() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("autoSend", Boolean.toString(autoSend));
        p.setProperty("solveMath", Boolean.toString(solveMath));
        p.setProperty("solveScramble", Boolean.toString(solveScramble));
        p.setProperty("solveReverse", Boolean.toString(solveReverse));
        p.setProperty("solveTyping", Boolean.toString(solveTyping));
        p.setProperty("solveTrivia", Boolean.toString(solveTrivia));
        p.setProperty("onlyRecognizedPrompts", Boolean.toString(onlyRecognizedPrompts));
        p.setProperty("answerDelayMs", Integer.toString(answerDelayMs));
        p.setProperty("minimumSendIntervalMs", Integer.toString(minimumSendIntervalMs));

        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG)) {
                p.store(output, "QYS-UTILS Chat Game Guesser settings");
            }
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/ChatGuesser] Config save failed: " + exception.getMessage());
        }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        return Boolean.parseBoolean(p.getProperty(key, Boolean.toString(fallback)));
    }

    private static int integer(Properties p, String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max,
                    Integer.parseInt(p.getProperty(key, Integer.toString(fallback)))));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
