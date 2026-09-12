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

        JsonObject error = ExpressionEvaluator.evaluate(ctx, "simple", "${header.foo ?: 'x'}", null);
        assertEquals("error", error.getString("status"), "the operator is inside the placeholder");
        assertFalse(error.getString("error").isBlank());

        JsonObject unknown = ExpressionEvaluator.evaluate(ctx, "no-such-language", "x", null);
        assertEquals("error", unknown.getString("status"));
        assertTrue(unknown.getString("error").contains("no-such-language"));
    }
}
