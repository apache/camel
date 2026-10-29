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

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionEvaluatorTest {

    @Test
    void predicatesAreRecognisedByTheirOperators() {
        assertTrue(ExpressionEvaluator.looksLikePredicate("${body} > 200 && ${body} < 300"));
        assertTrue(ExpressionEvaluator.looksLikePredicate("${header.type} in 'gold,silver'"));
        assertTrue(ExpressionEvaluator.looksLikePredicate("${header.foo} == 'bar' || ${header.bar} != null"));
        assertTrue(ExpressionEvaluator.looksLikePredicate("${header.title} contains 'Camel'"));
        assertFalse(ExpressionEvaluator.looksLikePredicate("${random(1,10)}"));
        assertFalse(ExpressionEvaluator.looksLikePredicate("${header.user} ?: 'Guest'"), "elvis gives a value");
        assertFalse(ExpressionEvaluator.looksLikePredicate("${header.a} == 'x' ? 'yes' : 'no'"),
                "ternary gives a value");
        assertFalse(ExpressionEvaluator.looksLikePredicate("Hello ${body}, price>100"),
                "operators need spaces around them");
        assertFalse(ExpressionEvaluator.looksLikePredicate(null));
    }

    @Test
    void evaluatesLocallyWithoutARunningIntegration() {
        ToolContext ctx = new ToolContext();
        JsonObject value = ExpressionEvaluator.evaluate(ctx, null, "Hello ${body}", "Camel");
        assertEquals("ok", value.getString("status"));
        assertEquals("Hello Camel", value.getString("result"));
        assertEquals("simple", value.getString("language"));
        assertNull(value.get("predicate"));

        JsonObject predicate = ExpressionEvaluator.evaluate(ctx, "simple", "${body} == 'Camel'", "Camel");
        assertTrue(predicate.getBoolean("predicate"));
        assertEquals("true", predicate.getString("result"));

        JsonObject note = ExpressionEvaluator.evaluate(ctx, "simple", "${body.length()}", null);
        assertTrue(note.getString("note").contains("empty body"));

        JsonObject error = ExpressionEvaluator.evaluate(ctx, "simple", "${header.foo ?: x}", null);
        assertEquals("error", error.getString("status"), "the fallback is not quoted");
        assertFalse(error.getString("error").isBlank());

        JsonObject unknown = ExpressionEvaluator.evaluate(ctx, "no-such-language", "x", null);
        assertEquals("error", unknown.getString("status"));
        assertTrue(unknown.getString("error").contains("no-such-language"));
    }

    @Test
    void theElvisOperatorInsideThePlaceholderEvaluates() {
        // the same as ${header.foo} ?: 'x' since Camel 4.24 (CAMEL-26401)
        JsonObject value = ExpressionEvaluator.evaluate(new ToolContext(), "simple", "${header.foo ?: 'x'}", null);
        assertEquals("ok", value.getString("status"));
        assertEquals("x", value.getString("result"));
        assertNull(value.get("nullResult"));
        assertNull(value.get("note"));
    }

    @Test
    void aNullValueIsSaidToBeNull() {
        // an answer without null fields (the MCP server) has no result then, which read as a form that works
        JsonObject value = ExpressionEvaluator.evaluate(new ToolContext(), "simple", "${header.missing}", null);
        assertEquals("ok", value.getString("status"));
        assertTrue(value.containsKey("result"));
        assertNull(value.get("result"));
        assertTrue(value.getBoolean("nullResult"));
        assertEquals(ExpressionEvaluator.NULL_NOTE, value.getString("note"));

        JsonObject empty = ExpressionEvaluator.evaluate(new ToolContext(), "simple", "${body}", null);
        assertNull(empty.get("nullResult"), "an empty body is a value");
    }

    @Test
    void anElvisWithoutItsSpacesIsTheNote() {
        JsonObject value = ExpressionEvaluator.evaluate(new ToolContext(), "simple", "${header.offset ?:0}", null);
        assertEquals("ok", value.getString("status"));
        assertTrue(value.getBoolean("nullResult"));
        assertTrue(value.getString("note").startsWith(
                "`${header.offset ?:0}` reads a header named 'offset ?:0': the elvis operator needs a space on both sides"
                                                      + " and goes after the function: `${header.offset} ?: 0` (from Camel 4.24 also"
                                                      + " `${header.offset ?: 0}`"),
                value.getString("note"));

        // a value that is not null is no help either: the fallback was never applied
        JsonObject text = ExpressionEvaluator.evaluate(new ToolContext(), "simple", "Limit ${header.limit?: 10}", null);
        assertEquals("Limit ", text.getString("result"));
        assertTrue(text.getString("note").contains("reads a header named 'limit?: 10'"), text.getString("note"));
        assertTrue(text.getString("note").contains("`${header.limit} ?: 10`"), text.getString("note"));
    }

    @Test
    void theElvisInsideThePlaceholderIsRewrittenForAnOlderCamel() {
        ToolContext ctx = new ToolContext();
        ctx.setCamelVersion("4.22.1");
        // the catalog of the CLI, so the test downloads nothing
        ctx.setCatalog(new DefaultCamelCatalog());
        JsonObject value = ExpressionEvaluator.evaluate(ctx, "simple", "${header.limit ?: 10}", null);
        assertEquals("ok", value.getString("status"), "this process reads it");
        assertEquals("10", value.getString("result"));
        assertEquals("`${header.limit ?: 10}` needs Camel 4.24 or later, where the elvis operator may be written inside"
                     + " ${...} (here Camel 4.22.1): write it after the function: `${header.limit} ?: 10`",
                value.getString("note"));
        // without a space it is a header name in any version, and the form inside the braces is not offered
        JsonObject offset = ExpressionEvaluator.evaluate(ctx, "simple", "${header.offset ?:0}", null);
        assertTrue(offset.getString("note").endsWith("goes after the function: `${header.offset} ?: 0`"),
                offset.getString("note"));
    }
}
