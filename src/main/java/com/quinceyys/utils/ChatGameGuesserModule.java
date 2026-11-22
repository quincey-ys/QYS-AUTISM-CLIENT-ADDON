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
            "(?i)(?:unscramble|unjumble|word scramble|scramble(?:d)? word|rearrange(?: the letters)?)(?:\\s+(?:this|the|following))?(?:\\s+word)?\\s*[:：-]?\\s*([a-z]{3,18})\\b"
    );
    private static final Pattern NUMBER_RANGE = Pattern.compile(
            "(?i)(?:guess|find|pick)(?: the)? number(?: between| from)\\s+(\\d{1,7})\\s*(?:and|to|[-–])\\s*(\\d{1,7})"
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
    public static boolean solveNumberGuess = true;
    public static boolean onlyRecognizedPrompts = true;
    public static int answerDelayMs = 900;
    public static int minimumSendIntervalMs = 1800;

    private static String pendingAnswer;
    private static String lastPrompt;
    private static long answerDueAt;
    private static long lastAnswerAt;
    private static boolean numberGuessActive;
    private static int numberGuessLow = 1;
    private static int numberGuessHigh = 100;
    private static int lastNumberGuess = 50;

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
        if (message.isEmpty()) return;

        String numberAnswer = handleNumberGuessMessage(message);
        if (numberAnswer != null) {
            queueAnswer(message, numberAnswer);
            return;
        }

        if (onlyRecognizedPrompts && !looksLikeGamePrompt(message)) return;
        String answer = solve(message);
        if (answer == null || answer.isBlank()) return;
        queueAnswer(message, answer);
    }

    private static void queueAnswer(String message, String answer) {
        long now = System.currentTimeMillis();
        if (message.equals(lastPrompt) && now - lastAnswerAt < 5000L) return;
        lastPrompt = message;
        pendingAnswer = answer;
        answerDueAt = now + answerDelayMs;
    }

    private static String handleNumberGuessMessage(String message) {
        if (!solveNumberGuess) return null;
        String lower = message.toLowerCase(Locale.ROOT);

        if (numberGuessActive) {
            if (lower.contains("correct") || lower.contains("you win")
                    || lower.contains("winner") || lower.contains("guessed it")) {
                numberGuessActive = false;
                return null;
            }

            if (lower.contains("too high") || lower.contains("lower")
                    || lower.contains("smaller") || lower.contains("less than")) {
                numberGuessHigh = Math.min(numberGuessHigh, lastNumberGuess - 1);
                return nextNumberGuess();
            }
            if (lower.contains("too low") || lower.contains("higher")
                    || lower.contains("greater") || lower.contains("more than")) {
                numberGuessLow = Math.max(numberGuessLow, lastNumberGuess + 1);
                return nextNumberGuess();
            }
        }

        Matcher range = NUMBER_RANGE.matcher(message);
        if (!range.find()) return null;
        try {
            int first = Integer.parseInt(range.group(1));
            int second = Integer.parseInt(range.group(2));
            numberGuessLow = Math.min(first, second);
            numberGuessHigh = Math.max(first, second);
            numberGuessActive = numberGuessLow < numberGuessHigh;
            return numberGuessActive ? nextNumberGuess() : null;
        } catch (NumberFormatException exception) {
            numberGuessActive = false;
            return null;
        }
    }

    private static String nextNumberGuess() {
        if (numberGuessLow > numberGuessHigh) {
            numberGuessActive = false;
            return null;
        }
        lastNumberGuess = numberGuessLow + (numberGuessHigh - numberGuessLow) / 2;
        return Integer.toString(lastNumberGuess);
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
                || lower.contains("guess the number")
                || lower.contains("find the number")
                || lower.contains("pick the number")
                || lower.contains("what is")
                || lower.contains("what was")
                || lower.contains("what are")
                || lower.contains("what does")
                || lower.contains("what word")
                || lower.contains("who is")
                || lower.contains("who was")
                || lower.contains("who created")
                || lower.contains("who made")
                || lower.contains("when was")
                || lower.contains("when did")
                || lower.contains("what year")
                || lower.contains("where is")
                || lower.contains("where was")
                || lower.contains("which mob")
                || lower.contains("which item")
                || lower.contains("which planet")
                || lower.contains("how many")
                || lower.contains("how old")
                || lower.contains("how tall")
                || lower.contains("how big")
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
        String normalized = normalizeTriviaText(message);
        if (normalized.isEmpty()) return null;

        // Prefer the most specific phrase so a broad alias cannot override a precise answer.
        List<Map.Entry<String, String>> entries = new ArrayList<>(TRIVIA.entrySet());
        entries.sort((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()));
        for (Map.Entry<String, String> entry : entries) {
            if (normalized.contains(normalizeTriviaText(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String normalizeTriviaText(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("(?i)§[0-9A-FK-OR]", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
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

        // Minecraft history and development.
        result.put("who created minecraft", "Markus Persson (Notch)");
        result.put("who is the creator of minecraft", "Markus Persson (Notch)");
        result.put("who made minecraft", "Markus Persson (Notch)");
        result.put("who developed minecraft", "Mojang Studios; it was originally created by Markus Persson (Notch)");
        result.put("when was minecraft made", "The first public version was released on May 17, 2009; the full game launched on November 18, 2011");
        result.put("when was minecraft created", "Development began in 2009; the first public version was released on May 17, 2009");
        result.put("when did minecraft come out", "The first public version came out on May 17, 2009; the full release was November 18, 2011");
        result.put("when did minecraft first come out", "May 17, 2009 (first public version)");
        result.put("when was minecraft first released", "May 17, 2009 (first public version)");
        result.put("what year was minecraft made", "2009");
        result.put("what year was minecraft created", "2009");
        result.put("what year did minecraft come out", "2009 (first public version)");
        result.put("what year was minecraft released", "2009 (first public version); 2011 was the full release");
        result.put("when was minecraft officially released", "November 18, 2011 (full release)");
        result.put("when did minecraft officially release", "November 18, 2011");
        result.put("when was the full release of minecraft", "November 18, 2011");
        result.put("what was minecraft originally called", "Cave Game");
        result.put("what was minecraft first called", "Cave Game");
        result.put("what is minecraft original name", "Cave Game");
        result.put("what company owns minecraft", "Microsoft, which acquired Mojang in 2014");
        result.put("who owns minecraft", "Microsoft");
        result.put("what year did microsoft buy mojang", "2014");
        result.put("when did microsoft buy mojang", "2014");
        result.put("how much did microsoft buy mojang for", "About 2.5 billion US dollars");
        result.put("what does notch mean", "Notch is the nickname of Minecraft's original creator, Markus Persson");
        result.put("what is the name of minecraft final boss", "Ender Dragon");
        result.put("who is minecraft final boss", "Ender Dragon");

        // Minecraft gameplay facts.
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
        result.put("what does a creeper drop", "Gunpowder");
        result.put("what do creepers drop", "Gunpowder");
        result.put("what do you use to tame a wolf", "Bones");
        result.put("what food tames a cat", "Raw cod or raw salmon");
        result.put("what item is used to tame a horse", "Horses are tamed by repeatedly mounting them; food can help");
        result.put("what is the strongest material for tools", "Netherite");
        result.put("what block is the end portal frame made of", "End Portal Frame");
        result.put("what mob can teleport", "Enderman");
        result.put("which mob can teleport", "Enderman");
        result.put("what is the name of the dimension with piglins", "The Nether");
        result.put("what dimension do piglins live in", "The Nether");
        result.put("what item do you need to make an enchanting table", "A book, two diamonds, and four obsidian");
        result.put("how many eyes of ender activate a portal", "Up to 12");
        result.put("how many eyes of ender are needed for the end portal", "Up to 12; some frames may already contain eyes");
        result.put("what is the rare blue mob in minecraft", "The Allay is a rare blue flying helper mob");
        result.put("what mob carries a trident", "Drowned");
        result.put("which mob carries a trident", "Drowned");
        result.put("what do you need to charge a respawn anchor", "Glowstone");
        result.put("what item charges a respawn anchor", "Glowstone");
        result.put("what happens if you use a bed in the nether", "It explodes");
        result.put("what happens when you sleep in the nether", "The bed explodes");
        result.put("what happens if you use a bed in the end", "It explodes");
        result.put("what mob drops a wither skeleton skull", "Wither Skeleton");
        result.put("which mob drops a wither skeleton skull", "Wither Skeleton");

        // General knowledge and quick fun facts.
        result.put("what is the largest planet", "Jupiter");
        result.put("which is the largest planet", "Jupiter");
        result.put("what planet is the biggest", "Jupiter");
        result.put("what planet is known as the red planet", "Mars");
        result.put("which planet is called the red planet", "Mars");
        result.put("what is the closest planet to the sun", "Mercury");
        result.put("which planet is closest to the sun", "Mercury");
        result.put("what is the hottest planet", "Venus");
        result.put("which planet is the hottest", "Venus");
        result.put("what is the largest ocean", "The Pacific Ocean");
        result.put("which is the largest ocean", "The Pacific Ocean");
        result.put("what is the tallest mountain", "Mount Everest (highest above sea level)");
        result.put("what is the highest mountain above sea level", "Mount Everest");
        result.put("what is the capital of japan", "Tokyo");
        result.put("what is japan capital", "Tokyo");
        result.put("what is the capital of france", "Paris");
        result.put("what is the capital of philippines", "Manila");
        result.put("what is the capital of italy", "Rome");
        result.put("what is the capital of australia", "Canberra");
        result.put("what is the capital of canada", "Ottawa");
        result.put("what is the capital of south korea", "Seoul");
        result.put("what is the capital of the united kingdom", "London");
        result.put("what is the chemical formula for water", "H2O");
        result.put("what is the formula for water", "H2O");
        result.put("what does h2o stand for", "Water");
        result.put("what is the chemical symbol for gold", "Au");
        result.put("what is the chemical symbol for oxygen", "O");
        result.put("what is the chemical symbol for iron", "Fe");
        result.put("what is the smallest prime number", "2");
        result.put("what is the only even prime number", "2");
        result.put("how many sides does a hexagon have", "6");
        result.put("how many sides does an octagon have", "8");
        result.put("how many bones does an adult human have", "Usually 206");
        result.put("how many bones are in the human body", "An adult usually has 206 bones");
        result.put("how many chambers does the human heart have", "4");
        result.put("how many hearts does an octopus have", "3");
        result.put("what color is an octopus blood", "Blue");
        result.put("what is the fastest land animal", "Cheetah");
        result.put("which is the fastest land animal", "Cheetah");
        result.put("what is the largest mammal", "Blue whale");
        result.put("which is the largest mammal", "Blue whale");
        result.put("what animal has the longest neck", "Giraffe");
        result.put("what gas do plants absorb", "Carbon dioxide");
        result.put("what gas do plants take in", "Carbon dioxide");
        result.put("what gas do humans breathe in", "Oxygen");
        result.put("what is the hardest natural substance", "Diamond");
        result.put("what is the hardest natural mineral", "Diamond");
        result.put("what is the speed of light", "299,792,458 metres per second in a vacuum");
        result.put("how many colors are in a rainbow", "Traditionally 7");
        result.put("what is the smallest continent", "Australia");
        result.put("what is the largest continent", "Asia");
        result.put("what is the largest country by area", "Russia");
        result.put("what is the largest desert", "Antarctica (the largest desert overall); the Sahara is the largest hot desert");
        result.put("what is the currency of japan", "Yen");
        result.put("what is the currency of the philippines", "Philippine peso");
        result.put("what is the currency of the united states", "US dollar");
        result.put("how many days are in a leap year", "366");
        result.put("how many months have 28 days", "All 12 months have at least 28 days");
        result.put("what is the nearest star to earth", "The Sun");
        result.put("what is the natural satellite of earth", "The Moon");
        result.put("what is earth natural satellite", "The Moon");

        // Common internet and pop-culture trivia.
        result.put("who sings never gonna give you up", "Rick Astley");
        result.put("who sang never gonna give you up", "Rick Astley");
        result.put("what is a rickroll", "A prank that tricks someone into opening Rick Astley's Never Gonna Give You Up music video");
        result.put("what does lol stand for", "Laughing out loud");
        result.put("what does brb stand for", "Be right back");
        result.put("what does idk stand for", "I don't know");
        result.put("what does tbh stand for", "To be honest");
        result.put("what does afk stand for", "Away from keyboard");
        result.put("what does sus mean", "Suspicious");
        result.put("what does 404 mean", "Not Found; it is a common HTTP status code");
        result.put("what is the meaning of 404", "Not Found; it is a common HTTP status code");
        result.put("what does gg mean", "Good game");
        result.put("what does npc mean", "Non-player character");
        result.put("what does bruh mean", "An informal expression of disbelief, frustration, or surprise");

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
            solveNumberGuess = bool(p, "solveNumberGuess", true);
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
        p.setProperty("solveNumberGuess", Boolean.toString(solveNumberGuess));
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
