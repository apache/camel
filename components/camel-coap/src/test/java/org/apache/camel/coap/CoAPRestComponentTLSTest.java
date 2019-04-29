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
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.californium.core.coap.CoAP;
import org.junit.Test;

public class CoAPRestComponentTLSTest extends CamelTestSupport {
    protected static final int PORT = AvailablePortFinder.getNextAvailable();

    @Produce("direct:start")
    protected ProducerTemplate sender;

    @Test
    public void testPOST() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sender.sendBodyAndHeader("Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testGET() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello user");
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sender.sendBody("");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        
        KeyStoreParameters keystoreParameters = new KeyStoreParameters();
        keystoreParameters.setResource("service.jks");
        keystoreParameters.setPassword("security");

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("truststore.jks");
        truststoreParameters.setPassword("storepass");

        context.getRegistry().bind("keystoreParameters", keystoreParameters);
        context.getRegistry().bind("truststoreParameters", truststoreParameters);

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("coap").scheme("coaps").host("localhost").port(PORT)
                    .endpointProperty("keyStoreParameters", "#keystoreParameters")
                    .endpointProperty("alias", "service")
                    .endpointProperty("password", "security");

                rest("/TestResource")
                    .get().to("direct:get1")
                    .post().to("direct:post1");

                from("direct:get1").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody("Hello user");
                    }
                });

                from("direct:post1").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody("Hello " + exchange.getIn().getBody(String.class));
                    }
                });

                from("direct:start")
                    .toF("coaps://localhost:%d/TestResource?trustStoreParameters=#truststoreParameters", PORT)
                    .to("mock:result");
            }
        };
    }
}
