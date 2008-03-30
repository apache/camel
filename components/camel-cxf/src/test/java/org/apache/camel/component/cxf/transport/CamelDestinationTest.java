/**
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

import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.MessageObserver;
import org.easymock.classextension.EasyMock;

public class CamelDestinationTest extends CamelTestSupport {
    private Message destMessage;

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:Producer").to("direct:EndpointA");
            }
        };
    }

    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext();
    }

    public void testCamelDestinationConfiguration() throws Exception {
        QName testEndpointQName = new QName("http://activemq.apache.org/camel-test", "port");
        // set up the bus with configure file
        SpringBusFactory bf = new SpringBusFactory();
        BusFactory.setDefaultBus(null);
        Bus bus = bf.createBus("/org/apache/camel/component/cxf/transport/CamelDestination.xml");
        BusFactory.setDefaultBus(bus);

        endpointInfo.setAddress("camel://direct:EndpointA");
        endpointInfo.setName(testEndpointQName);
        CamelDestination destination = new CamelDestination(null, bus, null, endpointInfo);

        assertEquals("{http://activemq.apache.org/camel-test}port.camel-destination", destination.getBeanName());
        CamelContext context = destination.getCamelContext();

        assertNotNull("The camel context which get from camel destination is not null", context);
        assertEquals("Get the wrong camel context", context.getName(), "dest_context");
        assertEquals("The camel context should has two routers", context.getRoutes().size(), 2);
        assertEquals("The router 0 start endpoint is wrong", context.getRoutes().get(0).getEndpoint().getEndpointUri(), "direct:EndpointA");
        bus.shutdown(false);
    }

    public CamelDestination setupCamelDestination(EndpointInfo endpointInfo, boolean send) throws IOException {
        ConduitInitiator conduitInitiator = EasyMock.createMock(ConduitInitiator.class);
        CamelDestination camelDestination = new CamelDestination(context, bus, conduitInitiator, endpointInfo);
        if (send) {
            // setMessageObserver
            observer = new MessageObserver() {
                public void onMessage(Message m) {
                    Exchange exchange = new ExchangeImpl();
                    exchange.setInMessage(m);
                    m.setExchange(exchange);
                    destMessage = m;
                }
            };
            camelDestination.setMessageObserver(observer);
        }
        return camelDestination;
    }

    public void testGetTransportFactoryFromBus() throws Exception {
        Bus bus = BusFactory.getDefaultBus();
        assertNotNull(bus.getExtension(ConduitInitiatorManager.class)
            .getConduitInitiator(CamelTransportFactory.TRANSPORT_ID));
    }

    public void testOneWayDestination() throws Exception {
        destMessage = null;
        inMessage = null;
        EndpointInfo conduitEpInfo = new EndpointInfo();
        conduitEpInfo.setAddress("camel://direct:Producer");
        CamelConduit conduit = setupCamelConduit(conduitEpInfo, true, false);
        Message outMessage = new MessageImpl();
        CamelDestination destination = null;
        try {
            endpointInfo.setAddress("camel://direct:EndpointA");
            destination = setupCamelDestination(endpointInfo, true);
            // destination.activate();
        } catch (IOException e) {
            assertFalse("The CamelDestination activate should not through exception ", false);
            e.printStackTrace();
        }
        sendoutMessage(conduit, outMessage, true, "HelloWorld");

        // just verify the Destination inMessage
        assertTrue("The destiantion should have got the message ", destMessage != null);
        verifyReceivedMessage(destMessage, "HelloWorld");
        destination.shutdown();
    }



    private void verifyReceivedMessage(Message inMessage, String content) {
        ByteArrayInputStream bis = (ByteArrayInputStream)inMessage.getContent(InputStream.class);
        byte bytes[] = new byte[bis.available()];
        try {
            bis.read(bytes);
        } catch (IOException ex) {
            assertFalse("Read the Destination recieved Message error ", false);
            ex.printStackTrace();
        }
        String reponse = new String(bytes);
        assertEquals("The reponse date should be equals", content, reponse);
    }

    public void testRoundTripDestination() throws Exception {

        inMessage = null;
        EndpointInfo conduitEpInfo = new EndpointInfo();
        conduitEpInfo.setAddress("camel://direct:Producer");
        // set up the conduit send to be true
        CamelConduit conduit = setupCamelConduit(conduitEpInfo, true, false);
        final Message outMessage = new MessageImpl();

        endpointInfo.setAddress("camel://direct:EndpointA");
        final CamelDestination destination = setupCamelDestination(endpointInfo, true);

        // set up MessageObserver for handlering the conduit message
        MessageObserver observer = new MessageObserver() {
            public void onMessage(Message m) {
                Exchange exchange = new ExchangeImpl();
                exchange.setInMessage(m);
                m.setExchange(exchange);
                verifyReceivedMessage(m, "HelloWorld");
                //verifyHeaders(m, outMessage);
                // setup the message for
                Conduit backConduit;
                try {
                    backConduit = destination.getBackChannel(m, null, null);
                    // wait for the message to be got from the conduit
                    Message replyMessage = new MessageImpl();
                    sendoutMessage(backConduit, replyMessage, true, "HelloWorld Response");
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        };
        //this call will active the camelDestination
        destination.setMessageObserver(observer);
        // set is oneway false for get response from destination
        // need to use another thread to send the request message
        sendoutMessage(conduit, outMessage, false, "HelloWorld");
        // wait for the message to be got from the destination,
        // create the thread to handler the Destination incomming message

        verifyReceivedMessage(inMessage, "HelloWorld Response");

        destination.shutdown();
    }

}
