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
package org.apache.camel.processor;

import java.util.function.Consumer;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.SwitchValueDefinition;
import org.apache.camel.model.language.HeaderExpression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchValidationTest {
    @Test
    void rejectsCaseIdCollidingWithFallback() {
        rejects(s -> s.id("dispatch").doCase("billing").id("dispatch-otherwise").to("mock:a").otherwise("mock:b"),
                "Duplicate id detected");
    }

    @Test
    void rejectsDuplicateScalarValues() {
        rejects(s -> s.doCase("Billing", "mock:a").doCase("billing", "mock:b"), "Duplicate switch case");
    }

    @Test
    void rejectsMissingUri() {
        rejects(s -> s.doCase("billing", null), "nonblank uri");
    }

    @Test
    void rejectsDynamicUri() {
        rejects(s -> s.doCase("billing", "mock:${header.target}"), "must be static");
    }

    @Test
    void rejectsMissingValue() {
        rejects(s -> s.doCase((String) null, "mock:a"), "require value");
    }

    @Test
    void rejectsMissingSelector() {
        rejects(s -> s.setSelector(null), "selector requires an expression");
    }

    @Test
    void rejectsEmptySelector() {
        rejects(s -> s.setSelector(new ExpressionSubElementDefinition()), "selector requires an expression");
    }

    @Test
    void rejectsDuplicateKeys() {
        rejects(s -> s.keys("department", "department"), "keys must be nonblank and unique");
    }

    @Test
    void rejectsMissingCompositeKey() {
        rejects(s -> s.keys("department", "urgent").doCase().value("department", "billing").to("mock:a"),
                "exactly the declared keys");
    }

    @Test
    void rejectsExtraCompositeKey() {
        rejects(s -> s.keys("department").doCase().value("department", "billing").value("urgent", true).to("mock:a"),
                "exactly the declared keys");
    }

    @Test
    void rejectsRepeatedCompositeKey() {
        rejects(s -> s.keys("department").doCase().value("department", "billing").value("department", "technical").to("mock:a"),
                "Duplicate or missing switch value name");
    }

    @Test
    void rejectsMixedForms() {
        rejects(s -> s.keys("department").doCase("billing", "mock:a"), "cannot declare scalar value");
    }

    @Test
    void rejectsDuplicateCompositeValues() {
        rejects(s -> s.keys("department", "score")
                .doCase().value("department", "billing").value("score", 2).to("mock:a")
                .doCase().value("score", 2.0).value("department", "BILLING").to("mock:b"), "Duplicate switch case");
    }

    @Test
    void rejectsNonFiniteNumbers() {
        rejects(s -> s.keys("score").doCase().value("score", Double.NaN).to("mock:a"), "NaN");
    }

    @Test
    void rejectsInvalidBoolean() {
        rejects(s -> {
            s.keys("urgent").doCase().value("urgent", true).to("mock:a");
            SwitchValueDefinition value = s.getCases().get(0).getValues().get(0);
            value.setValue("yes");
        }, "boolean must be true or false");
    }

    private void rejects(Consumer<SwitchDefinition> configure, String message) {
        Exception failure = assertThrows(Exception.class, () -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        SwitchDefinition s = new SwitchDefinition();
                        s.setSelector(new ExpressionSubElementDefinition(new HeaderExpression("decision")));
                        configure.accept(s);
                        from("direct:start").addOutput(s);
                    }
                });
                context.start();
            }
        });
        StringBuilder chain = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            chain.append(cause.getMessage()).append('\n');
        }
        assertTrue(chain.toString().contains(message), chain.toString());
    }
}
