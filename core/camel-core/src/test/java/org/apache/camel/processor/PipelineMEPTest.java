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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test for pipeline keeping the MEP (CAMEL-1233)
 */
public class PipelineMEPTest extends ContextTestSupport {

    @Test
    public void testInOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(3);

        Exchange exchange = context.getEndpoint("direct:a").createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody(1);

        Exchange out = template.send("direct:a", exchange);
        assertNotNull(out);
        assertEquals(ExchangePattern.InOnly, out.getPattern());

        assertMockEndpointsSatisfied();

        // should keep MEP as InOnly
        assertEquals(ExchangePattern.InOnly, mock.getExchanges().get(0).getPattern());
    }

    @Test
    public void testInOut() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(3);

        Exchange exchange = context.getEndpoint("direct:a").createExchange(ExchangePattern.InOut);
        exchange.getIn().setBody(1);

        Exchange out = template.send("direct:a", exchange);
        assertNotNull(out);
        assertEquals(ExchangePattern.InOut, out.getPattern());

        assertMockEndpointsSatisfied();

        // should keep MEP as InOut
        assertEquals(ExchangePattern.InOut, mock.getExchanges().get(0).getPattern());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        final Processor inProcessor = new Processor() {
            public void process(Exchange exchange) {
                Integer number = exchange.getIn().getBody(Integer.class);
                if (number == null) {
                    number = 0;
                }
                number = number + 1;
                exchange.getIn().setBody(number);
            }
        };

        final Processor outProcessor = new Processor() {
            public void process(Exchange exchange) {
                Integer number = exchange.getIn().getBody(Integer.class);
                if (number == null) {
                    number = 0;
                }
                number = number + 1;
                // this is a bit evil we let you set on OUT body even if the MEP
                // is InOnly
                // however the result after the routing is correct using APIs to
                // get the result
                // however the exchange will carry body IN and OUT when the
                // route completes, as
                // we operate on the original exchange in this processor
                // (= we are the first node in the route after the from
                // consumer)
                exchange.getMessage().setBody(number);
            }
        };

        return new RouteBuilder() {
            public void configure() {
                from("direct:a").process(outProcessor)
                    // this pipeline is not really needed by to have some more
                    // routing in there to test with
                    .pipeline("log:x", "log:y").process(inProcessor).to("mock:result");
            }
        };
    }

}
