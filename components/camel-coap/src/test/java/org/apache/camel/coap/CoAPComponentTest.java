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

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.junit.Test;

public class CoAPComponentTest extends CoAPTestSupport {

    protected static final int TCP_PORT = AvailablePortFinder.getNextAvailable();

    @Produce("direct:start")
    protected ProducerTemplate sender;

    @Produce("direct:starttcp")
    protected ProducerTemplate tcpSender;

    @Test
    public void testCoAPComponent() throws Exception {
        CoapClient client = createClient("/TestResource");
        CoapResponse response = client.post("Camel", MediaTypeRegistry.TEXT_PLAIN);
        assertEquals("Hello Camel", response.getResponseText());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sender.sendBody("Camel CoAP");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCoAPComponentTLS() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        tcpSender.sendBody("Camel CoAP");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("coap://localhost:%d/TestResource", PORT).convertBodyTo(String.class).transform(body().prepend("Hello "));

                fromF("coap+tcp://localhost:%d/TestResource", TCP_PORT).convertBodyTo(String.class).transform(body().prepend("Hello "));

                from("direct:start").toF("coap://localhost:%d/TestResource", PORT).to("mock:result");

                from("direct:starttcp").toF("coap+tcp://localhost:%d/TestResource", TCP_PORT).to("mock:result");
            }
        };
    }

}
