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
package org.apache.camel.processor.enricher;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class EnricherRefTest extends ContextTestSupport {

    private MockEndpoint cool = new MockEndpoint("mock:cool", new MockComponent(context));

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("cool", cool);
        jndi.bind("agg", new UseLatestAggregationStrategy());
        return jndi;
    }

    @Test
    public void testEnrichRef() throws Exception {
        cool.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage().setBody("Bye World");
            }
        });
        cool.expectedBodiesReceived("Hello World");

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);

        cool.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                cool.setCamelContext(context);

                from("direct:start").enrich().simple("ref:cool").aggregationStrategyRef("agg");
            }
        };
    }
}
