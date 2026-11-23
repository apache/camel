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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.run.catalog.KameletDataTypeModel;
import org.apache.camel.tooling.model.BodyTypeModel;
import org.apache.camel.tooling.model.TransformerModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The data type a Kamelet turns the body into before its route sees it (CAMEL-27475): the default {@code in} data type
 * of the Kamelet ({@code spec.dataTypes}), which Camel applies as the input type of the route of the Kamelet, and what
 * the data type transformer takes, as the catalog says.
 * <p>
 * A step that calls a Kamelet is reached with the body of the calling route, such as a {@code LinkedHashMap}, and the
 * Kamelet gets it as its data type, such as JSON: {@code postgresql-sink} takes {@code json}, and its transformer
 * ({@code application-json}) writes a Map, a List or a POJO as JSON. Kamelets come from event-driven and serverless
 * integration (Knative, CloudEvents), where the data between the steps is JSON, which is why a SQL sink takes JSON. A
 * Camel developer does not know this from the Kamelet's name, and the body types alone look as if a Map goes into a
 * sink that takes JSON, so the tools show the conversion at the step.
 *
 * @param kamelet     the name of the Kamelet
 * @param dataType    the name of the data type, as the Kamelet names it: json, text, binary
 * @param transformer the data type transformer, as the Kamelet names it (its format): application-json, text-plain
 * @param mediaType   the media type of the data type, or null
 * @param description what the Kamelet takes, as its data type describes it, or null
 * @param carriers    the kinds of Java objects the transformer takes (text, bytes, stream, map, pojo...), as the
 *                    catalog says; empty when the catalog does not know the transformer
 * @param takes       what the transformer takes in words, as the catalog says, or null
 */
public record KameletInput(
        String kamelet, String dataType, String transformer, String mediaType, String description,
        List<String> carriers, String takes) {

    public KameletInput {
        carriers = carriers != null ? List.copyOf(carriers) : List.of();
    }

    /**
     * The name of the Kamelet a uri calls, such as postgresql-sink of {@code kamelet:postgresql-sink?serverName=db} or
     * {@code kamelet://postgresql-sink}; null when the uri does not call a Kamelet, or names it dynamically.
     */
    public static String kameletName(String uri) {
        if (uri == null) {
            return null;
        }
        String name = uri.trim();
        if (!name.startsWith("kamelet:")) {
            return null;
        }
        name = name.substring("kamelet:".length());
        if (name.startsWith("//")) {
            name = name.substring(2);
        }
        int end = name.length();
        for (char c : new char[] { '?', '/' }) {
            int i = name.indexOf(c);
            if (i >= 0) {
                end = Math.min(end, i);
            }
        }
        name = name.substring(0, end).trim();
        // kamelet:source and kamelet:sink are the ends of the route of a Kamelet, and a placeholder is not known here
        if (name.isEmpty() || "source".equals(name) || "sink".equals(name) || name.contains("{{")
                || name.contains("${")) {
            return null;
        }
        return name;
    }

    /**
     * The data type the Kamelet a uri calls turns the body into, from the project's Kamelets and else the catalog.
     *
     * @param  uri       the uri of the step, such as kamelet:postgresql-sink
     * @param  directory the directory of the project, for its own Kamelets, or null
     * @param  catalog   the catalog, for what the transformer takes, or null
     * @return           the data type, or null when the uri does not call a Kamelet, the Kamelet is not known, or it
     *                   has no default in data type (it gets the body as it is)
     */
    public static KameletInput find(String uri, Path directory, CamelCatalog catalog) {
        String name = kameletName(uri);
        KameletDefinitions.Definition def = name != null ? KameletDefinitions.find(name, directory, null) : null;
        return def != null ? of(def, catalog) : null;
    }

    /**
     * The default in data type of a Kamelet, with what its transformer takes as the catalog says; null when it has
     * none.
     */
    public static KameletInput of(KameletDefinitions.Definition def, CamelCatalog catalog) {
        for (KameletDataTypeModel dt : def.dataTypes()) {
            if ("in".equals(dt.direction) && dt.defaultType && dt.format != null) {
                TransformerModel transformer = catalog != null
                        ? catalog.transformerModel(CatalogDocs.transformerName(dt.format)) : null;
                BodyTypeModel in = transformer != null ? transformer.getBodyType(BodyTypeModel.TRANSFORMER_IN) : null;
                return new KameletInput(
                        def.name(), dt.name, dt.format, dt.mediaType, dt.description,
                        in != null ? in.getCarriers() : List.of(), in != null ? in.getDescription() : null);
            }
        }
        return null;
    }

    /**
     * Whether the transformer takes the bodies a step has been reached with: false only when the catalog says it does
     * not take one of them, such as a List for text-plain; null when it is not known, such as for a class the catalog
     * has no name for.
     *
     * @param types the class names of the bodies, as the body-type console gives them ("null" for no body)
     * @param kinds the kinds of the bodies: null, payload, data or collection
     */
    public Boolean takes(List<String> types, List<String> kinds) {
        if (carriers.isEmpty() || types == null || types.isEmpty()) {
            return null;
        }
        if (carriers.contains("any")) {
            return true;
        }
        // the kind of each type is known when they are all of one kind
        String kind = kinds != null && kinds.size() == 1 ? kinds.get(0) : null;
        boolean known = true;
        for (String type : types) {
            String carrier = carrierOf(type, kind);
            if (carrier == null) {
                known = false;
            } else if (!carriers.contains(carrier)) {
                return false;
            }
        }
        return known ? Boolean.TRUE : null;
    }

    /**
     * The carrier of a body by its class name, as the catalog names them (text, bytes, stream, file, value, map, list,
     * iterator, pojo, null); null when it is not known, such as for a class of a library that could be a stream or not.
     */
    static String carrierOf(String type, String kind) {
        if ("null".equals(type)) {
            return "null";
        }
        String carrier = BodyTypeModel.carrierOf(type);
        if (!"pojo".equals(carrier) && !"unknown".equals(carrier)) {
            return carrier;
        }
        if ("payload".equals(kind)) {
            // the stream caches of Camel (ByteArrayInputStreamCache, ReaderCache...) and the streams of the JDK
            String simple = type.substring(type.lastIndexOf('.') + 1);
            if (simple.endsWith("InputStream") || simple.endsWith("Reader") || simple.endsWith("Cache")) {
                return "stream";
            }
            if (simple.endsWith("GenericFile")) {
                return "file";
            }
            return null;
        }
        if ("data".equals(kind)) {
            // a Map of a library (a JsonObject) or an object: the transformers that take data take both
            return type.endsWith("Map") || type.endsWith("JsonObject") ? "map" : "pojo";
        }
        if ("collection".equals(kind)) {
            return type.endsWith("Iterator") ? "iterator" : "list";
        }
        return null;
    }

    /**
     * Adds the data type of the Kamelet each step calls to the step, as {@code kameletInput}, in the output of the
     * route-structure console: for {@code to: kamelet:name} and the kamelet EIP, when the Kamelet has a default in data
     * type. With the body types the step has been reached with, {@code takesBody} says whether its transformer takes
     * them (false only when the catalog says it does not).
     *
     * @param  routeStructure  the output of the route-structure console
     * @param  directory       the directory of the project, for its own Kamelets, or null
     * @param  catalog         the catalog, for what the transformers take, or null
     * @param  kameletsVersion the version of the Kamelet catalog the integration uses (camel run --kamelets-version),
     *                         or null for the one of the Camel CLI
     * @return                 how many steps got a kameletInput
     */
    public static int addTo(JsonObject routeStructure, Path directory, CamelCatalog catalog, String kameletsVersion) {
        JsonArray routes = routeStructure != null ? routeStructure.getCollection("routes") : null;
        if (routes == null) {
            return 0;
        }
        Map<String, Optional<KameletInput>> known = new HashMap<>();
        int count = 0;
        for (Object r : routes) {
            List<JsonObject> lines = r instanceof JsonObject route ? route.getCollection("code") : null;
            if (lines == null) {
                continue;
            }
            for (int i = 2; i < lines.size(); i++) {
                JsonObject line = lines.get(i);
                String name = kameletName(stepUri(line));
                if (name == null) {
                    continue;
                }
                KameletInput in = known.computeIfAbsent(name, n -> {
                    KameletDefinitions.Definition def = KameletDefinitions.find(n, directory, kameletsVersion);
                    return Optional.ofNullable(def != null ? of(def, catalog) : null);
                }).orElse(null);
                if (in != null) {
                    List<String> types = line.getCollection("bodyTypes");
                    List<String> kinds = line.getCollection("bodyKinds");
                    line.put("kameletInput", in.toJson(in.takes(types, kinds)));
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * The uri a step of the route structure calls: its endpoint, or kamelet:name for the kamelet EIP, whose code is
     * kamelet[name].
     */
    static String stepUri(JsonObject line) {
        String uri = line.getString("uri");
        if (uri != null) {
            return Jsoner.unescape(uri);
        }
        String code = line.getString("code");
        if ("kamelet".equals(line.getString("type")) && code != null && code.startsWith("kamelet[")
                && code.endsWith("]")) {
            return "kamelet:" + Jsoner.unescape(code.substring("kamelet[".length(), code.length() - 1));
        }
        return null;
    }

    /**
     * The data type in words after the body types of a step: "→ json", and why it needs attention when the transformer
     * does not take the body: "→ text (text-plain does not take it)".
     */
    public static String arrowText(String dataType, Boolean takes, String transformer) {
        String answer = "→ " + dataType;
        if (Boolean.FALSE.equals(takes)) {
            answer += " (" + transformer + " does not take it)";
        }
        return answer;
    }

    /**
     * What the transformer takes, in the words a Camel developer knows: "text, bytes, a stream, a Map, a List, a POJO
     * or no body"; null when the catalog does not know the transformer.
     */
    public String carriersInWords() {
        if (carriers.isEmpty()) {
            return null;
        }
        List<String> words = carriers.stream().map(c -> switch (c) {
            case "stream" -> "a stream";
            case "file" -> "a file";
            case "value" -> "a value (number, boolean)";
            case "map" -> "a Map";
            case "list" -> "a List";
            case "iterator" -> "an Iterator";
            case "pojo" -> "a POJO";
            case "null" -> "no body";
            case "any" -> "any body";
            default -> c;
        }).toList();
        if (words.size() == 1) {
            return words.get(0);
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " or " + words.get(words.size() - 1);
    }

    /** As JSON, for the route-structure output of the CLI and the MCP tools: the step's kameletInput. */
    public JsonObject toJson(Boolean takes) {
        JsonObject jo = new JsonObject();
        jo.put("kamelet", kamelet);
        jo.put("dataType", dataType);
        jo.put("transformer", transformer);
        if (mediaType != null) {
            jo.put("mediaType", mediaType);
        }
        if (description != null) {
            jo.put("description", description);
        }
        if (!carriers.isEmpty()) {
            jo.put("carriers", new JsonArray(carriers));
        }
        if (takes != null) {
            jo.put("takesBody", takes);
        }
        return jo;
    }
}
