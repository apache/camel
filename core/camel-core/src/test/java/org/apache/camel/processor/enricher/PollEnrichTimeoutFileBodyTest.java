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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A pollEnrich that times out sets no body, also in a route that consumes files: the message of a file consumer creates
 * its body from the file on demand, which brought the file of the exchange back as the body, or as the variable of
 * variableReceive. The messages of JMS, mail and HTTP consumers create their body on demand as well.
 */
class PollEnrichTimeoutFileBodyTest extends ContextTestSupport {

    @Test
    void theBodyIsNullWhenNothingWasPolled() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:body");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader(fileUri("orders-body"), "ORD-1001", Exchange.FILE_NAME, "order-1001.json");

        mock.assertIsSatisfied();
        assertNull(mock.getReceivedExchanges().get(0).getMessage().getBody(), "nothing was polled, so no body");
    }

    @Test
    void theVariableIsNullWhenNothingWasPolled() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:variable");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader(fileUri("orders-variable"), "ORD-1001", Exchange.FILE_NAME, "order-1001.json");

        mock.assertIsSatisfied();
        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertNull(exchange.getVariable("customer"), "nothing was polled, so no variable");
        // the order is kept as the body
        assertEquals("ORD-1001", exchange.getMessage().getBody(String.class));
    }

    @Test
    void theBodyOfAMessageThatCreatesItOnDemandIsNull() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:demand");
        mock.expectedMessageCount(1);

        template.send("direct:demand", e -> e.setIn(new OnDemandMessage(e)));

        mock.assertIsSatisfied();
        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertNull(exchange.getMessage().getBody(), "nothing was polled, so no body");
        assertEquals("ORD-1001", exchange.getMessage().getHeader("orderId"), "the headers are kept");
    }

    // creates its body on demand, as the message of a JMS or mail consumer does
    private static class OnDemandMessage extends DefaultMessage {

        OnDemandMessage(Exchange exchange) {
            super(exchange);
            setHeader("orderId", "ORD-1001");
        }

        @Override
        protected Object createBody() {
            return "the payload of the consumer";
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("orders-body?initialDelay=0&delay=10"))
                        .pollEnrich().simple(fileUri("customers?fileName=C-482.json")).timeout(100)
                        .to("mock:body");

                from(fileUri("orders-variable?initialDelay=0&delay=10"))
                        .pollEnrich().simple(fileUri("customers?fileName=C-482.json")).timeout(100)
                        .variableReceive("customer")
                        .to("mock:variable");

                from("direct:demand")
                        .pollEnrich().simple(fileUri("customers?fileName=C-482.json")).timeout(100)
                        .to("mock:demand");
            }
        };
    }
}
