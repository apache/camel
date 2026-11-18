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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the curated pattern recipes against the YAML DSL schema and the Camel Catalog metadata for the same Camel
 * release.
 */
public class PatternRecipesYamlValidationTest {

    private static final Map<String, Map<String, String>> EXPECTED_EXAMPLES = Map.ofEntries(
            Map.entry("idempotent-consumer",
                    Map.of("route/enrich-and-dedupe", "direct")),
            Map.entry("content-based-routing-with-validation",
                    Map.of("route/content-based-router", "related")),
            Map.entry("file-polling-with-idempotent-repository",
                    Map.of("connect/file-processing", "related")),
            Map.entry("throttling-rate-limiting",
                    Map.of("route/dynamic-routing", "direct")),
            Map.entry("error-handling-dead-letter-redelivery",
                    Map.of("fail-well/error-handling", "direct")),
            Map.entry("jms-asynchronous-request-reply",
                    Map.of("connect-service/jms-request-reply", "direct")),
            Map.entry("circuit-breaker-with-fallback",
                    Map.of("fail-well/circuit-breaker", "direct")),
            Map.entry("scatter-gather-with-timeout",
                    Map.of(
                            "route/filter-and-multicast", "related",
                            "route/aggregator", "related")),
            Map.entry("transactional-outbox",
                    Map.of(
                            "connect-service/sql", "building-block",
                            "connect-service/artemis", "building-block")));

    private static final Set<String> EXPECTED_RECIPE_IDS = Set.of(
            "idempotent-consumer",
            "content-based-routing-with-validation",
            "file-polling-with-idempotent-repository",
            "throttling-rate-limiting",
            "error-handling-dead-letter-redelivery",
            "jms-asynchronous-request-reply",
            "circuit-breaker-with-fallback",
            "scatter-gather-with-timeout",
            "transactional-outbox",
            "saga-coordination-with-compensation");

    private static final String EXAMPLE_REPOSITORY = "apache/camel-jbang-examples";

    private static final Set<String> EXPECTED_IDEMPOTENT_TRANSPORT_VARIANTS = Set.of(
            "kafka", "jms");

    @Test
    void everyRecipeYamlPassesCamelDslAndCatalogValidation() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog();
        YamlValidator validator = new YamlValidator(false, null, catalog);
        validator.init();

        JsonNode yamlMapperTree;
        List<JsonObject> recipes = loadRecipes();

        assertEquals(10, recipes.size());

        Set<String> ids = new HashSet<>();
        Set<String> componentNames = new LinkedHashSet<>();
        Set<String> eipNames = new LinkedHashSet<>();

        for (JsonObject recipe : recipes) {
            String id = recipe.getString("id");
            assertNotNull(id);
            assertTrue(ids.add(id), "Duplicate recipe id: " + id);

            String name = recipe.getString("name");
            assertNotNull(name, id + " must declare a name");
            assertFalse(name.isBlank(), id + " must not have a blank name");

            String problem = recipe.getString("problem");
            assertNotNull(problem, id + " must declare a problem statement");
            assertFalse(problem.isBlank(), id + " must not have a blank problem statement");

            Collection<?> recommendedOptions = recipe.getCollection("recommendedOptions");
            assertNotNull(recommendedOptions, id + " must declare recommendedOptions");
            assertFalse(recommendedOptions.isEmpty(), id + " must have recommendedOptions");

            Collection<?> pitfalls = recipe.getCollection("pitfalls");
            assertNotNull(pitfalls, id + " must declare pitfalls");
            assertFalse(pitfalls.isEmpty(), id + " must have pitfalls");

            Collection<?> components = recipe.getCollection("components");
            assertNotNull(components, id + " must declare components");
            assertFalse(components.isEmpty(), id + " must declare at least one component");

            for (Object value : components) {
                String componentName = referenceName(value);
                assertNotNull(componentName, id + " contains an empty component reference");

                String component = componentName.toLowerCase(Locale.ROOT);
                if (component.startsWith("camel-")) {
                    component = component.substring("camel-".length());
                }

                assertNotNull(
                        catalog.componentModel(component),
                        id + " references an unknown Camel component: " + componentName);
                componentNames.add(component);
            }

            Collection<?> eips = recipe.getCollection("eips");
            assertNotNull(eips, id + " must declare EIPs");
            assertFalse(eips.isEmpty(), id + " must declare at least one EIP");

            for (Object value : eips) {
                String eipName = referenceName(value);
                assertNotNull(eipName, id + " contains an empty EIP reference");
                assertFalse(eipName.isBlank(), id + " contains a blank EIP reference");

                assertNotNull(
                        catalog.eipModel(eipName),
                        id + " references an EIP/model not present in Camel Catalog: " + eipName);

                eipNames.add(eipName);
            }

            String yaml = recipe.getJsonObject("exampleRoute").getString("source");
            assertNotNull(yaml, id + " must contain YAML source");

            JsonNode tree = validateYamlSnippet(catalog, validator, id, yaml);

            validateStructuredRecommendations(catalog, recipe, tree);
            validateTransportVariants(catalog, validator, recipe);
            validateSagaOrchestrator(id, tree);
        }

        assertEquals(EXPECTED_RECIPE_IDS, ids);
        assertFalse(componentNames.isEmpty());
        assertFalse(eipNames.isEmpty());
    }

    @Test
    void exampleLinksAndCoverageAreAccuratelyClassified() throws Exception {
        List<JsonObject> recipes = loadRecipes();
        assertEquals(10, recipes.size());

        Set<String> seenIds = new HashSet<>();

        for (JsonObject recipe : recipes) {
            String id = recipe.getString("id");
            assertTrue(seenIds.add(id), "Duplicate recipe id: " + id);

            Collection<?> examples = recipe.getCollection("examples");
            assertNotNull(examples, id + " must have an examples array");

            if ("saga-coordination-with-compensation".equals(id)) {
                assertTrue(examples.isEmpty(), "Saga must not claim an unavailable exact example");

                String availability = recipe.getString("exampleAvailability");
                assertNotNull(availability, "Saga must explain the absence of an exact example");
                assertFalse(availability.isBlank());
                continue;
            }

            Map<String, String> expected = EXPECTED_EXAMPLES.get(id);
            assertNotNull(expected, "No expected example mapping for " + id);
            assertEquals(expected.size(), examples.size(), id + " example count differs");

            Map<String, String> actual = new LinkedHashMap<>();

            for (Object value : examples) {
                assertTrue(value instanceof JsonObject, id + " example entry must be an object");
                JsonObject example = (JsonObject) value;

                assertEquals(
                        EXAMPLE_REPOSITORY,
                        example.getString("repository"),
                        id + " example repository differs");

                String path = example.getString("path");
                assertNotNull(path, id + " example path is required");

                String coverage = normalizeCoverage(example.getString("coverage"));
                assertNotNull(coverage, id + " example coverage is required");

                String url = example.getString("url");
                assertEquals(
                        "https://github.com/" + EXAMPLE_REPOSITORY + "/tree/main/" + path,
                        url,
                        id + " example URL must match its repository path");

                String note = example.getString("note");
                assertNotNull(note, id + " example note is required");
                assertFalse(note.isBlank(), id + " example note must not be blank");

                assertTrue(actual.put(path, coverage) == null, id + " repeats example path " + path);
            }

            assertEquals(expected, actual, id + " example coverage differs");
        }

        assertEquals(EXPECTED_RECIPE_IDS, seenIds);
    }

    private static List<JsonObject> loadRecipes() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog();

        String json;
        try (InputStream stream = catalog.loadResource("patterns", "recipes.json")) {
            assertNotNull(stream, "patterns/recipes.json must be packaged in camel-catalog");
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        JsonObject root = JsonMapper.deserialize(json);
        assertNotNull(root);
        assertEquals("1", String.valueOf(root.get("schemaVersion")));

        Collection<?> values = root.getCollection("recipes");
        assertNotNull(values);

        List<JsonObject> answer = new ArrayList<>();
        for (Object value : values) {
            assertTrue(value instanceof JsonObject, "Each recipe must be an object");
            answer.add((JsonObject) value);
        }
        return answer;
    }

    private static String referenceName(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof JsonObject) {
            JsonObject object = (JsonObject) value;
            String name = object.getString("name");
            if (name == null) {
                name = object.getString("scheme");
            }
            if (name == null) {
                name = object.getString("id");
            }
            return name;
        }
        return null;
    }

    private static String normalizeCoverage(String value) {
        if (value == null) {
            return null;
        }
        String coverage = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if ("exact".equals(coverage) || "exact-example".equals(coverage)
                || "direct example".equals(coverage)) {
            return "direct";
        }
        if ("related example".equals(coverage)) {
            return "related";
        }
        if ("building block".equals(coverage) || "building-block-example".equals(coverage)) {
            return "building-block";
        }
        return coverage;
    }

    private static void collectEndpointUris(JsonNode node, Set<String> answer) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            var properties = node.properties().iterator();
            while (properties.hasNext()) {
                var property = properties.next();
                JsonNode child = property.getValue();

                if ("uri".equals(property.getKey()) && child.isString()) {
                    String uri = child.asString("").trim();
                    if (!uri.isEmpty()) {
                        answer.add(uri);
                    }
                }

                collectEndpointUris(child, answer);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectEndpointUris(child, answer);
            }
        }
    }

    private static JsonNode validateYamlSnippet(
            CamelCatalog catalog, YamlValidator validator, String id, String yaml)
            throws Exception {

        List<com.networknt.schema.Error> errors = validator.validate(yaml);
        List<String> messages = YamlValidator.describeAll(yaml, errors);

        assertTrue(
                errors.isEmpty(),
                id + " YAML is not valid against the Camel YAML DSL/catalog:\n"
                                  + String.join("\n", messages));

        JsonNode tree = YamlTrees.newMapper().readTree(yaml);
        assertNotNull(tree, id + " YAML must parse");

        Set<String> endpointUris = new LinkedHashSet<>();
        collectEndpointUris(tree, endpointUris);

        assertFalse(endpointUris.isEmpty(), id + " must contain at least one endpoint URI");

        for (String uri : endpointUris) {
            // Camel YAML supports a bare component name with parameters supplied
            // separately, for example uri: file plus parameters.directoryName.
            if (uri.startsWith("${") || uri.startsWith("{{") || uri.startsWith("#")) {
                continue;
            }

            int colon = uri.indexOf(':');
            String scheme = (colon > 0 ? uri.substring(0, colon) : uri)
                    .toLowerCase(Locale.ROOT);

            assertNotNull(
                    catalog.componentModel(scheme),
                    id + " uses an endpoint scheme not present in Camel Catalog: " + scheme);

            // A bare component name has no URI path/options string to validate here.
            // Its YAML parameters have already been checked by YamlValidator.
            // Dynamic URI expressions cannot be completely validated statically.
            if (colon <= 0 || uri.contains("${") || uri.contains("{{")) {
                continue;
            }

            var endpointValidation = catalog.validateEndpointProperties(uri);
            assertTrue(
                    endpointValidation.isSuccess(),
                    id + " contains an unknown endpoint option or invalid URI: "
                                                    + uri + "\n"
                                                    + endpointValidation.summaryErrorMessage(false));
        }

        return tree;
    }

    private static void validateTransportVariants(
            CamelCatalog catalog, YamlValidator validator, JsonObject recipe)
            throws Exception {

        String recipeId = recipe.getString("id");
        Collection<?> variants = recipe.getCollection("transportVariants");
        Set<String> variantIds = new HashSet<>();

        if (variants != null) {
            for (Object value : variants) {
                assertTrue(value instanceof JsonObject,
                        recipeId + " transport variant must be a JSON object");

                JsonObject variant = (JsonObject) value;
                String variantId = variant.getString("id");
                String component = variant.getString("component");

                assertNotNull(variantId, recipeId + " transport variant needs an id");
                assertFalse(variantId.isBlank(), recipeId + " transport variant id must not be blank");
                assertTrue(variantIds.add(variantId),
                        recipeId + " contains duplicate transport variant " + variantId);

                assertNotNull(component, variantId + " transport variant needs a component");
                assertEquals(component, variantId,
                        variantId + " variant id should identify its transport component");

                assertNotNull(
                        catalog.componentModel(component),
                        recipeId + " transport variant uses an unknown component: " + component);

                String description = variant.getString("description");
                assertNotNull(description, variantId + " variant needs a description");
                assertFalse(description.isBlank(), variantId + " variant description must not be blank");

                JsonObject route = variant.getJsonObject("exampleRoute");
                assertNotNull(route, variantId + " variant needs exampleRoute");
                assertEquals("yaml", route.getString("dsl"),
                        variantId + " variant must declare YAML DSL");

                String yaml = route.getString("source");
                assertNotNull(yaml, variantId + " variant needs YAML source");

                JsonNode tree = validateYamlSnippet(
                        catalog, validator, recipeId + "[" + variantId + "]", yaml);

                Set<String> endpointUris = new LinkedHashSet<>();
                collectEndpointUris(tree, endpointUris);

                boolean usesVariantComponent = false;
                for (String uri : endpointUris) {
                    int colon = uri.indexOf(':');
                    String scheme = (colon > 0 ? uri.substring(0, colon) : uri)
                            .toLowerCase(Locale.ROOT);
                    if (component.equalsIgnoreCase(scheme)) {
                        usesVariantComponent = true;
                        break;
                    }
                }

                assertTrue(
                        usesVariantComponent,
                        recipeId + " " + variantId
                                              + " variant does not use its declared component " + component);
            }
        }

        if ("idempotent-consumer".equals(recipeId)) {
            assertEquals(
                    EXPECTED_IDEMPOTENT_TRANSPORT_VARIANTS,
                    variantIds,
                    "Idempotent Consumer must cover Kafka and JMS transport variants");
        } else {
            assertTrue(
                    variantIds.isEmpty(),
                    recipeId + " has unexpected transport variants: " + variantIds);
        }
    }

    private static void validateStructuredRecommendations(
            CamelCatalog catalog, JsonObject recipe, JsonNode yamlTree) {

        String recipeId = recipe.getString("id");
        Collection<?> recommendations = recipe.getCollection("recommendedOptions");

        assertNotNull(recommendations, recipeId + " needs recommendedOptions");
        assertFalse(recommendations.isEmpty(), recipeId + " needs at least one recommendation");

        for (Object value : recommendations) {
            assertTrue(
                    value instanceof JsonObject,
                    recipeId + " recommendedOptions entries must be objects");

            JsonObject option = (JsonObject) value;
            String target = option.getString("target");
            String optionName = option.getString("name");
            String reason = option.getString("reason");

            assertNotNull(target, recipeId + " recommendation needs target");
            assertFalse(target.isBlank(), recipeId + " recommendation target must not be blank");

            assertNotNull(optionName, recipeId + " recommendation needs name");
            assertFalse(optionName.isBlank(), recipeId + " recommendation name must not be blank");

            assertNotNull(option.get("value"), recipeId + " recommendation needs value");

            assertNotNull(reason, recipeId + " recommendation needs reason");
            assertFalse(reason.isBlank(), recipeId + " recommendation reason must not be blank");

            var componentModel = catalog.componentModel(target);

            if (componentModel != null) {
                // ComponentModel separates endpoint URI parameters from
                // component configuration properties. Recipe recommendations
                // can legitimately refer to either kind.
                boolean knownComponentOption = componentModel.getEndpointOptions().stream()
                        .anyMatch(candidate -> optionName.equals(candidate.getName()))
                        || componentModel.getComponentOptions().stream()
                                .anyMatch(candidate -> optionName.equals(candidate.getName()));

                var recipeEipOptionModel = catalog.eipModel(target);
                boolean knownEipOption = recipeEipOptionModel != null
                        && recipeEipOptionModel.getOptions() != null
                        && recipeEipOptionModel.getOptions().stream()
                                .anyMatch(candidate -> optionName.equals(candidate.getName()));
                assertTrue(knownComponentOption || knownEipOption,
                        recipeId + " has unknown EIP, component, or endpoint option " + target + "." + optionName);
            } else {
                var eipModel = catalog.eipModel(target);

                assertNotNull(
                        eipModel,
                        recipeId + " recommendation target is neither a component nor a catalog model: "
                                  + target);

                assertTrue(
                        eipModel.getOptions().stream()
                                .anyMatch(candidate -> optionName.equals(candidate.getName())),
                        recipeId + " has unknown EIP/model option " + target + "." + optionName);
            }

            assertTrue(
                    hasTargetOption(yamlTree, target, optionName),
                    recipeId + " recommendation " + target + "." + optionName
                                                                   + " is not represented in its YAML route. "
                                                                   + "Keep recommendations and the example route synchronized.");
        }
    }

    private static boolean hasTargetOption(JsonNode node, String target, String optionName) {
        if (node == null) {
            return false;
        }

        if (node.isObject()) {
            JsonNode targetNode = node.get(target);
            if (targetNode != null && containsKeyRecursively(targetNode, optionName)) {
                return true;
            }

            // Component endpoint options are held under the endpoint's parameters map.
            JsonNode uriNode = node.get("uri");
            if (uriNode != null && uriNode.isString()) {
                String uri = uriNode.asString("").trim();
                int colon = uri.indexOf(':');
                String scheme = (colon > 0 ? uri.substring(0, colon) : uri);

                if (target.equalsIgnoreCase(scheme)) {
                    JsonNode parameters = node.get("parameters");
                    if (parameters != null && parameters.get(optionName) != null) {
                        return true;
                    }
                }
            }

            var properties = node.properties().iterator();
            while (properties.hasNext()) {
                if (hasTargetOption(properties.next().getValue(), target, optionName)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (hasTargetOption(child, target, optionName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean containsKeyRecursively(JsonNode node, String key) {
        if (node == null) {
            return false;
        }

        if (node.isObject()) {
            if (node.get(key) != null) {
                return true;
            }

            var properties = node.properties().iterator();
            while (properties.hasNext()) {
                if (containsKeyRecursively(properties.next().getValue(), key)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsKeyRecursively(child, key)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void validateSagaOrchestrator(String recipeId, JsonNode tree) {
        if (!"saga-coordination-with-compensation".equals(recipeId)) {
            return;
        }

        // Camel's documented YAML structure is a route-level Saga EIP step,
        // followed by the participating "to" steps as siblings in the route.
        assertTrue(tree.isArray(), "Saga example must be a YAML route list");

        JsonNode firstRouteEntry = tree.get(0);
        assertNotNull(firstRouteEntry);

        JsonNode route = firstRouteEntry.get("route");
        assertNotNull(route, "Saga example must start with an orchestrator route");

        JsonNode from = route.get("from");
        assertNotNull(from, "Saga orchestrator must have a from definition");

        JsonNode steps = from.get("steps");
        assertNotNull(steps, "Saga orchestrator must declare route steps");
        assertTrue(steps.isArray(), "Saga orchestrator steps must be an array");
        assertEquals(3, steps.size(),
                "Saga orchestrator must start a Saga and invoke two participants");

        JsonNode sagaStep = steps.get(0).get("saga");
        assertNotNull(sagaStep, "Saga EIP must be a route-level step");
        assertEquals("5m", sagaStep.get("timeout").asString(""),
                "Saga orchestrator must retain its explicit timeout");

        assertEquals(
                "direct:new-order",
                steps.get(1).get("to").get("uri").asString(""),
                "Order participant must follow the Saga EIP");

        assertEquals(
                "direct:reserve-credit",
                steps.get(2).get("to").get("uri").asString(""),
                "Credit participant must follow the Saga EIP");
    }

}
