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

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InflightRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An inflight exchange says where the node it sits at is in the source, so whoever reads the inflight list - the dev
 * console, JMX, or the shutdown strategy naming what is holding the shutdown up - can go to the line (CAMEL-24975).
 */
public class InflightRepositoryNodeSourceTest extends ContextTestSupport {

    /** Such as InflightRepositoryNodeSourceTest:73 */
    private static final Pattern SOURCE = Pattern.compile("\\S+:\\d+");

    private final AtomicReference<String> nodeSource = new AtomicReference<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getInflightRepository().setInflightBrowseEnabled(true);
        context.setMessageHistory(true);
        context.setSourceLocationEnabled(true);
        return context;
    }

    @Test
    public void testInflightExchangeSaysWhereTheNodeIs() {
        template.sendBody("direct:start", "Hello World");

        String source = nodeSource.get();
        assertNotNull(source, "The inflight exchange should say where the node is");
        assertTrue(SOURCE.matcher(source).matches(), "Expected a source:line but was: " + source);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("foo")
                        .process(exchange -> {
                            Collection<InflightRepository.InflightExchange> list
                                    = context.getInflightRepository().browse();
                            assertEquals(1, list.size());

                            InflightRepository.InflightExchange inflight = list.iterator().next();
                            assertEquals("myProcessor", inflight.getNodeId());
                            nodeSource.set(inflight.getNodeSource());
                        }).id("myProcessor")
                        .to("mock:result");
            }
        };
    }
}
