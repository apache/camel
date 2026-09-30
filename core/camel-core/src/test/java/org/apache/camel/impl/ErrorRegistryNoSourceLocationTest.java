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
package org.apache.camel.impl;

import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogErrorEventMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With source location off there is no line to name, so a step keeps its shape rather than inventing one. Source
 * location is set on the route definitions when they are loaded, so this needs its own context (CAMEL-24972).
 */
public class ErrorRegistryNoSourceLocationTest extends ContextTestSupport {

    /** Such as ErrorRegistryNoSourceLocationTest:57 */
    private static final Pattern LOCATION = Pattern.compile("\\S+:\\d+");

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getErrorRegistry().setEnabled(true);
        context.setMessageHistory(true);
        context.setSourceLocationEnabled(false);
        return context;
    }

    @Test
    public void testStepsKeepTheirShapeWithoutASourceLocation() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        BacklogErrorEventMessage entry = context.getErrorRegistry().browse().iterator().next();
        String[] steps = entry.getMessageHistory();
        assertNotNull(steps, "Message history should be captured when enabled");
        assertTrue(steps.length > 0, "Message history should have at least one entry");
        for (String step : steps) {
            assertFalse(LOCATION.matcher(step).find(), "There is no line to name, so none is named: " + step);
            assertTrue(step.contains("bodyType="), step);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").routeId("foo")
                        .to("log:before")
                        .throwException(new IllegalArgumentException("Forced error"));
            }
        };
    }
}
