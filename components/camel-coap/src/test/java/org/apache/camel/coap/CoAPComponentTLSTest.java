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
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.junit.Test;

public class CoAPComponentTLSTest extends CamelTestSupport {
    
    protected static final int PORT = AvailablePortFinder.getNextAvailable();

    @Produce(uri = "direct:start")
    protected ProducerTemplate sender;
    
    @Test
    public void testTLS() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sender.sendBodyAndHeader("Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        KeyStoreParameters keystoreParameters = new KeyStoreParameters();
        keystoreParameters.setResource("service.jks");
        keystoreParameters.setPassword("security");
        
        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("truststore.jks");
        truststoreParameters.setPassword("storepass");
        
        registry.bind("keyParams", keystoreParameters);
        registry.bind("trustParams", truststoreParameters);

        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("coaps://localhost:%d/TestResource?alias=service&password=security&"
                      + "keyStoreParameters=#keyParams", PORT)
                    .transform(body().prepend("Hello "));

                from("direct:start")
                    .toF("coaps://localhost:%d/TestResource?trustStoreParameters=#trustParams", PORT)
                    .to("mock:result");
            }
        };
    }
}
