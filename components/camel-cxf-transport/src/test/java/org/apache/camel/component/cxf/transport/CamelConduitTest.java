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
package org.apache.camel.component.cxf.transport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.junit.Test;

public class CamelConduitTest extends CamelTransportTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:Producer").to("mock:EndpointA").process(new Processor() {

                    public void process(org.apache.camel.Exchange exchange) throws Exception {

                        if (exchange.getPattern().isOutCapable()) {
                            Object result = exchange.getIn().getBody();
                            exchange.getOut().setBody(result);
                        }
                    }
                });
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext();
    }

    @Test
    public void testCamelConduitConfiguration() throws Exception {
        QName testEndpointQNameA = new QName("http://camel.apache.org/camel-test", "portA");
        QName testEndpointQNameB = new QName("http://camel.apache.org/camel-test", "portB");
        QName testEndpointQNameC = new QName("http://camel.apache.org/camel-test", "portC");
        
        // set up the bus with configure file
        SpringBusFactory bf = new SpringBusFactory();
        BusFactory.setDefaultBus(null);
        Bus bus = bf.createBus("/org/apache/camel/component/cxf/transport/CamelConduit.xml");
        BusFactory.setDefaultBus(bus);

        // create the conduit and set the configuration with it
        endpointInfo.setAddress("camel://direct:EndpointA");
        endpointInfo.setName(testEndpointQNameA);
        CamelConduit conduit = new CamelConduit(null, bus, endpointInfo);
        CamelContext context = conduit.getCamelContext();

        assertNotNull("the camel context which get from camel conduit is not null", context);
        assertEquals("get the wrong camel context", context.getName(), "conduit_context");
        assertEquals("direct://EndpointA", context.getRoutes().get(0).getEndpoint().getEndpointUri());
        
        // test the configuration of camelContextId attribute 
        endpointInfo.setAddress("camel://direct:EndpointA");
        endpointInfo.setName(testEndpointQNameC);
        conduit = new CamelConduit(null, bus, endpointInfo);
        context = conduit.getCamelContext();

        assertNotNull("the camel context which get from camel conduit is not null", context);
        assertEquals("get the wrong camel context", context.getName(), "conduit_context");
        assertEquals("direct://EndpointA", context.getRoutes().get(0).getEndpoint().getEndpointUri());


        endpointInfo.setAddress("camel://direct:EndpointC");
        endpointInfo.setName(testEndpointQNameB);
        conduit = new CamelConduit(null, bus, endpointInfo);
        context = conduit.getCamelContext();
        assertNotNull("the camel context which get from camel conduit is not null", context);
        assertEquals("get the wrong camel context", context.getName(), "context");
        assertEquals("direct://EndpointC", context.getRoutes().get(0).getEndpoint().getEndpointUri());
        bus.shutdown(false);
    }

    @Test
    public void testPrepareSend() throws Exception {
        endpointInfo.setAddress("camel://direct:Producer");
        CamelConduit conduit = setupCamelConduit(endpointInfo, false, false);
        Message message = new MessageImpl();
        try {
            conduit.prepare(message);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        verifyMessageContent(message);
    }

    public void verifyMessageContent(Message message) {
        OutputStream os = message.getContent(OutputStream.class);
        assertTrue("OutputStream should not be null", os != null);
    }

    @Test
    public void testSendOut() throws Exception {
        endpointInfo.setAddress("camel://direct:Producer");
        CamelConduit conduit = setupCamelConduit(endpointInfo, true, false);
        MockEndpoint endpoint = getMockEndpoint("mock:EndpointA");
        endpoint.expectedMessageCount(1);
        Message message = new MessageImpl();
        // set the isOneWay to be true
        sendoutMessage(conduit, message, true, "HelloWorld");
        assertMockEndpointsSatisfied();
        // verify the endpoint get the response
    }

    @Test
    public void testSendOutRunTrip() throws Exception {
        endpointInfo.setAddress("camel://direct:Producer");
        CamelConduit conduit = setupCamelConduit(endpointInfo, true, false);
        MockEndpoint endpoint = getMockEndpoint("mock:EndpointA");
        endpoint.expectedMessageCount(1);
        Message message = new MessageImpl();
        // set the isOneWay to be false
        sendoutMessage(conduit, message, false, "HelloWorld");
        // verify the endpoint get the response
        assertMockEndpointsSatisfied();
        verifyReceivedMessage("HelloWorld");
    }

    public void verifyReceivedMessage(String content) {
        ByteArrayInputStream bis = (ByteArrayInputStream)inMessage.getContent(InputStream.class);
        byte bytes[] = new byte[bis.available()];
        try {
            bis.read(bytes);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        String reponse = new String(bytes);
        assertEquals("The reponse date should be equals", content, reponse);

    }
}
