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
package org.apache.camel.component.spring.ws;

import java.io.StringReader;
import java.net.URI;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.spring.ws.bean.CamelEndpointMapping;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.ws.test.server.MockWebServiceClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.serverOrReceiverFault;

@ContextConfiguration
@CamelSpringTest
public class ConsumerExceptionPropagationRouteTest extends CamelTestSupport {

    private final String xmlRequestForGoogleStockQuote
            = "<GetQuote xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></GetQuote>";

    @Autowired
    private CamelEndpointMapping endpointMapping;

    @Autowired
    private ApplicationContext applicationContext;

    private MockWebServiceClient mockClient;

    @BeforeEach
    public void createClient() {
        mockClient = MockWebServiceClient.createClient(applicationContext);
    }

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.setTracing(true);
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = new SimpleRegistry();
        registry.bind("endpointMapping", this.endpointMapping);
        return registry;
    }

    @Disabled("For now getEndpointUri does not return the initial uri. Info like the endpoint scheme is lost")
    @Test
    public void testValidUri() throws Exception {
        String deprecate = "spring-ws:rootqname:{http://www.webserviceX.NET/}GetQuote?endpointMapping=#endpointMapping";
        String sanitized = "spring-ws:rootqname:(http://www.webserviceX.NET/)GetQuote?endpointMapping=#endpointMapping";
        Endpoint endpoint = context.getComponent("spring-ws").createEndpoint(deprecate);
        assertEquals(sanitized, endpoint.getEndpointUri());
        assertNotNull(new URI(endpoint.getEndpointUri()));
    }

    @Test
    public void consumeWebserviceAndTestForSoapFault() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlRequestForGoogleStockQuote));
        mockClient.sendRequest(withPayload(source)).andExpect(serverOrReceiverFault());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("spring-ws:rootqname:{http://www.webserviceX.NET/}GetQuote?endpointMapping=#endpointMapping")
                        .throwException(new RuntimeException("this is a test exception!"));
            }
        };
    }
}
