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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared camel_catalog_doc tool, as the TUI's AI panel and every Camel MCP server expose it: written for what a
 * small model gets wrong most (a path option as a query parameter, an operator inside a simple placeholder).
 */
class CatalogDocsTest {

    private static JsonObject catalogDoc(Map<String, Object> args) throws Exception {
        Map<String, String> stringArgs = new HashMap<>();
        args.forEach((k, v) -> stringArgs.put(k, String.valueOf(v)));
        String json = String.valueOf(ToolRegistry.execute("camel_catalog_doc", new ToolContext(), stringArgs));
        return (JsonObject) Jsoner.deserialize(json);
    }

    private static String json(Map<String, Object> args) throws Exception {
        return catalogDoc(args).toJson();
    }

    @Test
    void theToolIsSharedAndInTheCoreSubset() {
        ToolDescriptor td = ToolRegistry.findTool("camel_catalog_doc");
        assertTrue(td.isCore(), "local models get it too");
        assertTrue(td.isReadOnly());
        assertTrue(ToolRegistry.authoringTools().contains(td));
        JsonObject schema = td.inputSchema();
        assertEquals("object", schema.getString("type"));
        assertTrue(schema.getMap("properties").containsKey("endpoint"));
        assertNull(schema.get("required"), "name or endpoint, neither alone is required");
    }

    @Test
    void simpleLanguageListsItsFunctionsAndOperatorsCompactly() throws Exception {
        JsonObject result = catalogDoc(Map.of("name", "simple", "kind", "language"));

        assertEquals("language", result.getString("kind"));
        assertTrue(result.getInteger("functionCount") > 100, "the simple language has well over 100 functions");
        assertTrue(result.getInteger("operatorCount") > 20);
        JsonObject groups = result.getMap("functionGroups");
        assertTrue(groups.containsKey("number"));
        assertTrue(((Collection<?>) groups.get("number")).contains("random(min,max)"));
        assertTrue(result.getCollection("operatorSyntax").contains("LHS == RHS"));
        assertNull(result.get("functions"), "full entries only with a filter");
        assertTrue(result.getString("functionsHint").contains("optionsFilter"));
        assertTrue(result.getString("syntax").contains("BETWEEN placeholders"));
        assertEquals(List.of("functions", "operators", "ognl", "advanced"), result.getCollection("docPages"));
        assertNull(result.get("doc"));
        // a plain component or language without functions is unchanged
        assertNull(catalogDoc(Map.of("name", "constant", "kind", "language")).get("functionCount"));
    }

    @Test
    void componentOptionsSayWhetherTheyArePathOrQueryAndTheUriRulesComeAlong() throws Exception {
        JsonObject timer = catalogDoc(Map.of("name", "timer", "kind", "component"));

        assertEquals("timer:timerName", timer.getString("syntax"));
        String uriSyntax = timer.getString("uriSyntax");
        assertTrue(uriSyntax.startsWith("URI: timer:timerName?option=value"));
        assertTrue(uriSyntax.contains("(timerName) go in the path, never as ?name=value"));
        assertTrue(uriSyntax.contains("parameters: map"));
        assertTrue(uriSyntax.contains("camel.component.timer.<option>"));

        Map<String, JsonObject> byName = new HashMap<>();
        for (Object o : timer.getCollection("options")) {
            JsonObject opt = (JsonObject) o;
            byName.put(opt.getString("name") + "/" + opt.getString("scope"), opt);
        }
        assertEquals("path", byName.get("timerName/endpoint").getString("kind"));
        assertEquals("parameter", byName.get("period/endpoint").getString("kind"));
        assertNull(byName.get("bridgeErrorHandler/component").get("kind"), "component options are neither");

        // a component whose syntax has no path options says so
        JsonObject direct = catalogDoc(Map.of("name", "direct", "kind", "component", "includeOptions", false));
        assertTrue(direct.getString("uriSyntax").contains("(name) go in the path"));
    }

    @Test
    void endpointArgumentValidatesAUriAgainstTheComponent() throws Exception {
        JsonObject bad = catalogDoc(Map.of("endpoint", "timer:tick?period=5s&fixedRte=true&repeatCount=abc"));
        assertEquals("timer", bad.getString("name"));
        assertFalse(bad.getBoolean("valid"));
        Collection<?> problems = bad.getCollection("problems");
        assertTrue(problems.stream().anyMatch(p -> p.toString().contains("Unknown option 'fixedRte'")
                && p.toString().contains("fixedRate")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.toString().contains("Invalid integer value 'abc'")
                && p.toString().contains("repeatCount")), problems.toString());
        assertTrue(bad.getString("uriSyntax").contains("(timerName) go in the path"));
        // the options the URI uses come with their docs, the path option included
        Collection<?> used = bad.getCollection("usedOptions");
        assertTrue(used.stream().map(JsonObject.class::cast).anyMatch(o -> "timerName".equals(o.getString("name"))
                && "path".equals(o.getString("kind")) && "tick".equals(o.getString("value"))));
        assertTrue(used.stream().map(JsonObject.class::cast).anyMatch(o -> "period".equals(o.getString("name"))
                && "5s".equals(o.getString("value")) && o.getString("description") != null));

        JsonObject good = catalogDoc(Map.of("endpoint", "timer:tick?period=5s", "name", "ignored"));
        assertTrue(good.getBoolean("valid"));
        assertTrue(good.getCollection("problems").isEmpty());
        assertTrue(good.getString("message").contains("valid"));

        // a wrong scheme is answered with the closest components, a non-URI with an error
        JsonObject scheme = catalogDoc(Map.of("endpoint", "mqt:temperature"));
        assertTrue(scheme.getString("error").contains("no component named 'mqt'"));
        assertFalse(scheme.getCollection("suggestions").isEmpty());
        assertTrue(catalogDoc(Map.of("endpoint", "no-scheme-here")).getString("error").contains("Not an endpoint URI"));
        assertTrue(catalogDoc(Map.of()).getString("error").contains("required"));
    }

    @Test
    void docPageReturnsALanguageSubPageAsText() throws Exception {
        JsonObject operators = catalogDoc(Map.of("name", "simple", "kind", "language", "docPage", "operators"));
        assertTrue(operators.getString("doc").contains("There *must* be spaces around the operator"));
        assertNull(operators.get("operatorCount"), "the page is the answer; no function or operator lists around it");
        assertNull(operators.get("options"));
        assertTrue(operators.getString("syntax").contains("BETWEEN placeholders"), "the primer stays");

        JsonObject missing = catalogDoc(Map.of("name", "simple", "kind", "language", "docPage", "nope"));
        assertTrue(missing.getString("error").contains("nope"));
        assertTrue(missing.getCollection("docPages").contains("functions"));
        // languages without sub-pages advertise none
        assertNull(catalogDoc(Map.of("name", "constant", "kind", "language")).get("docPages"));
    }

    @Test
    void filterReturnsMatchingFunctionsWithParametersAndExamples() throws Exception {
        JsonObject result = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", "random"));

        assertTrue(result.getInteger("matchedFunctions") >= 1);
        JsonObject random = result.getCollection("functions").stream()
                .map(JsonObject.class::cast)
                .filter(fn -> "random(min,max)".equals(fn.getString("name")))
                .findFirst().orElseThrow();
        assertEquals("number", random.getString("group"));
        assertEquals(2, random.getCollection("params").size());
        assertFalse(random.getCollection("examples").isEmpty());
        assertEquals(0, result.getInteger("matchedOperators"));
        assertNull(result.get("functionGroups"));

        // a group name matches every function of the group, and operators are found by their kind
        JsonObject dates = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", "date"));
        assertTrue(dates.getInteger("matchedFunctions") >= 2);
        JsonObject binary = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", "binary"));
        assertTrue(binary.getInteger("matchedOperators") > 5);
        JsonObject eq = (JsonObject) binary.getCollection("operators").iterator().next();
        assertEquals("LHS == RHS", eq.getString("syntax"));
    }

    @Test
    void aGroupNameReturnsTheGroupOnlyAndAFunctionNameComesFirst() throws Exception {
        // CAMEL-24804: date is a group of two functions, and two more mention a date in their description
        JsonObject dates = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", "date"));
        assertEquals("date", dates.getString("functionGroup"));
        assertEquals(2, dates.getInteger("matchedFunctions"));
        dates.getCollection("functions").forEach(fn -> assertEquals("date", ((JsonObject) fn).getString("group")));

        // string is a group of a dozen or so, while most descriptions say "string" somewhere
        JsonObject strings = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", "string"));
        assertEquals("string", strings.getString("functionGroup"));
        assertTrue(strings.getInteger("matchedFunctions") < 25,
                "the group, not the catalog: " + strings.getInteger("matchedFunctions"));
        strings.getCollection("functions").forEach(fn -> assertEquals("string", ((JsonObject) fn).getString("group")));

        // a function name comes first, with or without its parameters, then the word matches
        for (String name : List.of("random", "RANDOM", "random(min,max)")) {
            JsonObject random = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", name));
            assertNull(random.get("functionGroup"));
            JsonObject first = (JsonObject) random.getCollection("functions").iterator().next();
            assertEquals("random(min,max)", first.getString("name"), name);
        }

        // a function name comes first even when a group has the same name
        JsonObject function = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", "function"));
        assertEquals("function(name,exp)",
                ((JsonObject) function.getCollection("functions").iterator().next()).getString("name"));

        // an operator kind is that kind only, and an operator itself comes first
        JsonObject logical = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", "logical"));
        assertEquals("logical", logical.getString("operatorKind"));
        assertTrue(logical.getInteger("matchedOperators") >= 2);
        logical.getCollection("operators").forEach(op -> assertEquals("logical", ((JsonObject) op).getString("kind")));
        JsonObject eq = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", "=="));
        assertNull(eq.get("operatorKind"));
        assertEquals("==", ((JsonObject) eq.getCollection("operators").iterator().next()).getString("name"));
    }

    @Test
    void anInterfaceNameFindsTheBuiltInBeans() {
        org.apache.camel.catalog.CamelCatalog catalog = new org.apache.camel.catalog.DefaultCamelCatalog();
        var result = CatalogDocs.find(catalog, "AggregationStrategy", "bean", 20);
        var matches = (org.apache.camel.util.json.JsonArray) result.get("matches");
        assertTrue(matches.size() >= 5, "found " + matches.size());
        var first = (org.apache.camel.util.json.JsonObject) matches.get(0);
        assertEquals("org.apache.camel.AggregationStrategy", first.getString("interfaceType"));
        assertTrue(matches.stream().anyMatch(
                m -> "StringAggregationStrategy".equals(((org.apache.camel.util.json.JsonObject) m).getString("name"))));

        var doc = CatalogDocs.catalogDoc(catalog, "StringAggregationStrategy", null, "bean", null, null, false, false, null);
        assertEquals("org.apache.camel.processor.aggregate.StringAggregationStrategy", doc.getString("javaType"));
        assertTrue(doc.getString("declare")
                .contains("type: \"#class:org.apache.camel.processor.aggregate.StringAggregationStrategy\""));
        assertTrue(doc.getString("use").contains("aggregationStrategy: stringAggregationStrategy"));

        assertTrue(CatalogDocs.beansOfInterface(catalog, "org.apache.camel.AggregationStrategy").stream()
                .anyMatch(b -> b.startsWith("GroupedBodyAggregationStrategy (")));
    }

    @Test
    void anEipAliasFindsTheEip() throws Exception {
        // the aliases of the EIP models (fan-out, dedup, rate-limit) are what a model asks with; the former
        // camel_catalog_eips tool matched them, now camel_catalog_find and camel_catalog_doc do
        org.apache.camel.catalog.CamelCatalog catalog = new org.apache.camel.catalog.DefaultCamelCatalog();
        var result = CatalogDocs.find(catalog, "fan-out", "eip", 5);
        var matches = (org.apache.camel.util.json.JsonArray) result.get("matches");
        var first = (org.apache.camel.util.json.JsonObject) matches.get(0);
        assertEquals("eip", first.getString("kind"));
        assertEquals("multicast", first.getString("name"));
        assertTrue(first.getCollection("aliases").contains("fan-out"));

        // without a kind the EIPs are searched with the rest
        result = CatalogDocs.find(catalog, "dedup", null, 5);
        matches = (org.apache.camel.util.json.JsonArray) result.get("matches");
        assertTrue(matches.stream().anyMatch(
                m -> "idempotentConsumer".equals(((org.apache.camel.util.json.JsonObject) m).getString("name"))));

        // the doc of an alias is the doc of the EIP, saying which term it matched
        JsonObject doc = catalogDoc(Map.of("name", "rate-limit", "kind", "eip"));
        assertEquals("throttle", doc.getString("name"));
        assertEquals("rate-limit", doc.getString("matchedTerm"));
        doc = catalogDoc(Map.of("name", "fan-out"));
        assertEquals("multicast", doc.getString("name"));
        assertEquals("fan-out", doc.getString("matchedTerm"));
        assertNull(catalogDoc(Map.of("name", "multicast")).get("matchedTerm"), "an exact name matched no term");

        JsonObject missing = catalogDoc(Map.of("name", "no-such-pattern", "kind", "eip"));
        assertTrue(missing.getString("error").contains("no-such-pattern"));
    }

    @Test
    void aComponentListsItsHeadersOnRequest() throws Exception {
        JsonObject doc = catalogDoc(Map.of("name", "kafka", "kind", "component"));
        assertNull(doc.get("headers"), "headers only on request, they are many");
        assertEquals("org.apache.camel", doc.getString("groupId"));
        assertEquals("camel-kafka", doc.getString("artifactId"));
        assertFalse(doc.getString("version").isBlank(), "the Maven coordinates are complete");

        doc = catalogDoc(Map.of("name", "kafka", "kind", "component", "includeHeaders", true, "includeOptions", false));
        assertNull(doc.get("options"));
        var headers = (org.apache.camel.util.json.JsonArray) doc.get("headers");
        var key = headers.stream().map(h -> (org.apache.camel.util.json.JsonObject) h)
                .filter(h -> "CamelKafkaKey".equals(h.getString("name"))).findFirst().orElseThrow();
        assertEquals("org.apache.camel.component.kafka.KafkaConstants#KEY", key.getString("constantName"));
        assertEquals("Object", key.getString("javaType"));
        assertEquals("common", key.getString("group"));
        assertTrue(key.getBoolean("required"));
        assertTrue(key.getString("description").contains("key"));
    }

    @Test
    void theCommonOptionsAreTheDefaultAndTheScopeWidensThem() throws Exception {
        // the answer for kafka stays readable: deprecated and advanced options only on request, said in the answer
        JsonObject common = catalogDoc(Map.of("name", "kafka", "kind", "component"));
        int matched = common.getInteger("matchedOptions");
        int omitted = common.getInteger("omittedOptions");
        assertTrue(omitted > 10, "kafka has many advanced options, omitted " + omitted);
        assertTrue(common.getString("optionsHint").contains("includeOptions=all"));
        assertTrue(common.getCollection("options").stream()
                .noneMatch(o -> "isolationLevel".equals(((JsonObject) o).getString("name"))), "advanced");

        JsonObject all = catalogDoc(Map.of("name", "kafka", "kind", "component", "includeOptions", "all"));
        assertEquals(matched + omitted, all.getInteger("matchedOptions"));
        assertNull(all.get("omittedOptions"));
        assertTrue(all.getCollection("options").stream()
                .anyMatch(o -> "isolationLevel".equals(((JsonObject) o).getString("name"))));

        JsonObject required = catalogDoc(Map.of("name", "kafka", "kind", "component", "includeOptions", "required"));
        assertTrue(required.getInteger("matchedOptions") < 5);
        assertTrue(required.getCollection("options").stream().allMatch(o -> ((JsonObject) o).getBoolean("required")));
        assertTrue(required.getString("optionsHint").contains("required options only"));

        // a filter names what it wants, so it searches the advanced options too
        JsonObject filtered = catalogDoc(Map.of("name", "kafka", "kind", "component", "optionsFilter", "isolationLevel"));
        assertTrue(filtered.getInteger("matchedOptions") >= 1);
        assertNull(filtered.get("omittedOptions"));

        // the other kinds scope the same way, true is common and a wrong value is an error
        assertNotNull(catalogDoc(Map.of("name", "split", "kind", "eip", "includeOptions", "true")).get("options"));
        assertNull(catalogDoc(Map.of("name", "split", "kind", "eip", "includeOptions", "false")).get("options"));
        assertTrue(catalogDoc(Map.of("name", "split", "includeOptions", "some")).getString("error")
                .contains("includeOptions"));
    }

    @Test
    void aMainOptionGroupAskedForAsAComponentListsItsKeys() throws Exception {
        // a model asked for resilience4j as a component when it wanted the camel.resilience4j.* keys
        Map<String, String> args = new HashMap<>();
        args.put("name", "resilience4j");
        args.put("kind", "component");
        String json = String.valueOf(ToolRegistry.execute("camel_catalog_doc", new ToolContext(), args));
        JsonObject o = (JsonObject) Jsoner.deserialize(json);
        assertEquals("main-options", o.getString("kind"));
        assertEquals("camel.resilience4j", o.getString("group"));
        assertTrue(o.getString("note").contains("not a component"), o.getString("note"));
        assertTrue(json.contains("camel.resilience4j.failureRateThreshold"), json);

        // no kind: the same answer
        args.remove("kind");
        json = String.valueOf(ToolRegistry.execute("camel_catalog_doc", new ToolContext(), args));
        assertEquals("camel.resilience4j", ((JsonObject) Jsoner.deserialize(json)).getString("group"));

        // a real component keeps its answer, an unknown name keeps the not-found answer
        args.put("name", "timer");
        json = String.valueOf(ToolRegistry.execute("camel_catalog_doc", new ToolContext(), args));
        assertEquals("component", ((JsonObject) Jsoner.deserialize(json)).getString("kind"));
        args.put("name", "nosuchthing");
        json = String.valueOf(ToolRegistry.execute("camel_catalog_doc", new ToolContext(), args));
        assertTrue(json.contains("not found"), json);
    }

    @Test
    void apiKindIsTheCompactReferenceOfACoreClass() throws Exception {
        JsonObject exchange = catalogDoc(Map.of("name", "Exchange", "kind", "api"));

        assertEquals("api", exchange.getString("kind"));
        assertEquals("org.apache.camel.Exchange", exchange.getString("javaType"));
        // the Camel 4 changes for a model trained on older Camel
        assertTrue(exchange.getString("description").contains("getOut() is deprecated"), exchange.getString("description"));
        List<JsonObject> methods = exchange.getCollection("methods").stream().map(JsonObject.class::cast).toList();
        // the important methods first
        assertEquals("getMessage", methods.get(0).getString("name"));
        assertTrue(methods.get(0).getCollection("signatures").contains("Message getMessage()"));
        assertTrue(methods.get(0).getCollection("examples").contains("exchange.getMessage().getBody(String.class)"));
        // the overloads of an annotated method come from the compiled class
        JsonObject getProperty = methods.stream().filter(m -> "getProperty".equals(m.getString("name"))).findFirst()
                .orElseThrow();
        assertTrue(getProperty.getCollection("signatures").contains("<T> T getProperty(String name, Class<T> type)"));
        assertTrue(methods.stream().noneMatch(m -> "getOut".equals(m.getString("name"))), "deprecated is left out");
        // the other cards, so a model can navigate
        Collection<?> apis = exchange.getCollection("apis");
        assertTrue(apis.contains("Message") && apis.contains("CamelContext") && apis.contains("groovy"), apis.toString());
        assertNull(exchange.get("implementations"), "Exchange has no built-in implementations");

        // a qualified name and a lower case name work too, and the kind is detected
        assertEquals("api", catalogDoc(Map.of("name", "org.apache.camel.Message")).getString("kind"));
        assertEquals("Message", catalogDoc(Map.of("name", "message", "kind", "api")).getString("name"));
        assertTrue(json(Map.of("name", "message", "kind", "api")).contains("<T> T getHeader(String name, Class<T> type)"));

        // an interface lists the catalog beans that implement it: the strategies exist, no need to write one
        JsonObject strategy = catalogDoc(Map.of("name", "AggregationStrategy"));
        assertEquals("api", strategy.getString("kind"), "the api card wins over a bean implementing the interface");
        assertTrue(strategy.getString("description").contains("oldExchange is null"));
        assertTrue(strategy.getCollection("implementations").stream()
                .anyMatch(i -> i.toString().startsWith("GroupedBodyAggregationStrategy (")), strategy.toJson());
        // the registry card carries the methods inherited from BeanRepository
        assertTrue(json(Map.of("name", "Registry", "kind", "api")).contains("lookupByNameAndType"));

        // a miss says which cards exist
        JsonObject miss = catalogDoc(Map.of("name", "Nope", "kind", "api"));
        assertTrue(miss.getString("error").contains("No API reference"));
        assertTrue(miss.getCollection("apis").contains("Exchange"));
        // and without the kind the usual not found
        assertTrue(json(Map.of("name", "Nope")).contains("not found"));
    }

    @Test
    void scriptVariablesAreACardOfTheirOwnAndComeWithTheLanguageDoc() throws Exception {
        JsonObject groovy = catalogDoc(Map.of("name", "groovy", "kind", "api"));
        assertEquals("api", groovy.getString("kind"));
        JsonObject vars = groovy.getMap("variables");
        assertTrue(vars.containsKey("camelContext") && vars.containsKey("message") && vars.containsKey("log"));
        assertTrue(vars.getString("message").contains("request"), "the older name is given as an alias");
        assertTrue(groovy.getString("note").contains("lookupByName('myBean')"), "a bean name is not a variable");
        assertTrue(groovy.getCollection("apis").contains("Exchange"));

        // javascript has context and message, not camelContext; joor and javascript are aliases
        JsonObject js = catalogDoc(Map.of("name", "javascript", "kind", "api"));
        assertEquals("js", js.getString("name"));
        assertTrue(js.getMap("variables").containsKey("context"));
        assertFalse(js.getMap("variables").containsKey("camelContext"));
        assertTrue(catalogDoc(Map.of("name", "joor", "kind", "api")).getMap("variables").containsKey("optionalBody"));
        // the template components share one card
        assertEquals("template", catalogDoc(Map.of("name", "velocity", "kind", "api")).getString("name"));

        // the language doc carries the same variables, so no kind is needed to find them
        JsonObject lang = catalogDoc(Map.of("name", "groovy", "kind", "language"));
        assertEquals("language", lang.getString("kind"));
        assertTrue(lang.getMap("scriptVariables").containsKey("camelContext"));
        assertTrue(lang.getString("scriptNote").contains("lookupByName('myBean')"));
        assertNull(catalogDoc(Map.of("name", "simple", "kind", "language")).get("scriptVariables"));
    }

    @Test
    void scriptVariableCardsDoNotDriftFromTheVariableMapCamelBinds() throws Exception {
        // groovy and the template components bind ExchangeHelper.populateVariableMap (groovy adds attachments and log)
        try (var context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("hi");
            Map<String, Object> bound = new HashMap<>();
            ExchangeHelper.populateVariableMap(exchange, bound, true);
            assertTrue(bound.size() > 10);
            for (String card : List.of("groovy", "template")) {
                JsonObject vars = CatalogDocs.scriptVariables(card).getMap("variables");
                String text = vars.toJson();
                for (String name : bound.keySet()) {
                    assertTrue(vars.containsKey(name) || text.contains(name + " is an alias"),
                            card + " card misses the variable " + name);
                }
            }
            JsonObject groovy = CatalogDocs.scriptVariables("groovy").getMap("variables");
            assertTrue(groovy.containsKey("message") && groovy.containsKey("attachments") && groovy.containsKey("log"),
                    "the groovy extras");
        }
    }
}
