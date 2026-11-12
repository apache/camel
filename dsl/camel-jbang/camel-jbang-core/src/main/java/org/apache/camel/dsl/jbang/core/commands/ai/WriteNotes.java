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
import org.apache.camel.support.LanguageSupport;
import org.snakeyaml.engine.v2.nodes.Node;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * What a written route does that works but is probably not what was asked, said as a note of the write: the route can
 * be right, so the write is not refused. A to: that copies each file its file consumer then moves to .camel, a to: that
 * appends to a file with nothing between the messages, a request-reply over JMS whose reply replaces the message that
 * was sent, and a literal written over a {{key}} placeholder that leaves the key in application.properties unused.
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

    /**
     * The components of a message broker over JMS: a to: with InOut on them sends a request and waits for the reply.
     */
    private static final Set<String> JMS_SCHEMES = Set.of("jms", "sjms", "sjms2", "activemq", "activemq6", "amqp");

    /** The steps that set the body themselves: after them the route has chosen what the body is. */
    private static final Set<String> BODY_STEPS = Set.of("setBody", "transform");

    /** The steps that run only when something fails: a body they set is not the body of the main path. */
    private static final Set<String> EXCEPTION_PATHS = Set.of("doCatch", "onException", "onFallback");

    /** A destination name that can be the start of a variable name: stock gives stockReply. */
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

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

    /** The appendChars option in a uri, with its value up to the next option. */
    private static final Pattern APPEND_CHARS = Pattern.compile("(?i)([?&]appendChars=)[^&]*");

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
        } catch (RuntimeException e) {
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
     * A to: or toD: on the file component with fileExist Append that adds nothing after each message: the messages run
     * together in the file, so lines written one per message become one line. r32 of the local-model benchmark,
     * route-batch-file step 2 ("append each line that cannot be read to rejects/<file name>, one per line"): in 4 of
     * the 5 runs that wrote the route, the lines ran together. 3 had no appendChars, and one had appendChars: "\n". In
     * YAML that is a line break, and the endpoint uri drops line breaks, so it adds nothing (the run logged the
     * endpoint as appendChars=&fileExist=Append). In step 3, 4 of the 7 runs rewrote the file and turned the
     * appendChars: "\\n" of the reference into "\n" the same way. A \n in a toD with ${...} adds nothing too: the toD
     * turns it into a line break first. A body that already ends with a line break gets no note. Only the file
     * component has appendChars (ftp, sftp and the other file components leave it out). Each note is "Line N: ..." for
     * the line of the to:.
     */
    static List<String> fileAppendNotes(String content) {
        List<String> notes = new ArrayList<>();
        if (content == null || !content.toLowerCase(Locale.ROOT).contains("append")) {
            return notes;
        }
        Object doc;
        try {
            doc = new Yaml(new SafeConstructor(new LoaderOptions())).load(content);
        } catch (RuntimeException e) {
            return notes;
        }
        if (!(doc instanceof List<?> items)) {
            return notes;
        }
        try {
            // the pointer of each such to:, with its note
            Map<String, String> appends = new LinkedHashMap<>();
            for (int i = 0; i < items.size(); i++) {
                appendNested(items.get(i), "/" + i, false, appends);
            }
            Node root = appends.isEmpty() ? null : YamlPointerLines.root(content);
            for (Map.Entry<String, String> e : appends.entrySet()) {
                int line = YamlPointerLines.line(root, e.getKey(), null);
                notes.add((line > 0 ? "Line " + line + ": " : "") + e.getValue());
            }
        } catch (RuntimeException e) {
            // a shape the walk does not know: a note must never fail the write
        }
        return notes;
    }

    /**
     * Walks the steps: lists in the node: those of a route or a routeConfiguration, and those of the EIPs of a route
     * (the when: of a choice, a doCatch). lineEnds is whether the body ends with a line break where the node starts.
     */
    private static void appendNested(Object node, String at, boolean lineEnds, Map<String, String> appends) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                if ("steps".equals(key) && e.getValue() instanceof List<?> steps) {
                    appendSteps(steps, at + "/steps", lineEnds, appends);
                } else {
                    appendNested(e.getValue(), at + "/" + key, lineEnds, appends);
                }
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                appendNested(list.get(i), at + "/" + i, lineEnds, appends);
            }
        }
    }

    /**
     * Adds the note of each to: or toD: of the steps that appends to a file with nothing after each message, and walks
     * the steps of the EIPs among them. The steps before a to: tell whether the body already ends with a line break: a
     * setBody or transform whose Simple text ends with \n, or marshal with csv, which ends each record with one. The
     * steps of a split start with a part of the body, which has none.
     */
    private static void appendSteps(List<?> steps, String at, boolean lineEnds, Map<String, String> appends) {
        boolean ends = lineEnds;
        for (int j = 0; j < steps.size(); j++) {
            if (!(steps.get(j) instanceof Map<?, ?> step) || step.size() != 1) {
                continue;
            }
            String eip = String.valueOf(step.keySet().iterator().next());
            Object value = step.values().iterator().next();
            if ("setBody".equals(eip) || "transform".equals(eip)) {
                ends = endsWithLineBreak(value);
            } else if ("marshal".equals(eip) || "unmarshal".equals(eip)) {
                ends = "marshal".equals(eip) && value instanceof Map<?, ?> m && m.containsKey("csv");
            } else if ("to".equals(eip) || "toD".equals(eip)) {
                String note = ends ? null : appendNote(eip, value);
                if (note != null) {
                    appends.put(at + "/" + j, note);
                }
            } else {
                appendNested(value, at + "/" + j + "/" + eip, ends && !"split".equals(eip), appends);
            }
        }
    }

    /**
     * Whether a setBody or transform ends the body with a line break: Simple text that ends with \n, the two characters
     * that Simple turns into a line break. A line break of the YAML itself does not count: Simple trims it away (r32
     * wrote "${variable.originalLine}\n" in double quotes, and the lines still ran together).
     */
    private static boolean endsWithLineBreak(Object setBody) {
        Object expression = setBody instanceof Map<?, ?> m && m.get("expression") instanceof Map<?, ?> e ? e : setBody;
        Object simple = expression instanceof Map<?, ?> m ? m.get("simple") : null;
        Object text = simple instanceof Map<?, ?> s ? s.get("expression") : simple;
        return text instanceof String s && s.strip().endsWith("\\n");
    }

    /**
     * The note of a to: or toD: on the file component with fileExist Append whose appendChars adds nothing after each
     * message; null for any other. The value it gives is one that works where it goes. Under parameters: that is
     * appendChars: "\\n", the two characters \n that the file component turns into a line break. In a uri it is %5Cn,
     * which is \n as a uri writes it and needs no escape whatever the quotes of the uri. A toD with ${...} gets %5Cn
     * too: it reads its uri as Simple, which turns \n into a line break that the uri then drops.
     */
    private static String appendNote(String eip, Object endpoint) {
        FileEndpoint target = fileEndpoint(endpoint);
        Object fileExist = target != null ? optionValue(target.options(), "fileExist") : null;
        // Camel reads the value in any case: append, APPEND
        if (fileExist == null || !"Append".equalsIgnoreCase(String.valueOf(fileExist).strip())) {
            return null;
        }
        String uri = String.valueOf(endpoint instanceof Map<?, ?> m ? m.get("uri") : endpoint).strip();
        // the toD uses Simple for its uri only when it has a function, as the runtime decides
        boolean simple = "toD".equals(eip) && (LanguageSupport.hasSimpleFunction(uri)
                || LanguageSupport.hasSimpleFunction(target.directory())
                || target.options().values().stream().anyMatch(v -> LanguageSupport.hasSimpleFunction(String.valueOf(v))));
        Object appendChars = optionValue(target.options(), "appendChars");
        String chars = appendChars != null ? String.valueOf(appendChars) : "";
        String read = simple ? chars.replace("\\r", "\r").replace("\\n", "\n") : chars;
        // the endpoint uri is read as one line: its line breaks go, with the spaces around them
        if (!read.isEmpty() && !(read.isBlank() && (read.indexOf('\n') >= 0 || read.indexOf('\r') >= 0))) {
            return null;
        }
        String fix;
        if (target.parameters()) {
            fix = (appendChars == null ? "add" : "write") + " appendChars: " + (simple ? "\"%5Cn\"" : "\"\\\\n\"")
                  + " under parameters: of this " + eip + ":";
        } else {
            String written = appendChars == null
                    ? uri + (uri.contains("?") ? "&" : "?") + "appendChars=%5Cn"
                    : APPEND_CHARS.matcher(uri).replaceFirst("$1%5Cn");
            fix = "write the uri of this " + eip + ": as " + written;
        }
        return (chars.indexOf('\n') >= 0 || chars.indexOf('\r') >= 0
                ? "this appendChars is a line break in the YAML, and the endpoint uri drops line breaks, so " : "")
               + "fileExist Append adds each message right after the one before, with nothing between them. To write"
               + " one message per line, " + fix + ", which writes a line break after each message."
               + (simple
                       ? " In a toD with ${...}, write \\n as %5Cn: the toD turns \\n into a line break, which the uri"
                         + " drops."
                       : target.parameters() ? "" : " %5Cn is \\n as a uri writes it.");
    }

    /** The value of the option whatever the case of its name, as Camel reads fileexist as fileExist; null for none. */
    private static Object optionValue(Map<String, Object> options, String name) {
        for (Map.Entry<String, Object> e : options.entrySet()) {
            if (name.equalsIgnoreCase(e.getKey().strip())) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * A request-reply over JMS whose reply replaces the body: a to: on a JMS endpoint with InOut (exchangePattern=InOut
     * on the endpoint, pattern: InOut on the to:, or a setExchangePattern before it) and no variableReceive. After it
     * the body is the reply, and the message that was sent is gone. r23 to r26 of the local-model benchmark,
     * connect-service-jms-request-reply step 2 ("keep the order as the body"): every failed run logged the reply right
     * and lost the order, and no answer said that the body is now the reply. A route that sets the body itself after
     * the reply, on the main path, has chosen what the body is, and gets no note. Each note is "Line N: ..." for the
     * line of the to:.
     */
    static List<String> requestReplyNotes(String content) {
        List<String> notes = new ArrayList<>();
        if (content == null || !content.contains("InOut")) {
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
        try {
            // the pointer of each such to:, with the destination it sends to
            Map<String, String> replies = new LinkedHashMap<>();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) instanceof Map<?, ?> m && m.get("route") instanceof Map<?, ?> route
                        && route.get("from") instanceof Map<?, ?> from && from.get("steps") instanceof List<?> steps) {
                    requestReplies(steps, "/" + i + "/route/from/steps", false, false, replies);
                } else if (items.get(i) instanceof Map<?, ?> m && m.get("from") instanceof Map<?, ?> from
                        && from.get("steps") instanceof List<?> steps) {
                    requestReplies(steps, "/" + i + "/from/steps", false, false, replies);
                }
            }
            Node root = replies.isEmpty() ? null : YamlPointerLines.root(content);
            for (Map.Entry<String, String> e : replies.entrySet()) {
                int line = YamlPointerLines.line(root, e.getKey(), null);
                String destination = e.getValue();
                String variable = WORD.matcher(destination).matches() ? destination + "Reply" : "reply";
                notes.add((line > 0 ? "Line " + line + ": " : "") + "after this request-reply (InOut) the body is the"
                          + " reply" + (destination.isEmpty() ? "" : " from " + destination) + ", and the message that"
                          + " was sent is gone. To keep the message as the body, add variableReceive: " + variable
                          + " to this to: and read the reply as ${variable." + variable + "}.");
            }
        } catch (RuntimeException e) {
            // a shape the walk does not know: a note must never fail the write
        }
        return notes;
    }

    /**
     * Adds the pointer and the destination of each to: or toD: of the steps that is a request-reply over JMS whose
     * reply replaces the body. inOut is whether a setExchangePattern before the steps made the exchange InOut, and
     * bodySetAfter whether a step after them on the main path sets the body (a step after the EIP that holds them, in a
     * list above). The steps of the EIPs among them (filter, split, the when: of a choice, doTry) are walked too.
     */
    private static void requestReplies(
            List<?> steps, String at, boolean inOut, boolean bodySetAfter, Map<String, String> replies) {
        boolean exchangeInOut = inOut;
        for (int j = 0; j < steps.size(); j++) {
            if (!(steps.get(j) instanceof Map<?, ?> step) || step.size() != 1) {
                continue;
            }
            String eip = String.valueOf(step.keySet().iterator().next());
            Object value = step.values().iterator().next();
            // a step after this one sets the body: also a setBody in one branch of a choice, as the note only shows
            // when it is clearly right
            boolean bodySet = bodySetAfter || setsBody(steps.subList(j + 1, steps.size()));
            if ("setExchangePattern".equals(eip)) {
                // setExchangePattern: InOut, or with pattern: InOut; InOnly turns it off again
                exchangeInOut = isInOut(value instanceof Map<?, ?> m ? m.get("pattern") : value);
            } else if ("to".equals(eip) || "toD".equals(eip)) {
                String destination = jmsRequestReply(value, exchangeInOut);
                if (destination != null && !bodySet) {
                    replies.put(at + "/" + j, destination);
                }
            } else {
                nestedSteps(value, at + "/" + j + "/" + eip, exchangeInOut, bodySet, replies);
            }
        }
    }

    /**
     * Walks the steps: lists inside an EIP, such as the when: items of a choice. The doFinally of a doTry runs after
     * its steps and its doCatch, as the steps after the doTry do.
     */
    private static void nestedSteps(
            Object node, String at, boolean inOut, boolean bodySetAfter, Map<String, String> replies) {
        if (node instanceof Map<?, ?> m) {
            boolean bodySet = bodySetAfter || setsBody(m.get("doFinally"));
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                boolean after = "doFinally".equals(key) ? bodySetAfter : bodySet;
                if ("steps".equals(key) && e.getValue() instanceof List<?> steps) {
                    requestReplies(steps, at + "/steps", inOut, after, replies);
                } else {
                    nestedSteps(e.getValue(), at + "/" + key, inOut, after, replies);
                }
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                nestedSteps(list.get(i), at + "/" + i, inOut, bodySetAfter, replies);
            }
        }
    }

    /**
     * The destination of a to: on a JMS endpoint that sends InOut and has no variableReceive, such as stock for
     * jms:queue:stock (the empty text when it cannot be told); null for any other to:. The pattern is the one the
     * runtime sends with: exchangePattern of the endpoint wins over pattern: of the to: (as in SendProcessor), then
     * comes the pattern of the exchange; and disableReplyTo sends InOnly whatever the pattern.
     */
    private static String jmsRequestReply(Object endpoint, boolean inOut) {
        String uri;
        Map<?, ?> parameters = null;
        Object pattern = null;
        if (endpoint instanceof String s) {
            uri = s;
        } else if (endpoint instanceof Map<?, ?> m && m.get("uri") instanceof String s) {
            if (m.get("variableReceive") != null) {
                return null;
            }
            uri = s;
            parameters = m.get("parameters") instanceof Map<?, ?> p ? p : null;
            pattern = m.get("pattern");
        } else {
            return null;
        }
        uri = uri.strip();
        int colon = uri.indexOf(':');
        if (!JMS_SCHEMES.contains(colon > 0 ? uri.substring(0, colon) : uri)) {
            return null;
        }
        String path = colon > 0 ? uri.substring(colon + 1) : "";
        // the options of the endpoint, in the uri or under parameters:
        Map<String, Object> options = new LinkedHashMap<>();
        int question = path.indexOf('?');
        if (question >= 0) {
            for (String pair : path.substring(question + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    options.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
            path = path.substring(0, question);
        }
        if (parameters != null) {
            parameters.forEach((k, v) -> options.put(String.valueOf(k), v));
        }
        Object exchangePattern = options.get("exchangePattern");
        boolean sendsInOut = exchangePattern != null ? isInOut(exchangePattern) : pattern != null ? isInOut(pattern) : inOut;
        Object disableReplyTo = options.get("disableReplyTo");
        if (!sendsInOut || disableReplyTo != null && !"false".equals(String.valueOf(disableReplyTo).strip())) {
            return null;
        }
        String destination = path.startsWith("//") ? path.substring(2) : path;
        for (String type : List.of("queue:", "topic:", "temp-queue:", "temp-topic:")) {
            if (destination.startsWith(type)) {
                destination = destination.substring(type.length());
            }
        }
        if (destination.isBlank() && parameters != null && parameters.get("destinationName") != null) {
            destination = String.valueOf(parameters.get("destinationName"));
        }
        return destination.strip();
    }

    private static boolean isInOut(Object pattern) {
        return pattern != null && "InOut".equals(String.valueOf(pattern).strip());
    }

    /**
     * Whether a step of these, or of the EIPs among them, sets the body itself (setBody, transform); not a step that
     * runs only when something fails (doCatch).
     */
    private static boolean setsBody(Object node) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (BODY_STEPS.contains(key) || !EXCEPTION_PATHS.contains(key) && setsBody(e.getValue())) {
                    return true;
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object o : list) {
                if (setsBody(o)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A key of application.properties that a literal written over its {{key}} left unused: the option it was the value
     * of, the line of the route that has the literal now, and the literal (null when it cannot be told).
     */
    record UnusedProperty(String key, String properties, String option, int line, String value) {

        /** The note of the write: what is unused, and how the value is changed instead. */
        String note() {
            return key + " in " + properties + " is no longer used by any route. To change the " + option + ", change "
                   + key + " in " + properties + " and keep {{" + key + "}} in the route.";
        }

        /**
         * What to do next, for the start of the answer: as a note at its end, a model took it for information (r26 of
         * the local-model benchmark, run-nightly-report step 4: the 3 failed runs ended the step with the note repeated
         * in their own words).
         */
        String instruction() {
            String where = line > 0 ? "line " + line : "the route";
            return "Use {{" + key + "}} again on " + where + ", and set "
                   + (value != null ? key + "=" + value : "the new value as " + key) + " in " + properties
                   + ": the property is now unused, as " + where + " has its own value.";
        }
    }

    /** The notes of {@link #unusedProperties(Path, String, String, String)}. */
    static List<String> unusedPropertyNotes(Path dir, String file, String before, String after) {
        return unusedProperties(dir, file, before, after).stream().map(UnusedProperty::note).toList();
    }

    /**
     * A literal written over a {{key}} placeholder of the route that leaves the key of application.properties unused,
     * as the change of a value that the properties hold was meant (r24 and r25 of the local-model benchmark: asked to
     * change the schedule, 4 runs wrote the cron in the route over {{report.schedule}}). Only for a placeholder
     * replaced in its line (the text before it is still there), a key no other file of the project uses, and a key the
     * runtime does not read itself (camel.*, quarkus.*).
     */
    static List<UnusedProperty> unusedProperties(Path dir, String file, String before, String after) {
        List<UnusedProperty> answer = new ArrayList<>();
        String name = file != null ? file.toLowerCase(Locale.ROOT) : "";
        if (dir == null || before == null || after == null || !before.contains("{{")
                || !(name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".xml") || name.endsWith(".java"))) {
            return answer;
        }
        // the keys the change removed, each with the text before and after its placeholder on its line
        Map<String, String[]> removed = new LinkedHashMap<>();
        Set<String> lines = new HashSet<>();
        for (String line : before.split("\n", -1)) {
            lines.add(lead(line));
            Matcher m = PLACEHOLDER.matcher(line);
            while (m.find()) {
                String key = m.group(1);
                if (!uses(after, key)) {
                    removed.putIfAbsent(key,
                            new String[] { lead(line.substring(0, m.start())), line.substring(m.end()).strip() });
                }
            }
        }
        String[] afterLines = after.split("\n", -1);
        // the changed lines that belong to a key: two keys with the same text before them (schedule: " in two routes)
        // get the changed lines in their order
        Set<Integer> taken = new HashSet<>();
        try {
            for (Map.Entry<String, String[]> e : removed.entrySet()) {
                String key = e.getKey();
                String lead = e.getValue()[0];
                if (lead.isEmpty()) {
                    continue;
                }
                // replaced in its line: a changed line still has the text before the placeholder (a route removed as a
                // whole has none)
                int changed = -1;
                for (int i = 0; i < afterLines.length && changed < 0; i++) {
                    String l = lead(afterLines[i]);
                    if (l.startsWith(lead) && !lines.contains(l) && taken.add(i)) {
                        changed = i;
                    }
                }
                if (RUNTIME_KEYS.stream().anyMatch(key::startsWith) || changed < 0) {
                    continue;
                }
                String properties = definedIn(dir, key);
                if (properties != null && !usedElsewhere(dir, key)) {
                    answer.add(new UnusedProperty(
                            key, properties, option(lead), changed + 1,
                            literal(lead(afterLines[changed]), lead, e.getValue()[1])));
                }
            }
        } catch (RuntimeException e) {
            // the project cannot be listed: a note must never fail the write
        }
        return answer;
    }

    /**
     * The literal written in place of a placeholder: the text of the changed line between the text that was before the
     * placeholder and the text that was after it; null when the line changed elsewhere too.
     */
    private static String literal(String line, String lead, String tail) {
        String rest = line.substring(lead.length());
        if (!rest.endsWith(tail)) {
            return null;
        }
        String value = rest.substring(0, rest.length() - tail.length()).strip();
        return value.isEmpty() || value.contains("{{") ? null : value;
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
