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
package org.apache.camel.component.cxf;

import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.xml.ws.Endpoint;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.hello_world_soap_http.Greeter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CxfMessageHeaderTimeoutTest extends CamelSpringTestSupport {
    protected static final String GREET_ME_OPERATION = "greetMe";
    protected static final String TEST_MESSAGE = "Hello World!";
    protected static final String SERVER_ADDRESS
            = "http://localhost:" + CXFTestSupport.getPort1() + "/CxfMessageHeaderTimeoutTest/SoapContext/SoapPort";

    @BeforeAll
    public static void startService() {
        Greeter implementor = new GreeterImplWithSleep();
        Endpoint.publish(SERVER_ADDRESS, implementor);
    }

    @Test
    public void testInvokingJaxWsServerWithCxfEndpoint() throws Exception {
        sendTimeOutMessage("cxf://bean:springEndpoint");
    }

    protected void sendTimeOutMessage(String endpointUri) throws Exception {
        Exchange reply = sendJaxWsMessage(endpointUri);
        Exception e = reply.getException();
        assertNotNull(e, "We should get the exception cause here");
        assertTrue(e instanceof HttpTimeoutException, "We should get an http time out exception here");
    }

    protected Exchange sendJaxWsMessage(String endpointUri) throws InterruptedException {
        Exchange exchange = template.send(endpointUri, new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<>();
                params.add(TEST_MESSAGE);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, GREET_ME_OPERATION);
                // setup the receive timeout dynamically
                Map<String, Object> requestContext = new HashMap<>();
                HTTPClientPolicy clientPolicy = new HTTPClientPolicy();
                clientPolicy.setReceiveTimeout(100);
                requestContext.put(HTTPClientPolicy.class.getName(), clientPolicy);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(Client.REQUEST_CONTEXT, requestContext);

            }
        });
        return exchange;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        // we can put the http conduit configuration here
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/cxfMessageHeaderTimeOutContext.xml");
    }

}
