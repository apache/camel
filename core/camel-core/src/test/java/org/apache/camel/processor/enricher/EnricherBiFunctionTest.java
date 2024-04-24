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

import java.util.function.BiFunction;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnricherBiFunctionTest extends ContextTestSupport {

    private final MockEndpoint cool = new MockEndpoint("mock:cool", new MockComponent(context));

    private final BiFunction<Exchange, Exchange, Object> myAgg
            = (Exchange e1, Exchange e2) -> e1.getMessage().getBody(String.class) + "+" + e2.getMessage().getBody(String.class);

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("cool", cool);
        jndi.bind("agg", myAgg);
        return jndi;
    }

    @Test
    public void testEnrichRef() throws Exception {
        cool.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) {
                exchange.getMessage().setBody("Bye World");
            }
        });
        cool.expectedBodiesReceived("Hello World");

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Hello World+Bye World", out);

        cool.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                cool.setCamelContext(context);

                from("direct:start").enrich().simple("ref:cool").aggregationStrategy("agg");
            }
        };
    }
}
