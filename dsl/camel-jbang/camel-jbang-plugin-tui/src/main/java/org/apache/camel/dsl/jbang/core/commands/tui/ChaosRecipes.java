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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The known rules of camel-chaos (the recipes of the chaos model of the catalog, {@code CamelCatalog.chaosModelJSon()},
 * CAMEL-26689): what goes wrong in plain words, each a one-line rule with {@code ${name}} placeholders, and where to
 * pick the value of a placeholder from in the integration (the endpoints its routes send to, the endpoints it polls,
 * its routes, its steps, or listed values).
 */
final class ChaosRecipes {

    /** A placeholder of a recipe. */
    record Placeholder(String name, String type, String description, String defaultValue, String example, String pickFrom,
            List<String> schemes, List<String> values) {
    }

    /** A known rule. */
    record Recipe(String id, String group, String title, String description, String rule, List<Placeholder> placeholders) {

        /** The rule with the values of the placeholders; a placeholder without a value stays {@code ${name}}. */
        String fill(Map<String, String> values) {
            String answer = rule;
            for (Placeholder p : placeholders) {
                String value = values.get(p.name());
                if (value != null && !value.isBlank()) {
                    answer = answer.replace("${" + p.name() + "}", value.strip());
                }
            }
            return answer;
        }
    }

    private final List<Recipe> recipes;

    ChaosRecipes(List<Recipe> recipes) {
        this.recipes = List.copyOf(recipes);
    }

    List<Recipe> recipes() {
        return recipes;
    }

    /**
     * The recipes of the catalog of the Camel CLI, or null when its catalog has no chaos model (a Camel version before
     * it).
     */
    static ChaosRecipes load() {
        try {
            String json = new DefaultCamelCatalog().chaosModelJSon();
            return json != null ? parse(json) : null;
        } catch (RuntimeException | LinkageError e) {
            // an older catalog: the rule is typed as one line
            return null;
        }
    }

    /** The recipes of the chaos model ({@code {"chaos": {"recipes": [...]}}}). */
    static ChaosRecipes parse(String json) {
        JsonObject root;
        try {
            root = (JsonObject) Jsoner.deserialize(json);
        } catch (Exception e) {
            return null;
        }
        if (!(root.get("chaos") instanceof JsonObject chaos) || !(chaos.get("recipes") instanceof JsonArray array)) {
            return null;
        }
        List<Recipe> answer = new ArrayList<>();
        for (Object o : array) {
            if (!(o instanceof JsonObject r) || r.getString("rule") == null) {
                continue;
            }
            List<Placeholder> placeholders = new ArrayList<>();
            if (r.get("placeholders") instanceof JsonArray ps) {
                for (Object po : ps) {
                    if (po instanceof JsonObject p) {
                        JsonObject pick = p.get("pick") instanceof JsonObject pj ? pj : null;
                        placeholders.add(new Placeholder(
                                p.getString("name"), p.getString("type"), p.getString("description"),
                                p.getString("default"), p.getString("example"),
                                pick != null ? pick.getString("from") : null,
                                strings(pick != null ? pick.get("schemes") : null),
                                strings(pick != null ? pick.get("values") : null)));
                    }
                }
            }
            answer.add(new Recipe(
                    r.getString("id"), r.getStringOrDefault("group", ""), r.getString("title"),
                    r.getString("description"), r.getString("rule"), placeholders));
        }
        return answer.isEmpty() ? null : new ChaosRecipes(answer);
    }

    private static List<String> strings(Object value) {
        List<String> answer = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(v -> answer.add(String.valueOf(v)));
        }
        return answer;
    }

    /**
     * The values a placeholder can be picked from in the integration: the endpoints its routes send to (of the schemes
     * of the placeholder, when it names some), the endpoints it consumes with a scheduled poll, its routes, its steps
     * (the ones with an id of their own first), or the listed values. Empty when the value is typed.
     */
    static List<String> candidates(Placeholder p, IntegrationInfo info) {
        Set<String> answer = new LinkedHashSet<>();
        if (p.pickFrom() == null) {
            return List.of();
        }
        switch (p.pickFrom()) {
            case "values" -> answer.addAll(p.values());
            case "producers" -> {
                for (ProducerInfo producer : info.producers) {
                    addUri(answer, producer.uri, p.schemes());
                }
                for (EndpointInfo endpoint : info.endpoints) {
                    if ("out".equalsIgnoreCase(endpoint.direction)) {
                        addUri(answer, endpoint.uri, p.schemes());
                    }
                }
            }
            case "pollingConsumers" -> {
                for (ConsumerInfo consumer : info.consumers) {
                    if (consumer.scheduled) {
                        addUri(answer, consumer.uri, p.schemes());
                    }
                }
            }
            case "routes" -> info.routes.forEach(r -> {
                if (r.routeId != null) {
                    answer.add(r.routeId);
                }
            });
            case "steps" -> {
                List<String> named = new ArrayList<>();
                List<String> generated = new ArrayList<>();
                for (RouteInfo route : info.routes) {
                    for (ProcessorInfo step : route.processors) {
                        if (step.id != null) {
                            (isGeneratedId(step.id) ? generated : named).add(step.id);
                        }
                    }
                }
                answer.addAll(named);
                answer.addAll(generated);
            }
            default -> {
                // typed (such as a header name)
            }
        }
        return List.copyOf(answer);
    }

    // an id Camel made up, such as to1 or log3
    private static boolean isGeneratedId(String id) {
        int i = id.length();
        while (i > 0 && Character.isDigit(id.charAt(i - 1))) {
            i--;
        }
        return i < id.length() && i > 0 && id.substring(0, i).chars().allMatch(Character::isLowerCase);
    }

    /** Adds the URI without its query (the rule matches the endpoint with or without it), of one of the schemes. */
    private static void addUri(Set<String> answer, String uri, List<String> schemes) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        int q = uri.indexOf('?');
        String base = q > 0 ? uri.substring(0, q) : uri;
        int colon = base.indexOf(':');
        String scheme = colon > 0 ? base.substring(0, colon) : base;
        if (schemes.isEmpty() || schemes.contains(scheme)) {
            answer.add(base);
        }
    }
}
