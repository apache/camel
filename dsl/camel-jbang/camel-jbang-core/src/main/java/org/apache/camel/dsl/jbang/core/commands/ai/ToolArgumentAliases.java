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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The names models give the arguments of a tool when they carry them over from another tool (CAMEL-26399):
 * {@code camel_catalog_doc} takes {@code name}, so {@code camel_catalog_find} gets {@code name} or {@code query} for
 * its {@code term}, and {@code camel_validate_source} gets {@code filename} or {@code source} for its {@code file} and
 * {@code content}. Such a call runs as if it had used the name the tool declares, and its answer says which name that
 * is, so the next call uses it.
 * <p>
 * An alias is taken only when the call does not give the declared argument itself and the tool has no argument of that
 * name: {@code name} is never touched on {@code camel_catalog_doc}, where it is the real argument. When a call gives
 * several aliases of one argument, the first in the table wins and the others are ignored. Used by the MCP server
 * (before the arguments are checked, so a required argument given by an alias is not missing) and by {@code camel ask}.
 */
public final class ToolArgumentAliases {

    /** The field of a JSON answer that carries the {@link Resolved#note() note}. */
    public static final String NOTE_KEY = "argumentNote";

    /** For each tool, alias to declared name, in the order the aliases are tried. */
    private static final Map<String, Map<String, String>> ALIASES = Map.of(
            "camel_catalog_find", pairs("query", "term", "name", "term"),
            "camel_catalog_example_file", pairs("name", "example"),
            // the registry tool behind camel_catalog_example_file, as camel ask names it
            "get_example_file", pairs("name", "example"),
            // a path says more than a file name, so it wins when a call gives both
            "camel_validate_source", pairs("path", "file", "filename", "file", "name", "file", "source", "content"));

    private ToolArgumentAliases() {
    }

    /** An argument a tool declares: its name and whether a call has to give it. */
    public record Argument(String name, boolean required) {
    }

    /**
     * A call with its aliases taken for the declared names.
     *
     * @param tool      the tool name
     * @param declared  the arguments of the tool, in the order it declares them
     * @param given     the names of the arguments the call gave, in its order
     * @param arguments the arguments of the call under the declared names; an ignored argument is left in place
     * @param renamed   for each declared name taken from an alias, that alias
     * @param ignored   the names given that the tool does not take, with the aliases that lost to another one
     */
    public record Resolved<V>(String tool, List<Argument> declared, List<String> given, Map<String, V> arguments,
            Map<String, String> renamed, List<String> ignored) {

        /** The required arguments the call still does not give: it cannot run. */
        public List<String> missing() {
            return declared.stream()
                    .filter(a -> a.required() && arguments.get(a.name()) == null)
                    .map(Argument::name)
                    .toList();
        }

        /**
         * The answer to a call that misses a required argument: the arguments of the tool and the ones the call gave,
         * such as {@code camel_catalog_find takes term (required), kind, limit, camelVersion; got query}.
         */
        public String missingMessage() {
            return takes() + "; got " + (given.isEmpty() ? "no arguments" : String.join(", ", given));
        }

        /**
         * What the answer of the call says about its argument names: the declared name of each alias, and the arguments
         * the tool ignored with the ones it takes. Null when the call used the declared names only.
         */
        public String note() {
            List<String> parts = new ArrayList<>();
            renamed.forEach((name, alias) -> parts.add(name + " is the argument name (got " + alias + ")"));
            if (!ignored.isEmpty()) {
                parts.add("ignored: " + String.join(", ", ignored));
                parts.add(takes());
            }
            return parts.isEmpty() ? null : String.join("; ", parts);
        }

        private String takes() {
            List<String> names = declared.stream()
                    .map(a -> a.required() ? a.name() + " (required)" : a.name())
                    .toList();
            return tool + " takes " + (names.isEmpty() ? "no arguments" : String.join(", ", names));
        }
    }

    /** The aliases of a tool, alias to declared name; empty when it has none. */
    public static Map<String, String> aliases(String tool) {
        return ALIASES.getOrDefault(tool, Map.of());
    }

    /** The arguments a registry tool declares, for {@link #resolve(String, List, Map)}. */
    public static List<Argument> arguments(ToolDescriptor tool) {
        return tool.params().stream().map(p -> new Argument(p.name(), p.required())).toList();
    }

    /**
     * Takes the aliases of a call for the names the tool declares. A null value counts as not given, the way the MCP
     * server reads it.
     *
     * @param tool      the tool name
     * @param declared  the arguments of the tool, in the order it declares them
     * @param arguments the arguments of the call
     */
    public static <V> Resolved<V> resolve(String tool, List<Argument> declared, Map<String, V> arguments) {
        List<String> names = declared.stream().map(Argument::name).toList();
        // alias to declared name, for the aliases taken
        Map<String, String> taken = new LinkedHashMap<>();
        Map<String, String> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, String> alias : aliases(tool).entrySet()) {
            String name = alias.getValue();
            if (names.contains(name) && !names.contains(alias.getKey()) && !renamed.containsKey(name)
                    && arguments.get(name) == null && arguments.get(alias.getKey()) != null) {
                taken.put(alias.getKey(), name);
                renamed.put(name, alias.getKey());
            }
        }
        Map<String, V> resolved = new LinkedHashMap<>();
        List<String> ignored = new ArrayList<>();
        for (Map.Entry<String, V> e : arguments.entrySet()) {
            String name = taken.getOrDefault(e.getKey(), e.getKey());
            if (!names.contains(name)) {
                ignored.add(name);
            }
            // a declared argument given as null is left out for the alias that replaces it
            if (resolved.get(name) == null) {
                resolved.put(name, e.getValue());
            }
        }
        return new Resolved<>(tool, declared, List.copyOf(arguments.keySet()), resolved, renamed, ignored);
    }

    /**
     * A JSON object answer with the note as its first field; any other answer, or no note, is returned as it is.
     */
    public static String withNote(String answer, String note) {
        if (note == null || answer == null || !answer.stripLeading().startsWith("{")) {
            return answer;
        }
        Object json;
        try {
            json = Jsoner.deserialize(answer);
        } catch (DeserializationException e) {
            return answer;
        }
        if (!(json instanceof JsonObject object) || object.containsKey(NOTE_KEY)) {
            return answer;
        }
        JsonObject noted = new JsonObject();
        noted.put(NOTE_KEY, note);
        noted.putAll(object);
        return noted.toJson();
    }

    private static Map<String, String> pairs(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
