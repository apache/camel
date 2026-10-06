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
package org.apache.camel.component.kamelet;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25387: a Kamelet whose template starts from the Kamelet itself created routes until the stack overflowed.
 */
public class KameletCycleTest {

    @Test
    public void aKameletWhoseTemplateStartsFromItself() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    // from kamelet:loop instead of kamelet:source, as a local model wrote it
                    routeTemplate("loop")
                            .from("kamelet:loop/route")
                            .setBody(simple("${body}!"));

                    from("direct:start").to("kamelet:loop");
                }
            });
            Exception e = assertThrows(Exception.class, context::start);
            String messages = messages(e);
            assertTrue(messages.contains("Kamelet loop uses itself"), messages);
            assertTrue(messages.contains("starts from kamelet:source"), messages);
        }
    }

    @Test
    public void aKameletThatUsesAnotherKameletIsNotACycle() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("outer").from("kamelet:source").to("kamelet:inner");
                    routeTemplate("inner").from("kamelet:source").setBody(simple("${body}!"));

                    from("direct:start").to("kamelet:outer");
                }
            });
            context.start();
            assertEquals("hi!", context.createProducerTemplate().requestBody("direct:start", "hi", String.class));
        }
    }

    @Test
    public void aKameletUsedTwiceIsNotACycle() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("upper").from("kamelet:source").setBody(simple("${body.toUpperCase()}"));

                    from("direct:start").to("kamelet:upper").to("kamelet:upper");
                }
            });
            context.start();
            assertEquals("HI", context.createProducerTemplate().requestBody("direct:start", "hi", String.class));
        }
    }

    private static String messages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
