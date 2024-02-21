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
package org.apache.camel.issues;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.NamedNode;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.NodeIdFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RouteIdAnonymousAndFixedClashTest extends ContextTestSupport {

    @Test
    public void testClash() throws Exception {
        // should create the 2 routes
        assertEquals(2, context.getRoutes().size());

        assertNotNull(context.getRoute("route1"), "Should have route1 (fixed id");
        assertNotNull(context.getRoute("route2"), "Should have route2 (auto assigned id)");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(true);
        ctx.getCamelContextExtension().addContextPlugin(NodeIdFactory.class, new NodeIdFactory() {
            AtomicInteger counter = new AtomicInteger();

            @Override
            public String createId(NamedNode definition) {
                return definition.getShortName() + counter.incrementAndGet();
            }
        });
        return ctx;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:in1").id("route1") // Note the name
                        .to("mock:test1");

                from("direct:in2").to("mock:test2");
            }
        };
    }
}
