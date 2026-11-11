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
package org.apache.camel.test.main.junit6.sanitizer;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.sanitizer.Sanitizer;
import org.apache.camel.sanitizer.SanitizerViolation;
import org.apache.camel.sanitizer.Violation;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exchange sanitizer in a CamelTestSupport test: the annotation is the only change to the test.
 */
@CamelSanitizer
class SanitizerCamelTestSupportTest extends CamelTestSupport {

    private final UserCode.CompletesTwice completesTwice = new UserCode.CompletesTwice();

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:twice").process(completesTwice).id("twice").to("mock:out");
                from("direct:throws").process(new UserCode.Throws()).id("throws");
                from("direct:aggregate").aggregate(constant(true), new UserCode.AssertingStrategy()).completionSize(2)
                        .id("aggregate").to("mock:aggregated");
            }
        };
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.COMPLETED_TWICE, step = "twice")
    void aCallbackCalledTwice() throws Exception {
        template.sendBody("direct:twice", "x");
        assertTrue(completesTwice.done.await(10, TimeUnit.SECONDS));
        SanitizerViolation v = violation(context, Violation.COMPLETED_TWICE);
        assertEquals(UserCode.CompletesTwice.class.getName(), v.getProcessor());
        assertTrue(v.getText().contains("thread user-first") && v.getText().contains("thread user-second"), v.getText());
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "throws")
    void aProcessorThatThrows() {
        template.send("direct:throws", e -> e.getMessage().setBody("x"));
        SanitizerViolation v = violation(context, Violation.THREW_BEFORE_CALLBACK);
        assertEquals(UserCode.Throws.class.getName(), v.getProcessor());
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "aggregate")
    void anAggregationStrategyThatThrows() {
        template.send("direct:aggregate", e -> e.getMessage().setBody("a"));
        template.send("direct:aggregate", e -> e.getMessage().setBody("b"));
        SanitizerViolation v = violation(context, Violation.THREW_BEFORE_CALLBACK);
        assertTrue(v.getText().contains("java.lang.AssertionError: Forced by AssertingStrategy"), v.getText());
        assertTrue(v.getText().contains("at " + UserCode.AssertingStrategy.class.getName() + ".aggregate"), v.getText());
    }

    /**
     * The violation of this kind: it is reported before the callback that breaks the rule goes on, and the processor
     * throws on the thread of the send, so it is there when the test looks.
     */
    static SanitizerViolation violation(CamelContext context, Violation kind) {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertNotNull(sanitizer, "the sanitizer is installed for a test marked with @CamelSanitizer");
        assertEquals(1, sanitizer.getViolationCount(kind), sanitizer::report);
        return sanitizer.getViolations().stream().filter(v -> v.getKind() == kind).findFirst().orElseThrow();
    }
}
