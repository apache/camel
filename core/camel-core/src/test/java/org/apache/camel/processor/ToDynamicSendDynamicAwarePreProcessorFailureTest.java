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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bar.BarComponent;
import org.apache.camel.component.bar.KaboomSendDynamicAware;
import org.apache.camel.support.component.EndpointUriFactorySupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * When the pre-processor of a SendDynamicAware fails, toD fails the exchange and does not send it.
 */
public class ToDynamicSendDynamicAwarePreProcessorFailureTest extends ContextTestSupport {

    private final AtomicInteger after = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("kaboomFactory", new KaboomEndpointUriFactory());
        return context;
    }

    @Test
    public void testPreProcessorFailureDoesNotSend() {
        KaboomSendDynamicAware.SENT.set(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:start", "Hello Camel", "drink", "beer"));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());

        assertEquals(0, KaboomSendDynamicAware.SENT.get(), "the exchange should not be sent");
        assertEquals(0, after.get(), "the route should not continue after the failure");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("kaboom", new BarComponent());

                from("direct:start").toD("kaboom:order?drink=${header.drink}").process(e -> after.incrementAndGet());
            }
        };
    }

    private static class KaboomEndpointUriFactory extends EndpointUriFactorySupport {

        @Override
        public boolean isEnabled(String scheme) {
            return "kaboom".equals(scheme);
        }

        @Override
        public String buildUri(String scheme, Map<String, Object> properties, boolean encode) {
            // not in use for this test
            return null;
        }

        @Override
        public Set<String> propertyNames() {
            return Set.of("name", "drink");
        }

        @Override
        public Set<String> secretPropertyNames() {
            return null;
        }

        @Override
        public Map<String, String> multiValuePrefixes() {
            return null;
        }

        @Override
        public boolean isLenientProperties() {
            return false;
        }
    }
}
