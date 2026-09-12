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

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
