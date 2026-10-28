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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.common.EndpointScope;
import org.apache.camel.util.URISupport;

/**
 * The calls of the AI tools that the user confirms first, one set of rules for the clients of the tools (CAMEL-25676).
 * The Camel CLI and its monitor are developer tools: their AI assistants run the tools without asking, so the AI can
 * show and tell at once (see "Developer tools" in the security model). The exceptions are the calls that reach beyond
 * the integration, may destroy data or run code:
 * <ul>
 * <li>a message to an endpoint the integration does not have; a route id, a pattern and an endpoint of a component the
 * catalog marks internal (direct:, seda:, log:, mock: and the like) stay inside it. Also a message whose body the
 * integration reads from a file outside the project folder (a body that starts with file:);</li>
 * <li>a SQL statement that is not a single SELECT (or SHOW, DESCRIBE), and a row update;</li>
 * <li>an expression in a language that runs code, such as Groovy.</li>
 * </ul>
 * A file write is confirmed with its diff ({@link #write}). {@code camel ask}, which shows no screen of the integration
 * while it works, also asks before a call that stops or starts an integration, browses an endpoint the integration does
 * not have, writes a heap dump, or runs a camel command that changes something ({@link #cliCommand}). An HTTP request
 * reaches only the integration's own server.
 */
public final class ToolConfirms {

    /** A call the user confirms: what the AI wants to do, the call (an endpoint, a statement, an expression), why. */
    public record Call(String title, String target, String reason) {
    }

    /** The expression languages that run code, in the integration (or the CLI when none is selected). */
    public static final Set<String> CODE_LANGUAGES
            = Set.of("groovy", "java", "joor", "js", "javascript", "python", "mvel", "ognl", "spel");

    private ToolConfirms() {
    }

    /**
     * Whether a component stays inside the integration, as the catalog marks it internal ({@link EndpointScope}): a
     * message to it needs no confirm. This is narrower than remote=false, which exec, class, bean, language and the
     * components that load a template or a stylesheet by URI are too.
     */
    public static boolean isInternal(String scheme) {
        return EndpointScope.isInternal(scheme);
    }

    /**
     * A message to an endpoint the integration does not have, or with a body read from a file outside the project; null
     * when it stays inside the integration and the project.
     *
     * @param fileOutside the file outside the project that the body makes the integration read (see
     *                    {@link #fileOutsideProject}), or null
     */
    public static Call sendMessage(String endpoint, Collection<String> endpointUris, String fileOutside) {
        boolean inside = staysInside(endpoint, endpointUris);
        if (fileOutside != null) {
            return new Call(
                    inside
                            ? "send a file from outside the project"
                            : "send a file from outside the project to an endpoint the integration does not have",
                    endpoint + "\nbody: file:" + fileOutside,
                    "The integration reads the file and sends its content, which may hold secrets");
        }
        if (inside) {
            return null;
        }
        return new Call(
                "send a message to an endpoint the integration does not have", endpoint,
                "The message may leave the integration: to a file, a broker or a service");
    }

    /**
     * The file a message body makes the integration read when it is outside the project folder, or null. The send
     * console sends the content of the file that a body starting with file: names, relative to the working directory of
     * the integration. The project folder is the integration's source directory, where the AI tools write. Symlinks and
     * .. are resolved as the integration would; a file that cannot be placed counts as outside.
     */
    public static String fileOutsideProject(String body, String workingDirectory, Path projectDirectory) {
        if (body == null || !body.startsWith("file:")) {
            return null;
        }
        String name = body.substring(5);
        Path file;
        try {
            Path path = Path.of(name);
            if (!path.isAbsolute()) {
                // not known, blank or relative, it cannot place the file: the JVM of the CLI is elsewhere
                Path work = workingDirectory != null ? Path.of(workingDirectory) : null;
                if (work == null || !work.isAbsolute()) {
                    return name;
                }
                path = work.resolve(path);
            }
            file = realPath(path);
        } catch (InvalidPathException e) {
            return name;
        }
        return projectDirectory != null && file.startsWith(realPath(projectDirectory)) ? null : file.toString();
    }

    /**
     * The path with symlinks and .. resolved as the file system does. A file that is not there is placed under the real
     * path of its nearest parent that is.
     */
    private static Path realPath(Path path) {
        Path absolute = path.toAbsolutePath();
        try {
            return absolute.toRealPath();
        } catch (IOException e) {
            Path parent = absolute.getParent();
            return parent != null ? realPath(parent).resolve(absolute.getFileName()).normalize() : absolute.normalize();
        }
    }

    /**
     * Whether a send stays inside the integration: a route id or a pattern (the send console picks one of the routes),
     * an internal endpoint, or an endpoint the integration has (its endpoint registry, as its status lists it).
     */
    public static boolean staysInside(String endpoint, Collection<String> endpointUris) {
        // the same tests as SendDevConsole: without a scheme, or ending with *, it names a route of the integration
        if (!endpoint.contains(":") || endpoint.endsWith("*")) {
            return true;
        }
        // an option can still reach out: #class:...(args) runs any constructor (a FileOutputStream truncates a file),
        // and a {{placeholder}} can put a secret in the log
        String decoded = decode(endpoint);
        if (isInternal(endpoint.substring(0, endpoint.indexOf(':')))
                && !decoded.contains("#") && !decoded.contains("{{")) {
            return true;
        }
        String uri = normalize(endpoint);
        return uri != null && endpointUris.stream().anyMatch(e -> uri.equals(normalize(e)));
    }

    /**
     * The normalized URI, decoded: normalizing keeps an option as it is written, localhost%3A9092 or localhost:9092.
     */
    private static String normalize(String uri) {
        try {
            return uri != null ? decode(URISupport.normalizeUri(uri)) : null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    private static String decode(String uri) {
        try {
            return URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // a lone % in an option
            return uri;
        }
    }

    /** A statement that is not a single SELECT (or SHOW, DESCRIBE), or null for one. */
    public static Call sql(String query) {
        if (readsOnly(query)) {
            return null;
        }
        return new Call("run SQL that is not a SELECT", query, "It may change or drop data in the integration's database");
    }

    public static Call updateRow(String table, String columnValues) {
        return new Call("update a row of " + table, columnValues, "It changes data in the integration's database");
    }

    /**
     * Whether a statement only reads: a single SELECT, SHOW or DESCRIBE, with no INTO (which creates a table or writes
     * a file). EXPLAIN is not one, as EXPLAIN ANALYZE runs the statement. Comments and the text of literals do not
     * count, so {@code SELECT ';'} is one statement, and {@code SELECT 1; DROP TABLE t} is two.
     */
    public static boolean readsOnly(String sql) {
        String code = codeOnly(sql).strip();
        while (code.endsWith(";")) {
            code = code.substring(0, code.length() - 1).strip();
        }
        while (code.startsWith("(")) {
            code = code.substring(1).strip();
        }
        String lower = code.toLowerCase(Locale.ROOT);
        return lower.matches("(select|show|describe|desc)\\b[\\s\\S]*") && !lower.contains(";")
                && !lower.matches("[\\s\\S]*\\binto\\b[\\s\\S]*");
    }

    /** The statement without comments, and with each literal or quoted name as a blank. */
    private static String codeOnly(String sql) {
        StringBuilder code = new StringBuilder();
        int i = 0;
        int n = sql == null ? 0 : sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                i = sql.indexOf('\n', i);
                i = i < 0 ? n : i;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                code.append(' ');
            } else if (c == '\'' || c == '"' || c == '`') {
                // to the closing quote; a doubled quote is part of the text
                int j = i + 1;
                while (j < n && (sql.charAt(j) != c || j + 1 < n && sql.charAt(j + 1) == c)) {
                    j += sql.charAt(j) == c ? 2 : 1;
                }
                i = j + 1;
                code.append(' ');
            } else {
                code.append(c);
                i++;
            }
        }
        return code.toString();
    }

    /** An expression in a language that runs code, or null. */
    public static Call evalExpression(String language, String expression) {
        String lang = language == null ? "" : language.strip();
        if (!CODE_LANGUAGES.contains(lang.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return new Call("evaluate a " + lang + " expression", expression, "It runs code on this machine");
    }

    /**
     * A write of a project file, with the change as a diff (the lines of a new file), or null when the content stays
     * the same.
     *
     * @param before the content before the write, or null for a new file
     */
    public static Call write(String file, String before, String after) {
        if (before != null && before.equals(after)) {
            return null;
        }
        String change = before == null ? newFile(after, MAX_DIFF_LINES) : diff(before, after, 2, MAX_DIFF_LINES);
        return new Call(before == null ? "create " + file : "change " + file, change, "It changes the project's files");
    }

    /** Stopping the integration, which does not start again by itself. */
    public static Call stopApplication(String integration) {
        return new Call(
                "stop the integration", integration != null ? integration : "the selected integration",
                "It stops the integration; it does not start again by itself");
    }

    /** A camel_control action that stops or restarts the integration or its routes, or null for the others. */
    public static Call control(String action, String target) {
        String a = action == null ? "" : action.strip().toLowerCase(Locale.ROOT);
        return switch (a) {
            case "stop", "kill" -> new Call(
                    a + " the integration", target,
                    "It stops the integration; it does not start again by itself");
            case "restart" -> new Call(
                    "restart the integration", target,
                    "The integration stops for a while, and the messages in flight may fail");
            case "stop-routes" -> new Call(
                    "stop routes", target,
                    "The routes take no messages until they are started again");
            default -> null;
        };
    }

    /** Running an integration on this machine. */
    public static Call run(String what) {
        return new Call("run an integration", what, "It starts an integration on this machine with the project's code");
    }

    /** A heap dump of the integration. */
    public static Call heapDump(String target) {
        return new Call("write a heap dump", target, "It writes a file as big as the heap of the integration");
    }

    /** Browsing an endpoint the integration does not have, or null for one it has (or an internal one). */
    public static Call browse(String endpoint, Collection<String> endpointUris) {
        if (endpoint == null || staysInside(endpoint, endpointUris)) {
            return null;
        }
        return new Call(
                "browse an endpoint the integration does not have", endpoint,
                "It reads messages or files from outside the integration, which may hold secrets");
    }

    /** The camel commands that only read. */
    private static final Set<String> READ_ONLY_COMMANDS = Set.of("get", "ps", "catalog", "doc");

    /** The commands that only read without a subcommand or with one of these, and change something with another. */
    private static final Map<String, Set<String>> READ_ONLY_SUBCOMMANDS = Map.of(
            "version", Set.of("get", "list"),
            "infra", Set.of("list", "ps", "get"),
            "config", Set.of("get", "list"),
            "dependency", Set.of("list"),
            "plugin", Set.of("get", "list"));

    /**
     * A camel command that may change something (files, integrations, infrastructure, messages, the CLI's own
     * configuration), or null for one that only reads: get, ps, catalog, doc, the reading subcommands of version,
     * infra, config, dependency and plugin, and a help.
     *
     * @param args the command line without the leading camel, as cli_exec splits it
     */
    public static Call cliCommand(String[] args) {
        List<String> words = new ArrayList<>(Arrays.asList(args));
        if (!words.isEmpty() && "camel".equals(words.get(0))) {
            words.remove(0);
        }
        if (words.isEmpty() || words.contains("--help") || words.contains("-h")) {
            return null;
        }
        String command = words.get(0);
        if (READ_ONLY_COMMANDS.contains(command)) {
            return null;
        }
        Set<String> reading = READ_ONLY_SUBCOMMANDS.get(command);
        if (reading != null && (words.size() == 1 || words.get(1).startsWith("-") || reading.contains(words.get(1)))) {
            return null;
        }
        return new Call(
                "run camel " + command, "camel " + String.join(" ", words),
                "A camel command can change files, start or stop integrations, or send messages");
    }

    /** The most lines of a change shown for a write. */
    static final int MAX_DIFF_LINES = 80;

    /** The common start and end are trimmed first; the rest is diffed line by line up to this many cells. */
    private static final long MAX_DIFF_CELLS = 1_000_000;

    /** The lines of a new file, each with a +, at most maxLines of them. */
    static String newFile(String content, int maxLines) {
        List<String> lines = content.lines().toList();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            sb.append('+').append(lines.get(i)).append('\n');
        }
        if (lines.size() > maxLines) {
            sb.append("... (").append(lines.size() - maxLines).append(" more lines)\n");
        }
        return sb.toString();
    }

    /**
     * The change from before to after as unified diff hunks with a few lines of context, at most maxLines lines. The
     * common start and end of the two are trimmed first, so a small edit of a long file costs little.
     */
    static String diff(String before, String after, int context, int maxLines) {
        List<Op> ops = editScript(before.lines().toList(), after.lines().toList());
        // the line numbers of each op in the old and the new file, 1-based
        int[] lineBefore = new int[ops.size()];
        int[] lineAfter = new int[ops.size()];
        List<Integer> changes = new ArrayList<>();
        int a = 1;
        int b = 1;
        for (int i = 0; i < ops.size(); i++) {
            Op op = ops.get(i);
            lineBefore[i] = a;
            lineAfter[i] = b;
            if (op.kind() != '+') {
                a++;
            }
            if (op.kind() != '-') {
                b++;
            }
            if (op.kind() != ' ') {
                changes.add(i);
            }
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        int h = 0;
        while (h < changes.size()) {
            // a hunk: the changes closer than two contexts apart, with the context around them
            int last = h;
            while (last + 1 < changes.size() && changes.get(last + 1) - changes.get(last) <= 2 * context + 1) {
                last++;
            }
            int from = Math.max(0, changes.get(h) - context);
            int to = Math.min(ops.size() - 1, changes.get(last) + context);
            int countBefore = 0;
            int countAfter = 0;
            for (int i = from; i <= to; i++) {
                countBefore += ops.get(i).kind() != '+' ? 1 : 0;
                countAfter += ops.get(i).kind() != '-' ? 1 : 0;
            }
            sb.append("@@ -").append(lineBefore[from]).append(',').append(countBefore)
                    .append(" +").append(lineAfter[from]).append(',').append(countAfter).append(" @@\n");
            for (int i = from; i <= to; i++) {
                if (shown == maxLines) {
                    sb.append("... (the rest of the change is not shown)\n");
                    return sb.toString();
                }
                sb.append(ops.get(i).kind()).append(ops.get(i).line()).append('\n');
                shown++;
            }
            h = last + 1;
        }
        return sb.toString();
    }

    /** One line of an edit script: kept ( ), removed (-) or added (+). */
    private record Op(char kind, String line) {
    }

    private static List<Op> editScript(List<String> before, List<String> after) {
        int n = before.size();
        int m = after.size();
        int start = 0;
        while (start < n && start < m && before.get(start).equals(after.get(start))) {
            start++;
        }
        int end = 0;
        while (end < n - start && end < m - start && before.get(n - 1 - end).equals(after.get(m - 1 - end))) {
            end++;
        }
        List<Op> ops = new ArrayList<>();
        for (int i = 0; i < start; i++) {
            ops.add(new Op(' ', before.get(i)));
        }
        List<String> x = before.subList(start, n - end);
        List<String> y = after.subList(start, m - end);
        if ((long) x.size() * y.size() <= MAX_DIFF_CELLS) {
            // the longest common subsequence of the changed middle
            int[][] lcs = new int[x.size() + 1][y.size() + 1];
            for (int i = x.size() - 1; i >= 0; i--) {
                for (int j = y.size() - 1; j >= 0; j--) {
                    lcs[i][j] = x.get(i).equals(y.get(j)) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            int i = 0;
            int j = 0;
            while (i < x.size() && j < y.size()) {
                if (x.get(i).equals(y.get(j))) {
                    ops.add(new Op(' ', x.get(i++)));
                    j++;
                } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                    ops.add(new Op('-', x.get(i++)));
                } else {
                    ops.add(new Op('+', y.get(j++)));
                }
            }
            while (i < x.size()) {
                ops.add(new Op('-', x.get(i++)));
            }
            while (j < y.size()) {
                ops.add(new Op('+', y.get(j++)));
            }
        } else {
            // too big to diff line by line: all of the middle removed and added
            x.forEach(line -> ops.add(new Op('-', line)));
            y.forEach(line -> ops.add(new Op('+', line)));
        }
        for (int i = n - end; i < n; i++) {
            ops.add(new Op(' ', before.get(i)));
        }
        return ops;
    }
}
