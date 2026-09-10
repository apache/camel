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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiToolRegistryCatalogDocTest {

    private static JsonObject catalogDoc(Map<String, Object> args) throws Exception {
        String json = new TuiToolRegistry(null).execute("tui_catalog_doc", new JsonObject(args));
        return (JsonObject) Jsoner.deserialize(json);
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
