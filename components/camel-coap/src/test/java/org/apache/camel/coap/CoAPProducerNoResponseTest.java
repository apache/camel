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
package org.apache.camel.coap;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.eclipse.californium.core.CoapClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer must fail the exchange when the CoAP server does not answer, instead of completing it with the request
 * body.
 */
public class CoAPProducerNoResponseTest extends CoAPTestSupport {

    // nothing listens on this port
    @RegisterExtension
    static AvailablePortFinder.Port unusedPort = AvailablePortFinder.find();

    @BindToRegistry("noAnswerClient")
    private final CoapClient noAnswerClient = new CoapClient(
            String.format("coap://localhost:%d/TestResource", unusedPort.getPort())).setTimeout(500L);

    @AfterEach
    void shutdownClient() {
        noAnswerClient.shutdown();
    }

    @Test
    void testNoResponseFailsTheExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = template.request("direct:start", e -> e.getIn().setBody("Hello"));

        CamelExchangeException cause = assertInstanceOf(CamelExchangeException.class, out.getException());
        assertTrue(cause.getMessage().startsWith("No response received from CoAP server"), cause.getMessage());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("coap://localhost:%d/TestResource?client=#noAnswerClient", unusedPort.getPort())
                        .to("mock:result");
            }
        };
    }
}
