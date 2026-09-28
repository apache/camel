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
package org.apache.camel.component.properties;

import java.time.Duration;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PropertiesComponentCircularReferenceTest extends ContextTestSupport {

    // a circular reference through an optional placeholder used to make the parser loop for ever
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRequiredCircularReference() {
        context.getPropertiesComponent().addInitialProperty("loop1", "{{loop2}}");
        context.getPropertiesComponent().addInitialProperty("loop2", "{{loop1}}");
        context.start();

        assertCircularReference("{{loop1}}", "loop1");
    }

    @Test
    public void testOptionalSelfReference() {
        context.getPropertiesComponent().addInitialProperty("self", "{{?self}}");
        context.start();

        assertCircularReference("{{self}}", "?self");
        assertCircularReference("{{?self}}", "?self");
    }

    @Test
    public void testOptionalMutualReference() {
        context.getPropertiesComponent().addInitialProperty("a", "{{?b}}");
        context.getPropertiesComponent().addInitialProperty("b", "{{?a}}");
        context.start();

        assertCircularReference("x{{a}}y", "?b");
    }

    @Test
    public void testOptionalSelfReferenceWithDefaultValue() {
        context.getPropertiesComponent().addInitialProperty("timeout", "{{?timeout:5000}}");
        context.start();

        assertCircularReference("{{timeout}}", "?timeout:5000");
    }

    @Test
    public void testOptionalSelfReferenceInEndpointUri() throws Exception {
        // use its own context, so a start that does not return cannot block the stop in tearDown
        CamelContext camel = new DefaultCamelContext();
        camel.getPropertiesComponent().addInitialProperty("timeout", "{{?timeout:5000}}");
        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:t?period={{timeout}}&repeatCount=1").to("mock:result");
            }
        });

        Exception e = assertTimeoutPreemptively(TIMEOUT, () -> assertThrows(Exception.class, camel::start));
        camel.stop();
        assertCauseMessage(e, "Circular reference detected with key [?timeout:5000]");
    }

    @Test
    public void testOptionalWithoutCircularReference() {
        context.getPropertiesComponent().addInitialProperty("a", "{{b}}-{{?c}}");
        context.getPropertiesComponent().addInitialProperty("b", "B");
        context.getPropertiesComponent().addInitialProperty("timeout", "{{?override.timeout:5000}}");
        context.getPropertiesComponent().addInitialProperty("delay", "{{?override.delay}}");
        context.start();

        assertTimeoutPreemptively(TIMEOUT, () -> {
            assertEquals("B-", context.resolvePropertyPlaceholders("{{a}}"));
            // the same key twice is not a circular reference
            assertEquals("B-B-", context.resolvePropertyPlaceholders("{{a}}{{a}}"));
            assertEquals("xy", context.resolvePropertyPlaceholders("x{{?nope}}y"));
            assertNull(context.resolvePropertyPlaceholders("{{?nope}}"));
            assertEquals("5000", context.resolvePropertyPlaceholders("{{?nope:5000}}"));
            assertEquals("5000", context.resolvePropertyPlaceholders("{{timeout}}"));
            assertEquals("x-y", context.resolvePropertyPlaceholders("x-{{delay}}y"));
        });
    }

    @Test
    public void testOptionalWithoutCircularReferenceInEndpointUri() throws Exception {
        context.getPropertiesComponent().addInitialProperty("timeout", "{{?override.timeout:5000}}");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result?retainFirst={{?maxKeep}}&resultWaitTime={{timeout}}");
            }
        });
        context.start();

        assertNotNull(context.hasEndpoint("mock:result?resultWaitTime=5000"),
                "Endpoints: " + context.getEndpointRegistry().keySet());
    }

    private void assertCircularReference(String text, String key) {
        IllegalArgumentException e = assertTimeoutPreemptively(TIMEOUT,
                () -> assertThrows(IllegalArgumentException.class, () -> context.resolvePropertyPlaceholders(text)));
        assertTrue(e.getMessage().startsWith("Circular reference detected with key [" + key + "]"), e.getMessage());
    }

    private static void assertCauseMessage(Throwable e, String message) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(message)) {
                return;
            }
        }
        fail("Expected a cause with message containing: " + message + " but was: " + e);
    }
}
