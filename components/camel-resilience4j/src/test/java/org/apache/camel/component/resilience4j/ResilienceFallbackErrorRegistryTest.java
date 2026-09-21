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
package org.apache.camel.component.resilience4j;

import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.BacklogErrorEventMessage;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-24863: a failure the circuit breaker's fallback recovered from is an error that was handled, in the error
 * registry (camel get errors, the dev console): the entry keeps the node that failed and says handled.
 */
public class ResilienceFallbackErrorRegistryTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getErrorRegistry().setEnabled(true);
        return context;
    }

    @Test
    public void testFallbackRecordsTheFailureAsHandled() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Fallback response", "Fallback response");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        Collection<BacklogErrorEventMessage> entries = context.getErrorRegistry().browse();
        assertEquals(2, entries.size(),
                "one registry entry per call: the copy's ExchangeFailedEvent and the original's ExchangeFailureHandledEvent merge into the same slot");
        for (BacklogErrorEventMessage e : entries) {
            assertTrue(e.isHandled(), "the fallback handled the failure: " + e);
            assertEquals("java.lang.IllegalStateException", e.getExceptionType());
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .circuitBreaker()
                            .throwException(new IllegalStateException("Forced")).id("boom")
                        .onFallback()
                            .transform().constant("Fallback response")
                        .end()
                        .to("mock:result");
            }
        };
    }
}
