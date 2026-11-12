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

import java.util.Map;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26411: the answer of camel_eval_expression says what the evaluation did not have, so a model does not take it
 * for what the route will have.
 */
class ExpressionEvaluatorNotesTest {

    /** r24: jsonpath with no body answered "json string can not be null or empty", 3 times. */
    @Test
    void aLanguageThatReadsItsDocumentFromTheBodyNamesIt() {
        JsonObject none = ExpressionEvaluator.evaluate(new ToolContext(), "xpath", "/order/id", null);
        assertThat(none.getString("status")).isEqualTo("error");
        assertThat(none.getString("note")).isEqualTo("no body was passed: xpath reads the XML of the message body, so"
                                                     + " this was evaluated on empty text; pass the XML as body");

        JsonObject body = ExpressionEvaluator.evaluate(new ToolContext(), "xpath", "/order/id", "<order><id>1</id></order>");
        assertThat(body.get("note")).isNull();
    }

    /**
     * r24 to r26: ${header.CamelRedeliveryCounter} evaluated to null, then the model read it as an exchange property,
     * or made 11 lookups of the Exchange API, and wrote nothing.
     */
    @Test
    void anUnsetHeaderSaysHowToPassItAndWhoSetsIt() {
        assertThat(ExpressionEvaluator.evaluate(new ToolContext(), "simple", "${header.CamelRedeliveryCounter}", null)
                .getString("note")).isEqualTo(
                        "evaluated to null: the header CamelRedeliveryCounter is not set: an evaluation has only the"
                                              + " headers passed in headers; the error handler sets it from the first"
                                              + " redelivery on, so it is not set on the first attempt, and onException"
                                              + " has it only after a redelivery");
        assertThat(ExpressionEvaluator.nullNote("simple", "${exchangeProperty.CamelRedeliveryCounter}", null))
                .isEqualTo("evaluated to null: the exchange property CamelRedeliveryCounter is not set: an evaluation has"
                           + " no exchange properties; CamelRedeliveryCounter is a header: ${header.CamelRedeliveryCounter}");
        // a header that was passed: what is null is something else
        assertThat(ExpressionEvaluator.nullNote("simple", "${header.order[missing]}", Map.of("order", Map.of())))
                .isEqualTo(ExpressionEvaluator.NULL_NOTE);
    }
}
