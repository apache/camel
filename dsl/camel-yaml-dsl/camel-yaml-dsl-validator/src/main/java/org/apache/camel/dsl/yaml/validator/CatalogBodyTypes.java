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
package org.apache.camel.dsl.yaml.validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.BodyTypeModel;
import org.apache.camel.tooling.model.BodyTypeModel.OptionBodyTypeModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;

import static org.apache.camel.dsl.yaml.validator.RouteGraph.scheme;

/**
 * The body types of the catalog (CAMEL-24961), as the walk of {@link TextBodyFlow} reads them: the body a consumer
 * gives, the bodies a producer can send and the body it leaves, the body an unmarshal makes, and the bodies a consumer
 * that answers its caller can reply with.
 * <p/>
 * A side holds for a step only when the step decides every option the side depends on. A component can be configured as
 * well ({@code camel.component.kafka.valueDeserializer}), and the route file does not show that: an option of the
 * component counts only when the endpoint sets it, and otherwise the side is not known for the step. A value that is
 * only known when the route runs (a placeholder) makes it not known too.
 */
final class CatalogBodyTypes {

    /** The carriers of a payload: the text, the bytes, the stream or the file as read or written. */
    private static final Set<String> PAYLOAD = Set.of("text", "bytes", "stream", "file");

    /** The carriers of parsed data. */
    private static final Set<String> DATA = Set.of("map", "list");

    private final CamelCatalog catalog;
    private final CamelCatalog fallback;
    private final Map<String, Optional<ComponentModel>> components = new ConcurrentHashMap<>();
    private final Map<String, Optional<DataFormatModel>> dataFormats = new ConcurrentHashMap<>();
    private volatile Map<String, List<String>> byModelName;

    /**
     * @param catalog  the catalog of the validator
     * @param fallback the catalog to read the body types of a model from when the other has none, as the catalog of an
     *                 older Camel version does; null for none
     */
    CatalogBodyTypes(CamelCatalog catalog, CamelCatalog fallback) {
        this.catalog = catalog;
        this.fallback = fallback;
    }

    /** What a side says for a step: its carriers, and its Java type and format when it has them. */
    record Answer(List<String> carriers, String javaType, String format) {

        /** A payload (text, bytes, a stream or a file), maybe no body as well, as an HTTP response without one. */
        boolean payload() {
            return carriers.stream().anyMatch(PAYLOAD::contains) && onlyPayload();
        }

        /** Nothing but a payload or no body: no Map, no List, no object of a class. */
        boolean onlyPayload() {
            return !carriers.isEmpty() && carriers.stream().allMatch(c -> PAYLOAD.contains(c) || "null".equals(c));
        }

        /** A Map or a List: an iterator is not one, as it is read once and has no index. */
        boolean data() {
            return !carriers.isEmpty() && DATA.containsAll(carriers);
        }

        /** No body: the consumer gives none (timer). */
        boolean none() {
            return carriers.equals(List.of("null"));
        }

        /** The body is left as it was. */
        boolean unchanged() {
            return carriers.equals(List.of("unchanged"));
        }

        /**
         * The file as read, such as the GenericFile of the file consumer: the file is the only carrier. A blob that is
         * a stream or a downloaded file is a payload like any other.
         */
        boolean file() {
            return carriers.equals(List.of("file"));
        }

        /** The data format of the format, as an unmarshal or a marshal names it: json, jacksonXml, csv or yaml. */
        String dataFormat() {
            if (format == null) {
                return null;
            }
            return switch (format) {
                case "json", "csv", "yaml" -> format;
                case "xml" -> "jacksonXml";
                default -> null;
            };
        }

        /** The carriers in words, without no body: text, bytes, a stream or a file. */
        String inWords() {
            List<String> words = new ArrayList<>();
            for (String carrier : carriers) {
                switch (carrier) {
                    case "null" -> {
                        // a step that needs text takes no body too, and an HTTP response may have none
                    }
                    case "stream" -> words.add("a stream");
                    case "file" -> words.add("a file");
                    case "map" -> words.add("a Map");
                    case "list" -> words.add("a List");
                    case "iterator" -> words.add("an iterator");
                    case "value" -> words.add("a value");
                    case "pojo" -> words.add("an object");
                    default -> words.add(carrier);
                }
            }
            if (words.size() < 2) {
                return String.join("", words);
            }
            return String.join(", ", words.subList(0, words.size() - 1)) + " or " + words.get(words.size() - 1);
        }
    }

    /** The body a consumer gives the route it starts. */
    Answer consumerOut(String uri, JsonNode from) {
        return componentSide(uri, from, BodyTypeModel.CONSUMER_OUT);
    }

    /** The bodies a consumer that answers its caller can reply with: what the route can end with. */
    Answer consumerReply(String uri, JsonNode from) {
        return componentSide(uri, from, BodyTypeModel.CONSUMER_REPLY);
    }

    /** The bodies a producer can send or write. */
    Answer producerIn(String uri, JsonNode to) {
        return componentSide(uri, to, BodyTypeModel.PRODUCER_IN);
    }

    /** The body after a producer. */
    Answer producerOut(String uri, JsonNode to) {
        return componentSide(uri, to, BodyTypeModel.PRODUCER_OUT);
    }

    /** Whether the component of an endpoint is an HTTP component, by its label: platform-http. */
    boolean http(String uri) {
        ComponentModel model = component(scheme(uri));
        return model != null && model.getLabel() != null && Arrays.asList(model.getLabel().split(",")).contains("http");
    }

    /**
     * The body after an unmarshal with a data format of the YAML DSL (json, csv...) and its options: the data format of
     * the catalog is the one its library option names, such as jackson for json.
     */
    Answer unmarshalOut(String key, JsonNode options) {
        DataFormatModel model = dataFormat(key, options);
        return model != null
                ? answer(model.getBodyType(BodyTypeModel.UNMARSHAL_OUT), values(options), Set.of()) : null;
    }

    private Answer componentSide(String uri, JsonNode node, String side) {
        ComponentModel model = component(scheme(uri));
        if (model == null) {
            return null;
        }
        Set<String> configurable = new HashSet<>();
        for (BaseOptionModel option : model.getComponentOptions()) {
            configurable.add(option.getName());
        }
        Map<String, String> set = values(node != null && node.isObject() ? node.get("parameters") : null);
        String text = node != null && node.isTextual() ? node.asText()
                : node != null && node.isObject() && node.has("uri") ? node.get("uri").asText() : null;
        set.putAll(query(text));
        readPath(uri, model, set, configurable);
        return answer(model.getBodyType(side), set, configurable);
    }

    /**
     * Adds the options in the path of an endpoint uri, as the syntax of its component names them: the operation of
     * openai:embeddings. A path the catalog cannot read says nothing about them, as an option the component may set.
     */
    private void readPath(String uri, ComponentModel model, Map<String, String> set, Set<String> configurable) {
        if (uri == null || model.getEndpointPathOptions().isEmpty()) {
            return;
        }
        // the query is read on its own, and may hold what the parser of the catalog does not take, such as a space
        int q = uri.indexOf('?');
        String path = q < 0 ? uri : uri.substring(0, q);
        Optional<Map<String, String>> properties = endpointProperties(catalog, path);
        if (properties.isEmpty() && fallback != null) {
            properties = endpointProperties(fallback, path);
        }
        for (BaseOptionModel option : model.getEndpointPathOptions()) {
            String name = option.getName();
            if (properties.isEmpty()) {
                configurable.add(name);
            } else if (properties.get().containsKey(name)) {
                set.putIfAbsent(name, properties.get().get(name));
            }
        }
    }

    private static Optional<Map<String, String>> endpointProperties(CamelCatalog catalog, String uri) {
        try {
            return Optional.ofNullable(catalog.endpointProperties(uri));
        } catch (Exception e) {
            // not an endpoint the catalog can read, such as one of a component it does not have
            return Optional.empty();
        }
    }

    /**
     * What a side says for the options a step sets: the body for an option value it names, or its body when the options
     * it depends on keep their defaults; null when it is not known for the step.
     */
    private static Answer answer(BodyTypeModel side, Map<String, String> set, Set<String> configurable) {
        if (side == null) {
            return null;
        }
        Set<String> options = new LinkedHashSet<>(side.getDependsOn());
        side.getByOption().forEach(o -> options.add(o.getOption()));
        for (String option : options) {
            String value = set.get(option);
            if (value == null ? configurable.contains(option) : value.contains("{{") || value.contains("${")) {
                // the component may set it, or its value is only known when the route runs
                return null;
            }
        }
        Answer matched = null;
        for (OptionBodyTypeModel when : side.getByOption()) {
            String value = set.get(when.getOption());
            if (value != null && value.equalsIgnoreCase(when.getValue())) {
                if (matched != null && !matched.carriers().equals(when.getCarriers())) {
                    // two options the step sets say different bodies
                    return null;
                }
                matched = new Answer(when.getCarriers(), when.getJavaType(), side.getFormat());
            }
        }
        if (matched != null) {
            return matched;
        }
        if (side.isUnknown() || side.getDependsOn().stream().anyMatch(set::containsKey)) {
            return null;
        }
        return new Answer(side.getCarriers(), side.getJavaType(), side.getFormat());
    }

    /** The options of a node, by name in camel case: allowNullBody as well for allow-null-body. */
    private static Map<String, String> values(JsonNode node) {
        Map<String, String> answer = new HashMap<>();
        if (node != null && node.isObject()) {
            for (var it = node.fieldNames(); it.hasNext();) {
                String key = it.next();
                JsonNode value = node.get(key);
                answer.put(camelCase(key), value.isValueNode() ? value.asText() : value.toString());
            }
        }
        return answer;
    }

    /** The options in the query of an endpoint uri: file:inbox?noop=true. */
    private static Map<String, String> query(String uri) {
        Map<String, String> answer = new HashMap<>();
        int q = uri != null ? uri.indexOf('?') : -1;
        if (q < 0) {
            return answer;
        }
        for (String pair : uri.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                answer.put(camelCase(pair.substring(0, eq)), pair.substring(eq + 1));
            }
        }
        return answer;
    }

    private static String camelCase(String name) {
        if (name.indexOf('-') < 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.length());
        boolean upper = false;
        for (char c : name.toCharArray()) {
            if (c == '-') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    private ComponentModel component(String scheme) {
        if (scheme == null) {
            return null;
        }
        return components.computeIfAbsent(scheme, s -> {
            ComponentModel model = catalog.componentModel(s);
            if ((model == null || model.getBodyTypes().isEmpty()) && fallback != null) {
                ComponentModel other = fallback.componentModel(s);
                if (other != null && !other.getBodyTypes().isEmpty()) {
                    model = other;
                }
            }
            return Optional.ofNullable(model);
        }).orElse(null);
    }

    private DataFormatModel dataFormat(String key, JsonNode options) {
        List<String> names = dataFormatNames().getOrDefault(key, List.of());
        String name = names.size() == 1 ? names.get(0) : null;
        if (names.size() > 1) {
            // json is jackson, gson, fastjson or jsonb: the library option names it
            JsonNode library = options != null && options.isObject() ? options.get("library") : null;
            String wanted = library != null && library.isValueNode() ? library.asText() : defaultLibrary(key);
            name = names.stream().filter(n -> n.equalsIgnoreCase(wanted)).findFirst().orElse(null);
        }
        if (name == null) {
            return null;
        }
        return dataFormats.computeIfAbsent(name, n -> {
            DataFormatModel model = catalog.dataFormatModel(n);
            if ((model == null || model.getBodyTypes().isEmpty()) && fallback != null) {
                DataFormatModel other = fallback.dataFormatModel(n);
                if (other != null && !other.getBodyTypes().isEmpty()) {
                    model = other;
                }
            }
            return Optional.ofNullable(model);
        }).orElse(null);
    }

    /** The default of the library option of a data format of the YAML DSL: Jackson for json. */
    private String defaultLibrary(String key) {
        EipModel model = catalog.eipModel(key);
        if (model != null) {
            for (EipModel.EipOptionModel option : model.getOptions()) {
                if ("library".equals(option.getName()) && option.getDefaultValue() != null) {
                    return option.getDefaultValue().toString();
                }
            }
        }
        return null;
    }

    /** The data formats of the catalog by the name the YAML DSL gives them: json to fastjson, gson, jackson, jsonb. */
    private Map<String, List<String>> dataFormatNames() {
        Map<String, List<String>> answer = byModelName;
        if (answer == null) {
            answer = new HashMap<>();
            for (String name : catalog.findDataFormatNames()) {
                DataFormatModel model = catalog.dataFormatModel(name);
                if (model != null && model.getModelName() != null) {
                    answer.computeIfAbsent(model.getModelName(), k -> new ArrayList<>()).add(name);
                }
            }
            byModelName = answer;
        }
        return answer;
    }
}
