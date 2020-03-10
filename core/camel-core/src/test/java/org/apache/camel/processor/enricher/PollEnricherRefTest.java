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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

public class PollEnricherRefTest extends ContextTestSupport {

    private SedaEndpoint cool = new SedaEndpoint();

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("cool", cool);
        jndi.bind("agg", new UseLatestAggregationStrategy());
        return jndi;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        cool.setCamelContext(context);
        return context;
    }

    @Test
    public void testPollEnrichRef() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Bye World");
        cool.getQueue().add(exchange);

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);

        assertEquals(0, cool.getQueue().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                cool.setEndpointUriIfNotSpecified("cool");

                from("direct:start").pollEnrich().simple("ref:cool").timeout(2000).aggregationStrategyRef("agg");
            }
        };
    }
}
