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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.dsl.yaml.validator.YamlPointerLines;
import org.snakeyaml.engine.v2.nodes.Node;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * What a written route does that works but is probably not what was asked, said as a note of the write: the route can
 * be right, so the write is not refused. A to: that copies each file its file consumer then moves to .camel, and a
 * literal written over a {{key}} placeholder that leaves the key in application.properties unused.
 */
final class WriteNotes {

    /**
     * The options of a file consumer that decide where the file goes after the route, and charset, with which a copy is
     * written in another charset (as the file component's doc shows): a route with any of them is left alone.
     */
    private static final Set<String> CONSUMER_OPTIONS
            = Set.of("noop", "move", "delete", "moveFailed", "preMove", "charset");

    /** The steps that leave the body as the file consumer gave it. */
    private static final Set<String> NEUTRAL_STEPS = Set.of(
            "log", "setHeader", "setHeaders", "setProperty", "setVariable", "setVariables", "removeHeader",
            "removeHeaders", "removeProperty", "removeProperties", "removeVariable", "delay");

    /** A {{key}} property placeholder, with an optional ? in front and a default value after a colon. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\??([\\w.-]+)(?::[^}]*)?}}");

    /** The keys the runtime reads itself, not only through a placeholder: camel.main.*, quarkus.http.port. */
    private static final List<String> RUNTIME_KEYS = List.of("camel.", "quarkus.", "spring.");

    /** The files where another use of a key is looked for: routes, beans, Java, scripts, templates and properties. */
    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            "yaml", "yml", "xml", "java", "groovy", "kt", "kts", "js", "py", "properties", "ftl", "vm", "mustache", "jslt",
            "jsonata");

    /** The properties files Camel reads, with their profiles: application.properties, application-dev.properties. */
    private static final Pattern PROPERTIES_FILE = Pattern.compile("application(-[\\w.-]+)?\\.properties");

    /** A file larger than this is not read: it counts as a use of the key. */
    private static final long MAX_SOURCE_SIZE = 512 * 1024;

    /** The option a placeholder is the value of, in a uri (?schedule=) or as a YAML key (schedule: "). */
    private static final Pattern URI_OPTION = Pattern.compile("[?&]([\\w.-]+)=$");
    private static final Pattern YAML_KEY = Pattern.compile("^([\\w.-]+):\\s*[\"']?$");

    private WriteNotes() {
    }

    /**
     * A file endpoint: its directory, its options from the uri and from parameters:, and whether it has parameters:.
     */
    private record FileEndpoint(String directory, Map<String, Object> options, boolean parameters) {
    }

    /**
     * A to: file:done after a file consumer that moves its files (no noop, move or delete), in a route that does
     * nothing else to the file: the to: writes a copy, and the consumer still moves each file to .camel. r24 and r25 of
     * the local-model benchmark, asked to move the processed files to done, wrote that 4 times; a fifth run took
     * ".done" from the description of move, a hidden folder, which the note says too. Each note is "Line N: ..." for
     * the line of the to:.
     */
    static List<String> fileCopyNotes(String content) {
        List<String> notes = new ArrayList<>();
        if (content == null || !content.contains("file")) {
            return notes;
        }
        Object doc;
        try {
            doc = new Yaml(new SafeConstructor(new LoaderOptions())).load(content);
        } catch (Exception e) {
            return notes;
        }
        if (!(doc instanceof List<?> items)) {
            return notes;
        }
        Node root = null;
        try {
            for (int i = 0; i < items.size(); i++) {
                Map<?, ?> from = null;
                String at = null;
                if (items.get(i) instanceof Map<?, ?> m && m.get("route") instanceof Map<?, ?> route
                        && route.get("from") instanceof Map<?, ?> f) {
                    from = f;
                    at = "/" + i + "/route/from";
                } else if (items.get(i) instanceof Map<?, ?> m && m.get("from") instanceof Map<?, ?> f) {
                    from = f;
                    at = "/" + i + "/from";
                }
                FileEndpoint consumer = from != null ? fileEndpoint(from) : null;
                if (consumer == null || consumer.directory().isEmpty() || dynamic(consumer.directory())
                        || consumer.options().keySet().stream().anyMatch(k -> CONSUMER_OPTIONS.contains(k) || dynamic(k))
                        || !(from.get("steps") instanceof List<?> steps)) {
                    continue;
                }
                int copy = copyStep(steps, consumer);
                if (copy >= 0) {
                    if (root == null) {
                        root = YamlPointerLines.root(content);
                    }
                    int line = YamlPointerLines.line(root, at + "/steps/" + copy, null);
                    Map<?, ?> step = (Map<?, ?>) steps.get(copy);
                    notes.add((line > 0 ? "Line " + line + ": " : "")
                              + copyNote(consumer, fileEndpoint(step.values().iterator().next()), from));
                }
            }
        } catch (RuntimeException e) {
            // a shape the walk does not know: a note must never fail the write
        }
        return notes;
    }

    /**
     * The index of the one step that writes the file as it was read to another folder, a to: or toD: file with no
     * option, when every other step leaves the body and the file name as they are; -1 otherwise. A route that changes
     * the body (a transformed file) or the name of the copy, or sends it elsewhere too, writes the copy for a reason.
     */
    private static int copyStep(List<?> steps, FileEndpoint consumer) {
        int copy = -1;
        for (int j = 0; j < steps.size(); j++) {
            if (!(steps.get(j) instanceof Map<?, ?> step) || step.size() != 1) {
                return -1;
            }
            String eip = String.valueOf(step.keySet().iterator().next());
            Object value = step.values().iterator().next();
            if (NEUTRAL_STEPS.contains(eip)) {
                // a header such as CamelFileName names the copy
                if (eip.startsWith("setHeader") && String.valueOf(value).contains("FileName")) {
                    return -1;
                }
                continue;
            }
            if (!"to".equals(eip) && !"toD".equals(eip)) {
                return -1;
            }
            FileEndpoint target = fileEndpoint(value);
            if (target == null && isLog(value)) {
                continue;
            }
            if (copy >= 0 || target == null || !target.options().isEmpty() || !plainFolder(target.directory())
                    || folder(target.directory()).equals(folder(consumer.directory()))) {
                return -1;
            }
            copy = j;
        }
        return copy;
    }

    private static String copyNote(FileEndpoint consumer, FileEndpoint target, Map<?, ?> from) {
        String dir = folder(consumer.directory());
        String to = folder(target.directory());
        // move is relative to the folder the consumer reads, as .camel is
        String move = to.startsWith(dir + "/") ? to.substring(dir.length() + 1) : to;
        String uri = String.valueOf(from.get("uri")).strip();
        String set = consumer.parameters()
                ? "add move: " + move + " under parameters: of the from:"
                : "write the uri of the from: as " + uri + (uri.contains("?") ? "&" : "?") + "move=" + move;
        return "this to: writes a copy of each file to " + to + ", while the file consumer still moves the file itself to "
               + dir + "/.camel after the route (its default). To move the file to " + to + " instead, remove this to:"
               + " and " + set + " (the file then goes to " + dir + "/" + move + "). A move value that starts with a"
               + " dot, such as ." + (move.matches("[\\w-]+") ? move : "done") + ", is a hidden folder.";
    }

    /** The file endpoint of a from:, to: or toD: (a uri, or uri: with parameters:), or null for another scheme. */
    private static FileEndpoint fileEndpoint(Object endpoint) {
        String uri;
        Map<?, ?> parameters = null;
        if (endpoint instanceof String s) {
            uri = s;
        } else if (endpoint instanceof Map<?, ?> m && m.get("uri") instanceof String s) {
            uri = s;
            parameters = m.get("parameters") instanceof Map<?, ?> p ? p : null;
        } else {
            return null;
        }
        uri = uri.strip();
        if (!"file".equals(uri) && !uri.startsWith("file:")) {
            return null;
        }
        String path = "file".equals(uri) ? "" : uri.substring("file:".length());
        path = path.startsWith("//") ? path.substring(2) : path;
        Map<String, Object> options = new LinkedHashMap<>();
        int question = path.indexOf('?');
        if (question >= 0) {
            for (String pair : path.substring(question + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (!pair.isEmpty()) {
                    options.put(eq > 0 ? pair.substring(0, eq) : pair, eq > 0 ? pair.substring(eq + 1) : "");
                }
            }
            path = path.substring(0, question);
        }
        if (parameters != null) {
            parameters.forEach((k, v) -> options.put(String.valueOf(k), v));
        }
        Object directoryName = options.remove("directoryName");
        if (path.isBlank() && directoryName != null) {
            path = String.valueOf(directoryName);
        }
        return new FileEndpoint(path.strip(), options, parameters != null);
    }

    private static boolean isLog(Object endpoint) {
        Object uri = endpoint instanceof Map<?, ?> m ? m.get("uri") : endpoint;
        return uri instanceof String s && s.strip().startsWith("log:");
    }

    /** A folder named in the project, relative and with no .. or expression: the ones move can name the same way. */
    private static boolean plainFolder(String directory) {
        String d = folder(directory);
        return !d.isEmpty() && !d.startsWith("/") && !d.contains("..") && !dynamic(d);
    }

    private static String folder(String directory) {
        String d = directory.strip();
        while (d.startsWith("./")) {
            d = d.substring(2);
        }
        while (d.endsWith("/") && d.length() > 1) {
            d = d.substring(0, d.length() - 1);
        }
        return d;
    }

    private static boolean dynamic(String text) {
        return text.contains("{{") || text.contains("${");
    }

    /**
     * A literal written over a {{key}} placeholder of the route that leaves the key of application.properties unused,
     * as the change of a value that the properties hold was meant (r24 and r25 of the local-model benchmark: asked to
     * change the schedule, 4 runs wrote the cron in the route over {{report.schedule}}). Only for a placeholder
     * replaced in its line (the text before it is still there), a key no other file of the project uses, and a key the
     * runtime does not read itself (camel.*, quarkus.*).
     */
    static List<String> unusedPropertyNotes(Path dir, String file, String before, String after) {
        List<String> notes = new ArrayList<>();
        String name = file != null ? file.toLowerCase(Locale.ROOT) : "";
        if (dir == null || before == null || after == null || !before.contains("{{")
                || !(name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".xml") || name.endsWith(".java"))) {
            return notes;
        }
        // the keys the change removed, each with the text before its placeholder on its line
        Map<String, String> removed = new LinkedHashMap<>();
        Set<String> lines = new HashSet<>();
        for (String line : before.split("\n", -1)) {
            lines.add(lead(line));
            Matcher m = PLACEHOLDER.matcher(line);
            while (m.find()) {
                String key = m.group(1);
                if (!uses(after, key)) {
                    removed.putIfAbsent(key, lead(line.substring(0, m.start())));
                }
            }
        }
        try {
            for (Map.Entry<String, String> e : removed.entrySet()) {
                String key = e.getKey();
                String lead = e.getValue();
                // replaced in its line: a changed line still has the text before the placeholder (a route removed as a
                // whole has none)
                if (RUNTIME_KEYS.stream().anyMatch(key::startsWith) || lead.isEmpty()
                        || after.lines().map(WriteNotes::lead).noneMatch(l -> l.startsWith(lead) && !lines.contains(l))) {
                    continue;
                }
                String properties = definedIn(dir, key);
                if (properties != null && !usedElsewhere(dir, key)) {
                    notes.add(key + " in " + properties + " is no longer used by any route. To change the "
                              + option(lead) + ", change " + key + " in " + properties + " and keep {{" + key
                              + "}} in the route.");
                }
            }
        } catch (RuntimeException e) {
            // the project cannot be listed: a note must never fail the write
        }
        return notes;
    }

    /** A line without its indentation and list dash: the same text wherever the line moved. */
    private static String lead(String line) {
        String t = line.strip();
        return t.startsWith("- ") ? t.substring(2).strip() : t;
    }

    /** The option the placeholder was the value of: schedule for schedule: "{{key}}" or ?schedule={{key}}. */
    private static String option(String lead) {
        Matcher query = URI_OPTION.matcher(lead);
        if (query.find()) {
            return query.group(1);
        }
        Matcher key = YAML_KEY.matcher(lead);
        return key.find() ? key.group(1) : "value";
    }

    /** Whether the text names the key, in a placeholder or anywhere else (key.other is another key). */
    private static boolean uses(String text, String key) {
        return text.contains(key) && token(key).matcher(text).find();
    }

    private static Pattern token(String key) {
        return Pattern.compile("(?<![\\w.-])" + Pattern.quote(key) + "(?![\\w-]|\\.\\w)");
    }

    /** The line of a properties file that sets the key, in a profile too: key=, %dev.key=. */
    private static Pattern definition(String key) {
        return Pattern.compile("^\\s*(%[\\w-]+\\.)?" + Pattern.quote(key) + "\\s*[=:]");
    }

    /** The properties file that defines the key, relative to the directory; null when none does. */
    private static String definedIn(Path dir, String key) {
        Pattern definition = definition(key);
        for (Path folder : List.of(dir, dir.resolve("src/main/resources"))) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> list = Files.list(folder)) {
                files = list.filter(f -> PROPERTIES_FILE.matcher(f.getFileName().toString()).matches()).sorted().toList();
            } catch (IOException e) {
                continue;
            }
            for (Path f : files) {
                try {
                    if (Files.readAllLines(f).stream().anyMatch(l -> definition.matcher(l).find())) {
                        return AuthoringTools.relativePath(dir, f);
                    }
                } catch (IOException e) {
                    // not readable: not known to define it
                }
            }
        }
        return null;
    }

    /**
     * Whether a file of the project uses the key: a route, a bean, a Java class, a script or template, or a property
     * that refers to it (its own definition is no use). The file just written has it no more. A file that cannot be
     * read counts as a use, so nothing is said that might not be true.
     */
    private static boolean usedElsewhere(Path dir, String key) {
        Pattern token = token(key);
        Pattern definition = definition(key);
        for (Path p : AuthoringTools.projectFiles(dir)) {
            String fileName = p.getFileName().toString();
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (!SOURCE_EXTENSIONS.contains(extension)) {
                continue;
            }
            try {
                if (Files.size(p) > MAX_SOURCE_SIZE) {
                    return true;
                }
                String text = Files.readString(p);
                if (!text.contains(key)) {
                    continue;
                }
                boolean properties = "properties".equals(extension);
                for (String line : text.split("\n", -1)) {
                    if (token.matcher(line).find() && !(properties && definition.matcher(line).find())) {
                        return true;
                    }
                }
            } catch (IOException e) {
                return true;
            }
        }
        return false;
    }
}
