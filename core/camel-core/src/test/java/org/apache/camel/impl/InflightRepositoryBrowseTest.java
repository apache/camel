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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InflightRepository;
import org.junit.Test;

public class InflightRepositoryBrowseTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getInflightRepository().setInflightBrowseEnabled(true);
        return context;
    }

    @Test
    public void testInflight() throws Exception {
        assertEquals(0, context.getInflightRepository().browse().size());

        template.sendBody("direct:start", "Hello World");

        assertEquals(0, context.getInflightRepository().browse().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo").to("mock:a").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        Collection<InflightRepository.InflightExchange> list = context.getInflightRepository().browse();
                        assertEquals(1, list.size());

                        InflightRepository.InflightExchange inflight = list.iterator().next();
                        assertNotNull(inflight);

                        assertEquals(exchange, inflight.getExchange());
                        assertEquals("foo", inflight.getFromRouteId());
                        assertEquals("foo", inflight.getAtRouteId());
                        assertEquals("myProcessor", inflight.getNodeId());
                    }
                }).id("myProcessor").to("mock:result");
            }
        };
    }

}
