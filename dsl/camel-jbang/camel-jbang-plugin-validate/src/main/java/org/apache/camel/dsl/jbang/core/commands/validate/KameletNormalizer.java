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
package org.apache.camel.dsl.jbang.core.commands.validate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes a Kamelet file: its spec.template is replaced by the canonical YAML DSL of the route template the Kamelet
 * loads as, taken from a route template dump, and the rest of the file (metadata, definition, dependencies, comments)
 * is kept as written.
 */
final class KameletNormalizer {

    private static final Pattern TEMPLATE_ID = Pattern.compile("^ {4}id: \"?([^\"\\s]+)\"?\\s*$");

    private KameletNormalizer() {
    }

    /** Whether the content is a Kamelet: a document of kind Kamelet. */
    static boolean isKamelet(String content) {
        return content != null && Pattern.compile("(?m)^kind:\\s*Kamelet\\s*$").matcher(content).find();
    }

    /** The name of the Kamelet: metadata.name, else the file name without .kamelet.yaml. */
    static String kameletName(String content, String fileName) {
        boolean inMetadata = false;
        for (String line : content.split("\n", -1)) {
            if (line.startsWith("metadata:")) {
                inMetadata = true;
            } else if (inMetadata && !line.isBlank() && !line.startsWith(" ") && !line.startsWith("#")) {
                inMetadata = false;
            } else if (inMetadata) {
                Matcher m = Pattern.compile("^ {2}name:\\s*\"?([^\"\\s]+)\"?\\s*$").matcher(line);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.endsWith(".kamelet.yaml") ? name.substring(0, name.length() - ".kamelet.yaml".length()) : name;
    }

    /**
     * The Kamelet with its spec.template replaced by the beans and route of the route template of the same id in the
     * dump, or null when the dump has no such template or the Kamelet has no spec.template.
     */
    static String normalize(String kamelet, String dump, String templateId) {
        return normalize(kamelet, dump, templateId, new ArrayList<>());
    }

    /**
     * As {@link #normalize(String, String, String)}; the comments of the template are put back before the line they
     * were written before, and those that cannot be placed are added to lost (and kept at the end of the template).
     */
    static String normalize(String kamelet, String dump, String templateId, List<String> lost) {
        List<String> chunk = templateChunk(dump, templateId);
        if (chunk == null) {
            return null;
        }
        String[] lines = kamelet.split("\n", -1);
        int spec = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].matches("^spec:\\s*$")) {
                spec = i;
                break;
            }
        }
        if (spec < 0) {
            return null;
        }
        // the keys of spec are at the indent of its first key; template is one of them
        int template = -1;
        int templateIndent = -1;
        for (int i = spec + 1; i < lines.length; i++) {
            String l = lines[i];
            if (l.isBlank() || l.trim().startsWith("#")) {
                continue;
            }
            int indent = indent(l);
            if (indent == 0) {
                break;
            }
            if (templateIndent < 0) {
                templateIndent = indent;
            }
            if (indent == templateIndent && l.trim().matches("^template:\\s*$")) {
                template = i;
                break;
            }
        }
        if (template < 0) {
            return null;
        }
        int end = lines.length;
        for (int i = template + 1; i < lines.length; i++) {
            String l = lines[i];
            if (l.isBlank()) {
                continue;
            }
            if (indent(l) <= templateIndent) {
                end = i;
                break;
            }
        }
        // a template written as route: keeps that key, else from:
        boolean routeKey = false;
        for (int i = template + 1; i < end; i++) {
            if (lines[i].trim().startsWith("route:") && indent(lines[i]) > templateIndent) {
                routeKey = true;
                break;
            }
            if (lines[i].trim().startsWith("from:") && indent(lines[i]) > templateIndent) {
                break;
            }
        }
        // the children of template at the indent the file uses for them
        int child = templateIndent + 2;
        for (int i = template + 1; i < end; i++) {
            if (!lines[i].isBlank() && !lines[i].trim().startsWith("#")) {
                child = indent(lines[i]);
                break;
            }
        }
        List<String> body = new ArrayList<>();
        // the dump: "- routeTemplate:" at 0, its keys at 4 (id, parameters, beans, route), route's from at 6
        List<String> beans = block(chunk, "beans:", 4);
        if (beans != null) {
            body.addAll(shift(beans, child - 4));
        }
        List<String> route = block(chunk, "route:", 4);
        if (route == null) {
            return null;
        }
        if (routeKey) {
            body.addAll(shift(route, child - 4));
        } else {
            List<String> from = block(route, "from:", 6);
            if (from == null) {
                return null;
            }
            body.addAll(shift(from, child - 6));
        }
        // trailing blank lines of the template block belong to what follows it
        int bodyEnd = end;
        while (bodyEnd > template + 1 && lines[bodyEnd - 1].isBlank()) {
            bodyEnd--;
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i <= template; i++) {
            out.add(lines[i]);
        }
        // the dump is of the model, which has no comments: put those of the template back where they were
        List<String> original = new ArrayList<>();
        for (int i = template + 1; i < bodyEnd; i++) {
            original.add(lines[i]);
        }
        out.addAll(keepComments(original, body, child, lost));
        for (int i = bodyEnd; i < lines.length; i++) {
            out.add(lines[i]);
        }
        return String.join("\n", out);
    }

    /**
     * The normalized lines with the comments of the original put back: a comment block goes before the normalized line
     * that matches the line it was written before (a step such as - removeHeader:, or a key such as useHeaderSubject:).
     * The two are walked in order, as normalizing keeps the order of steps and keys. A block whose line is not found is
     * added to lost and kept at the end.
     */
    static List<String> keepComments(List<String> original, List<String> normalized, int indent, List<String> lost) {
        Map<Integer, List<String>> before = new TreeMap<>();
        List<String> block = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        int from = 0;
        for (String l : original) {
            String t = l.trim();
            if (t.startsWith("#")) {
                block.add(l);
                continue;
            }
            if (t.isEmpty()) {
                continue;
            }
            String signature = signature(t);
            int idx = find(normalized, signature, from, used);
            if (idx < 0 && !block.isEmpty()) {
                // an option the normalized form sorted earlier (parameters are in the catalog's order)
                idx = find(normalized, signature, 0, used);
            }
            if (idx >= 0) {
                used.add(idx);
                from = Math.max(from, idx + 1);
                if (!block.isEmpty()) {
                    before.computeIfAbsent(idx, k -> new ArrayList<>()).addAll(reindent(block, indent(normalized.get(idx))));
                }
            } else if (!block.isEmpty()) {
                // its line is gone (an option folded into a uri kept as written): after the last line found, which
                // is where it was in the original
                lost.addAll(block.stream().map(String::trim).toList());
                before.computeIfAbsent(from, k -> new ArrayList<>())
                        .addAll(reindent(block, from > 0 ? indent(normalized.get(from - 1)) : indent));
            }
            block.clear();
        }
        if (!block.isEmpty()) {
            // comments after the last line of the template: after the last line found
            before.computeIfAbsent(from, k -> new ArrayList<>())
                    .addAll(reindent(block, from > 0 ? indent(normalized.get(from - 1)) : indent));
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i <= normalized.size(); i++) {
            List<String> comments = before.get(i);
            if (comments != null) {
                out.addAll(comments);
            }
            if (i < normalized.size()) {
                out.add(normalized.get(i));
            }
        }
        return out;
    }

    /** What a line is, to find it after normalizing: "- key" for a step, "key" for a key, else the line. */
    private static String signature(String trimmed) {
        boolean step = trimmed.startsWith("- ");
        String t = step ? trimmed.substring(2).trim() : trimmed;
        int colon = t.indexOf(':');
        String key = colon > 0 && !t.startsWith("\"") && !t.startsWith("'") ? t.substring(0, colon) : t;
        return (step ? "- " : "") + key;
    }

    private static int find(List<String> lines, String signature, int from, Set<Integer> used) {
        for (int i = from; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (!t.isEmpty() && !t.startsWith("#") && !used.contains(i) && signature(t).equals(signature)) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> reindent(List<String> block, int indent) {
        int min = Integer.MAX_VALUE;
        for (String l : block) {
            min = Math.min(min, indent(l));
        }
        List<String> answer = new ArrayList<>();
        for (String l : block) {
            answer.add(" ".repeat(indent) + l.substring(min));
        }
        return answer;
    }

    /** The dump without the routeTemplates of the given ids: what is left are the routes of the other files. */
    static String withoutTemplates(String dump, Set<String> ids) {
        if (ids.isEmpty()) {
            return dump;
        }
        List<String> out = new ArrayList<>();
        List<String> item = new ArrayList<>();
        boolean drop = false;
        for (String l : dump.split("\n", -1)) {
            if (l.startsWith("- ")) {
                if (!drop) {
                    out.addAll(item);
                }
                item = new ArrayList<>();
                drop = false;
            }
            item.add(l);
            Matcher m = TEMPLATE_ID.matcher(l);
            if (!item.isEmpty() && item.get(0).startsWith("- routeTemplate:") && m.find() && ids.contains(m.group(1))) {
                drop = true;
            }
        }
        if (!drop) {
            out.addAll(item);
        }
        return String.join("\n", out).strip();
    }

    /** The lines of the routeTemplate with the given id in the dump, or null. */
    static List<String> templateChunk(String dump, String templateId) {
        if (dump == null) {
            return null;
        }
        List<String> current = null;
        boolean match = false;
        for (String l : dump.split("\n", -1)) {
            if (l.startsWith("- routeTemplate:")) {
                if (match) {
                    return current;
                }
                current = new ArrayList<>();
                match = false;
            }
            if (current != null) {
                current.add(l);
                Matcher m = TEMPLATE_ID.matcher(l);
                if (m.find() && m.group(1).equals(templateId)) {
                    match = true;
                }
            }
        }
        return match ? current : null;
    }

    /** The key line at the indent and the lines under it, or null when the key is not there. */
    static List<String> block(List<String> lines, String key, int indent) {
        List<String> answer = null;
        for (String l : lines) {
            if (answer == null) {
                if (indent(l) == indent && l.trim().equals(key)) {
                    answer = new ArrayList<>();
                    answer.add(l);
                }
            } else if (l.isBlank() || indent(l) > indent) {
                answer.add(l);
            } else {
                break;
            }
        }
        if (answer != null) {
            while (!answer.isEmpty() && answer.get(answer.size() - 1).isBlank()) {
                answer.remove(answer.size() - 1);
            }
        }
        return answer;
    }

    private static List<String> shift(List<String> lines, int by) {
        List<String> answer = new ArrayList<>();
        for (String l : lines) {
            if (l.isBlank()) {
                answer.add("");
            } else if (by >= 0) {
                answer.add(" ".repeat(by) + l);
            } else {
                answer.add(l.substring(Math.min(-by, indent(l))));
            }
        }
        return answer;
    }

    private static int indent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }
}
