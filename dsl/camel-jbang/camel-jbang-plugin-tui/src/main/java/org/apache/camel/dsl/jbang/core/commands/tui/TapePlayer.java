/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Plays a tape for {@code camel tui --record}: the TUI is driven by the tape while TamboUI records it to an asciinema
 * {@code .cast} file.
 * <p>
 * A tape uses the <a href="https://github.com/charmbracelet/vhs">VHS</a> syntax (keys, {@code Type}, {@code Sleep},
 * {@code Wait /regex/}) plus commands for the TUI itself ({@code Caption}, {@code Highlight}, {@code Tab}, ...). The
 * keys go through the same queue as the keys an MCP client sends, so every key works, function keys and modifiers
 * included, and the TUI commands call the same tools an MCP client calls.
 */
final class TapePlayer {

    /** What the player drives: the TUI, or a test double. */
    interface Driver {
        /** Presses a key. */
        void key(KeyEvent event);

        /** The screen as text, its rows separated by newlines; null before the first frame is rendered. */
        String screen();

        /** Calls a TUI tool (as an MCP client does) and returns its result. */
        String tool(String name, Map<String, Object> args) throws Exception;

        /** Ends the TUI, which ends the recording. */
        void quit();
    }

    /** One step of a tape. */
    sealed interface Step
            permits KeyStep, SleepStep, WaitStep, ToolStep, HighlightStep {
    }

    record KeyStep(KeyEvent event) implements Step {
    }

    record SleepStep(long millis) implements Step {
    }

    /**
     * Waits until a line of the screen (or the whole screen, its rows joined by newlines) matches the pattern.
     */
    record WaitStep(Pattern pattern, boolean wholeScreen, long timeoutMillis, String line) implements Step {
    }

    record ToolStep(String name, Map<String, Object> args, String line) implements Step {
    }

    /** Marks the first place on the screen showing the text, optionally for a number of seconds. */
    record HighlightStep(String text, int seconds, String line) implements Step {
    }

    /** How long typing waits between characters, and keys between presses, unless the tape says otherwise. */
    static final long DEFAULT_TYPING_MILLIS = 50;
    /** How long a Wait waits for its text when the tape gives no timeout, as in VHS. */
    static final long DEFAULT_WAIT_MILLIS = 15000;
    /** How often the screen is checked while waiting. */
    static final long WAIT_POLL_MILLIS = 100;
    /** How long the first frame may take to render before the tape starts anyway. */
    static final long FIRST_FRAME_MILLIS = 10000;
    /** How long the last frame stays before the TUI quits, so the recording shows it. */
    static final long END_PAUSE_MILLIS = 1000;

    private final Consumer<String> warnings;

    TapePlayer(Consumer<String> warnings) {
        this.warnings = warnings;
    }

    // ---- Parsing ----

    /** Parses a tape file; {@code Source} includes other tapes relative to it. */
    List<Step> parse(Path tape) throws IOException {
        List<Step> steps = new ArrayList<>();
        Path file = tape.toAbsolutePath().normalize();
        Deque<Path> including = new ArrayDeque<>();
        including.push(file);
        parse(Files.readAllLines(file), file.getParent(), including, steps);
        return steps;
    }

    /** Parses the lines of a tape into steps; unknown lines are reported and skipped. */
    List<Step> parse(List<String> lines) {
        List<Step> steps = new ArrayList<>();
        parse(lines, null, new ArrayDeque<>(), steps);
        return steps;
    }

    /** The tapes being parsed are in {@code including}, so a tape that sources itself (in a loop) is reported. */
    private void parse(List<String> lines, Path dir, Deque<Path> including, List<Step> steps) {
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                parseLine(line, dir, including, steps);
            } catch (IllegalArgumentException e) {
                warnings.accept(e.getMessage() + ": " + line);
            }
        }
    }

    private void parseLine(String line, Path dir, Deque<Path> including, List<Step> steps) {
        // The command is the first word, optionally with a timing: Command@duration ("Down@200ms 3", "Type@100ms
        // "text"", "Wait@10s /Ready/", "Caption@5s "text""). Only the first word is checked for '@', so text such as
        // Type "user@example.com" is typed as it is.
        String[] parts = line.split("\\s+", 2);
        String head = parts[0];
        String args = parts.length > 1 ? parts[1].strip() : "";
        long timing = -1;
        int at = head.indexOf('@');
        if (at > 0) {
            timing = parseDuration(head.substring(at + 1));
            head = head.substring(0, at);
        }
        String command = head.toLowerCase(Locale.ROOT);
        // Tab alone or with a count (Tab 2) is the key, as in VHS; Tab "name" switches to the tab
        if ("tab".equals(command) && (args.isEmpty() || args.chars().allMatch(Character::isDigit))) {
            command = "";
        }

        switch (command) {
            // directives for the vhs tool (terminal settings, output file, required programs): the recording takes
            // its size and file from the --record options
            case "set", "output", "require" -> {
            }
            case "source" -> {
                Path file = (dir != null ? dir.resolve(args) : Path.of(args)).toAbsolutePath().normalize();
                if (including.contains(file)) {
                    throw new IllegalArgumentException("The tape includes itself");
                }
                List<String> included;
                try {
                    included = Files.readAllLines(file);
                } catch (IOException e) {
                    throw new IllegalArgumentException("Cannot read the tape to include (" + e.getMessage() + ")");
                }
                including.push(file);
                parse(included, file.getParent(), including, steps);
                including.pop();
            }
            case "hide", "show" -> throw new IllegalArgumentException(
                    "Hide and Show are not supported, the interactions are recorded");
            case "sleep" -> steps.add(new SleepStep(parseDuration(args)));
            case "type" -> addTyped(unquote(args), timing >= 0 ? timing : DEFAULT_TYPING_MILLIS, steps);
            case "wait", "wait+line", "wait+screen" -> steps.add(new WaitStep(
                    parseRegex(args), "wait+screen".equals(command), timing > 0 ? timing : DEFAULT_WAIT_MILLIS, line));
            case "screenshot" -> steps.add(new ToolStep("tui_action", Map.of("action", "screenshot"), line));
            // the commands for the TUI itself
            case "caption" -> {
                Map<String, Object> toolArgs = new LinkedHashMap<>();
                toolArgs.put("text", unquote(args).replace("\\n", "\n"));
                if (timing > 0) {
                    toolArgs.put("duration", (int) Math.max(1, timing / 1000));
                }
                steps.add(new ToolStep("tui_show_caption", toolArgs, line));
            }
            case "highlight" -> steps.add(new HighlightStep(unquote(args), timing > 0 ? (int) (timing / 1000) : 0, line));
            case "cleardrawing" -> steps.add(new ToolStep("tui_draw_clear", Map.of(), line));
            case "tab" -> steps.add(new ToolStep("tui_navigate", Map.of("tab", unquote(args)), line));
            case "integration" -> steps.add(new ToolStep("tui_navigate", Map.of("integration", unquote(args)), line));
            case "action" -> steps.add(new ToolStep("tui_action", Map.of("action", unquote(args)), line));
            case "theme" -> steps.add(new ToolStep("tui_set_theme", Map.of("theme", unquote(args)), line));
            default -> {
                KeyEvent key = parseKey(head);
                if (key == null) {
                    throw new IllegalArgumentException("Unknown tape command");
                }
                int count = args.isEmpty() ? 1 : parseCount(args);
                long gap = timing >= 0 ? timing : DEFAULT_TYPING_MILLIS;
                for (int i = 0; i < count; i++) {
                    steps.add(new KeyStep(key));
                    steps.add(new SleepStep(gap));
                }
            }
        }
    }

    private static void addTyped(String text, long gap, List<Step> steps) {
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            KeyEvent key = switch (cp) {
                case '\n', '\r' -> KeyEvent.ofKey(KeyCode.ENTER);
                case '\t' -> KeyEvent.ofKey(KeyCode.TAB);
                default -> KeyEvent.ofChar(cp);
            };
            steps.add(new KeyStep(key));
            steps.add(new SleepStep(gap));
        }
    }

    /**
     * A key name with optional modifiers ({@code Enter}, {@code F8}, {@code Shift+F8}, {@code Ctrl+c}, {@code Alt+x},
     * {@code Shift+Tab}); null when it is not a key.
     */
    static KeyEvent parseKey(String spec) {
        boolean ctrl = false;
        boolean alt = false;
        boolean shift = false;
        String key = spec;
        int plus;
        // a lone "+" is a key of its own, not a modifier separator
        while ((plus = key.indexOf('+')) > 0 && plus < key.length() - 1) {
            switch (key.substring(0, plus).toLowerCase(Locale.ROOT)) {
                case "ctrl", "control" -> ctrl = true;
                case "alt", "meta" -> alt = true;
                case "shift" -> shift = true;
                default -> {
                    return null;
                }
            }
            key = key.substring(plus + 1);
        }
        boolean modified = ctrl || alt || shift;
        KeyModifiers modifiers = KeyModifiers.of(ctrl, alt, shift);
        KeyCode code = switch (key.toLowerCase(Locale.ROOT)) {
            case "enter", "return" -> KeyCode.ENTER;
            case "tab" -> KeyCode.TAB;
            case "backspace" -> KeyCode.BACKSPACE;
            case "delete" -> KeyCode.DELETE;
            case "insert" -> KeyCode.INSERT;
            case "escape", "esc" -> KeyCode.ESCAPE;
            case "up" -> KeyCode.UP;
            case "down" -> KeyCode.DOWN;
            case "left" -> KeyCode.LEFT;
            case "right" -> KeyCode.RIGHT;
            case "home" -> KeyCode.HOME;
            case "end" -> KeyCode.END;
            case "pageup", "pgup" -> KeyCode.PAGE_UP;
            case "pagedown", "pgdn" -> KeyCode.PAGE_DOWN;
            default -> functionKey(key);
        };
        if (code != null) {
            return KeyEvent.ofKey(code, modifiers);
        }
        if ("space".equalsIgnoreCase(key)) {
            return KeyEvent.ofChar(' ', modifiers);
        }
        // a single character is a key only with a modifier (Ctrl+t); a bare letter on its own line is a typo
        if (modified && key.codePointCount(0, key.length()) == 1) {
            int cp = key.codePointAt(0);
            return KeyEvent.ofChar(shift && Character.isLetter(cp) ? Character.toUpperCase(cp) : cp, modifiers);
        }
        return null;
    }

    private static KeyCode functionKey(String key) {
        if (key.length() >= 2 && key.length() <= 3 && (key.charAt(0) == 'F' || key.charAt(0) == 'f')) {
            try {
                int n = Integer.parseInt(key.substring(1));
                if (n >= 1 && n <= 12) {
                    return KeyCode.valueOf("F" + n);
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** A duration such as {@code 500ms}, {@code 2s} or {@code 1.5s}; a bare number is seconds, as in VHS. */
    static long parseDuration(String text) {
        String s = text.strip().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("ms")) {
                return Long.parseLong(s.substring(0, s.length() - 2).strip());
            }
            if (s.endsWith("s")) {
                return Math.round(Double.parseDouble(s.substring(0, s.length() - 1).strip()) * 1000);
            }
            return Math.round(Double.parseDouble(s) * 1000);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid duration '" + text + "'");
        }
    }

    private static int parseCount(String text) {
        try {
            return Math.max(1, Integer.parseInt(text.strip()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid repeat count '" + text + "'");
        }
    }

    private static Pattern parseRegex(String text) {
        if (text.length() < 2 || !text.startsWith("/") || !text.endsWith("/")) {
            throw new IllegalArgumentException("Wait needs a /regex/ to wait for");
        }
        try {
            return Pattern.compile(text.substring(1, text.length() - 1));
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex (" + e.getDescription() + ")");
        }
    }

    /** The text of a quoted argument ("..." with \" \\ \n \t escapes), or the argument as it is when unquoted. */
    static String unquote(String text) {
        String s = text.strip();
        if (s.length() < 2 || s.charAt(0) != '"' || s.charAt(s.length() - 1) != '"') {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() - 1) {
                char next = s.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---- Playing ----

    /** Plays the steps on the calling thread, then quits the TUI. */
    void play(List<Step> steps, Driver driver) throws InterruptedException {
        long start = System.nanoTime();
        while (driver.screen() == null && System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(FIRST_FRAME_MILLIS)) {
            Thread.sleep(WAIT_POLL_MILLIS);
        }
        for (Step step : steps) {
            if (step instanceof KeyStep k) {
                driver.key(k.event());
            } else if (step instanceof SleepStep s) {
                Thread.sleep(s.millis());
            } else if (step instanceof WaitStep w) {
                waitFor(w, driver);
            } else if (step instanceof ToolStep t) {
                callTool(t.name(), t.args(), t.line(), driver);
            } else if (step instanceof HighlightStep h) {
                highlight(h, driver);
            }
        }
        Thread.sleep(END_PAUSE_MILLIS);
        driver.quit();
    }

    private void waitFor(WaitStep wait, Driver driver) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(wait.timeoutMillis());
        while (!matches(wait, driver.screen())) {
            if (System.nanoTime() - deadline >= 0) {
                warnings.accept("Timed out after " + wait.timeoutMillis() + "ms: " + wait.line());
                return;
            }
            Thread.sleep(WAIT_POLL_MILLIS);
        }
    }

    static boolean matches(WaitStep wait, String screen) {
        if (screen == null) {
            return false;
        }
        if (wait.wholeScreen()) {
            return wait.pattern().matcher(screen).find();
        }
        for (String row : screen.split("\n", -1)) {
            if (wait.pattern().matcher(row).find()) {
                return true;
            }
        }
        return false;
    }

    private String callTool(String name, Map<String, Object> args, String line, Driver driver) {
        try {
            String result = driver.tool(name, args);
            if (result != null && result.startsWith("Error")) {
                warnings.accept(result + ": " + line);
            }
            return result;
        } catch (Exception e) {
            warnings.accept("Failed (" + e.getMessage() + "): " + line);
            return null;
        }
    }

    private void highlight(HighlightStep step, Driver driver) {
        String located = callTool("tui_locate", Map.of("text", step.text()), step.line(), driver);
        JsonObject match = firstMatch(located);
        if (match == null) {
            warnings.accept("Text not on the screen: " + step.line());
            return;
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("shape", "highlight");
        args.put("x", match.getInteger("x"));
        args.put("y", match.getInteger("y"));
        args.put("width", match.getInteger("width"));
        args.put("append", true);
        if (step.seconds() > 0) {
            args.put("duration", step.seconds());
        }
        callTool("tui_draw_shape", args, step.line(), driver);
    }

    private static JsonObject firstMatch(String located) {
        if (located == null) {
            return null;
        }
        try {
            Object parsed = Jsoner.deserialize(located);
            if (parsed instanceof JsonObject result && result.get("matches") instanceof JsonArray matches
                    && !matches.isEmpty() && matches.get(0) instanceof JsonObject first) {
                return first;
            }
        } catch (Exception e) {
            // not a locate result
        }
        return null;
    }

    /** The buffer as text, its rows separated by newlines (continuation cells of wide characters left out). */
    static String screenText(Buffer buffer) {
        if (buffer == null) {
            return null;
        }
        Rect area = buffer.area();
        StringBuilder sb = new StringBuilder();
        for (int y = area.top(); y < area.bottom(); y++) {
            if (y > area.top()) {
                sb.append('\n');
            }
            for (int x = area.left(); x < area.right(); x++) {
                if (!buffer.get(x, y).isContinuation()) {
                    sb.append(buffer.get(x, y).symbol());
                }
            }
        }
        return sb.toString();
    }
}
