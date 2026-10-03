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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25283: the sub-pages of a component's documentation can be asked for with {@code docPage}, and a model is told
 * where the page on writing a custom Kamelet is: in the kamelet answer, in its samples, and when a Kamelet file it
 * wrote is invalid.
 */
class CatalogDocComponentPagesTest {

    private static JsonObject catalogDoc(Map<String, String> args) throws Exception {
        String json = String.valueOf(ToolRegistry.execute("camel_catalog_doc", new ToolContext(), new HashMap<>(args)));
        return (JsonObject) Jsoner.deserialize(json);
    }

    private static JsonObject page(JsonArray pages, String name) {
        for (Object o : pages) {
            JsonObject p = (JsonObject) o;
            if (name.equals(p.getString("page"))) {
                return p;
            }
        }
        return null;
    }

    @Test
    public void testTheKameletAnswerNamesTheCustomKameletPage() throws Exception {
        JsonObject answer = catalogDoc(Map.of("name", "kamelet"));
        JsonArray pages = (JsonArray) answer.get("docPages");
        assertNotNull(pages, "no docPages in: " + answer.toJson());
        JsonObject custom = page(pages, "custom");
        assertNotNull(custom, "custom is not a doc page: " + pages.toJson());
        assertEquals("Writing a custom Kamelet", custom.getString("title"));
        assertNotNull(answer.getString("docPagesHint"));
        // the pages of another artifact that share the prefix are not sub-pages of the component
        assertNull(page(pages, "main"));
    }

    @Test
    public void testTheCustomKameletPageIsReturnedAsText() throws Exception {
        JsonObject answer = catalogDoc(Map.of("name", "kamelet", "docPage", "custom"));
        String doc = answer.getString("doc");
        assertNotNull(doc, "no doc in: " + answer.toJson());
        for (String kind : List.of("source", "sink", "action")) {
            assertTrue(doc.contains("camel.apache.org/kamelet.type: " + kind), "no " + kind + " Kamelet in the page");
        }
        assertTrue(doc.contains("{{?"), "the page does not say how an optional parameter is written");
        // the page is the answer: no option list around it
        assertNull(answer.get("options"));
        assertNull(answer.get("documentation"));
    }

    @Test
    public void testAnUnknownPageListsThePagesThereAre() throws Exception {
        JsonObject answer = catalogDoc(Map.of("name", "kamelet", "docPage", "nosuchpage"));
        assertNotNull(answer.getString("error"));
        assertNotNull(page((JsonArray) answer.get("docPages"), "custom"));
    }

    @Test
    public void testTheSubPagesOfOtherComponentsAreFoundToo() throws Exception {
        JsonArray pages = (JsonArray) catalogDoc(Map.of("name", "aws2-s3")).get("docPages");
        assertNotNull(pages);
        assertNotNull(page(pages, "streaming"), pages.toJson());
        assertNotNull(page(pages, "consumer-examples"), pages.toJson());
        String doc = catalogDoc(Map.of("name", "aws2-s3", "docPage", "streaming")).getString("doc");
        assertTrue(doc.startsWith("= AWS S3 - Streaming Upload"), doc.substring(0, Math.min(80, doc.length())));
    }

    @Test
    public void testAComponentWithoutSubPagesHasNoDocPages() throws Exception {
        assertNull(catalogDoc(Map.of("name", "timer")).get("docPages"));
    }

    @Test
    public void testAnInvalidKameletFileSaysWhereTheGuideIs() {
        String kamelet = """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: my-action
                  labels:
                    camel.apache.org/kamelet.type: action
                spec:
                  definition:
                    title: My Action
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        - jq: "."
                """;
        JsonObject invalid = AuthoringTools.validate(new ToolContext(), "my-action.kamelet.yaml", kamelet);
        assertFalse(invalid.getBoolean("valid"));
        assertEquals(AuthoringTools.KAMELET_GUIDE, invalid.getString("guide"));

        String fixed
                = kamelet.replace("- jq: \".\"", "- setBody:\n                  jq:\n                    expression: \".\"");
        JsonObject valid = AuthoringTools.validate(new ToolContext(), "my-action.kamelet.yaml", fixed);
        assertTrue(valid.getBoolean("valid"), valid.toJson());
        assertNull(valid.get("guide"));

        // a route file is not a Kamelet
        JsonObject route = AuthoringTools.validate(new ToolContext(), "my.camel.yaml",
                "- from:\n    uri: timer:x\n    steps:\n      - jq: \".\"\n");
        assertFalse(route.getBoolean("valid"));
        assertNull(route.get("guide"));
    }

    @Test
    public void testTheKameletSamplesSayWhereTheGuideIs() {
        JsonObject answer = CatalogSamples.sample(new DefaultCamelCatalog(), "component", "kamelet", 2);
        assertEquals(AuthoringTools.KAMELET_GUIDE, answer.getString("guide"), answer.toJson());
        assertNull(CatalogSamples.sample(new DefaultCamelCatalog(), "component", "timer", 2).get("guide"));
    }
}
