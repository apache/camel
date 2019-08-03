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

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;

import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.transport.CamelDestination.ConsumerProcessor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.UnknownPersonFault;
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
import org.junit.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;

public class CamelDestinationTest extends CamelTransportTestSupport {
    private Message destMessage;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                onException(RuntimeCamelException.class).handled(true).to("mock:error");
                from("direct:Producer").to("direct:EndpointA");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext();
    }

    @Test
    public void testCamelDestinationConfiguration() throws Exception {
        QName testEndpointQName = new QName("http://camel.apache.org/camel-test", "port");
        // set up the bus with configure file
        SpringBusFactory bf = new SpringBusFactory();
        BusFactory.setDefaultBus(null);
        Bus bus = bf.createBus("/org/apache/camel/component/cxf/transport/CamelDestination.xml");
        BusFactory.setDefaultBus(bus);

        endpointInfo.setAddress("camel://direct:EndpointA");
        endpointInfo.setName(testEndpointQName);
        CamelDestination destination = new CamelDestination(null, bus, null, endpointInfo);

        assertEquals("{http://camel.apache.org/camel-test}port.camel-destination", destination.getBeanName());
        CamelContext context = destination.getCamelContext();

        assertNotNull("The camel context which get from camel destination is not null", context);
        assertEquals("Get the wrong camel context", context.getName(), "dest_context");
        assertEquals("The camel context should has two routers", context.getRoutes().size(), 2);
        bus.shutdown(false);
    }

    public CamelDestination setupCamelDestination(EndpointInfo endpointInfo, boolean send) throws IOException {
        ConduitInitiator conduitInitiator = mock(ConduitInitiator.class);
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

    @Test
    public void testGetTransportFactoryFromBus() throws Exception {
        Bus bus = BusFactory.getDefaultBus();
        assertNotNull(bus.getExtension(ConduitInitiatorManager.class)
            .getConduitInitiator(CamelTransportFactory.TRANSPORT_ID));
    }

    @Test
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



    private void verifyReceivedMessage(Message inMessage, String content) throws IOException {
        ByteArrayInputStream bis = (ByteArrayInputStream)inMessage.getContent(InputStream.class);
        byte bytes[] = new byte[bis.available()];
        bis.read(bytes);
        String reponse = new String(bytes);
        assertEquals("The reponse date should be equals", content, reponse);
    }
    
    @Test
    public void testRoundTripDestination() throws Exception {

        inMessage = null;
        EndpointInfo conduitEpInfo = new EndpointInfo();
        conduitEpInfo.setAddress("camel://direct:Producer");
        // set up the conduit send to be true
        CamelConduit conduit = setupCamelConduit(conduitEpInfo, true, false);
        final Message outMessage = new MessageImpl();

        endpointInfo.setAddress("camel://direct:EndpointA");
        final CamelDestination destination = setupCamelDestination(endpointInfo, true);

        // set up MessageObserver for handling the conduit message
        MessageObserver observer = new MessageObserver() {
            public void onMessage(Message m) {
                try {
                    Exchange exchange = new ExchangeImpl();
                    exchange.setInMessage(m);
                    m.setExchange(exchange);
                    verifyReceivedMessage(m, "HelloWorld");
                    //verifyHeaders(m, outMessage);
                    // setup the message for
                    Conduit backConduit;
                    backConduit = getBackChannel(destination, m);
                    // wait for the message to be got from the conduit
                    Message replyMessage = new MessageImpl();
                    sendoutMessage(backConduit, replyMessage, true, "HelloWorld Response");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        MockEndpoint error = context.getEndpoint("mock:error", MockEndpoint.class);
        error.expectedMessageCount(0);
        //this call will active the camelDestination
        destination.setMessageObserver(observer);
        // set is one way false for get response from destination
        // need to use another thread to send the request message
        sendoutMessage(conduit, outMessage, false, "HelloWorld");
        // wait for the message to be got from the destination,
        // create the thread to handler the Destination incoming message

        verifyReceivedMessage(inMessage, "HelloWorld Response");
        error.assertIsSatisfied();
        destination.shutdown();
    }
    
    @Test
    public void testRoundTripDestinationWithFault() throws Exception {

        inMessage = null;
        EndpointInfo conduitEpInfo = new EndpointInfo();
        conduitEpInfo.setAddress("camel://direct:Producer");
        // set up the conduit send to be true
        CamelConduit conduit = setupCamelConduit(conduitEpInfo, true, false);
        final Message outMessage = new MessageImpl();

        endpointInfo.setAddress("camel://direct:EndpointA");
        final CamelDestination destination = setupCamelDestination(endpointInfo, true);
        destination.setCheckException(true);

        // set up MessageObserver for handling the conduit message
        MessageObserver observer = new MessageObserver() {
            public void onMessage(Message m) {
                try {
                    Exchange exchange = new ExchangeImpl();
                    exchange.setInMessage(m);
                    m.setExchange(exchange);
                    verifyReceivedMessage(m, "HelloWorld");
                    //verifyHeaders(m, outMessage);
                    // setup the message for
                    Conduit backConduit;
                    backConduit = getBackChannel(destination, m);
                    // wait for the message to be got from the conduit
                    Message replyMessage = new MessageImpl();
                    replyMessage.setContent(Exception.class, new RuntimeCamelException());
                    sendoutMessage(backConduit, replyMessage, true, "HelloWorld Fault");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        
        MockEndpoint error = context.getEndpoint("mock:error", MockEndpoint.class);
        error.expectedMessageCount(1);
        
        //this call will active the camelDestination
        destination.setMessageObserver(observer);
        // set is one way false for get response from destination
        // need to use another thread to send the request message
        sendoutMessage(conduit, outMessage, false, "HelloWorld");
        // wait for the message to be got from the destination,
        // create the thread to handler the Destination incoming message

        verifyReceivedMessage(inMessage, "HelloWorld Fault");
        error.assertIsSatisfied();
        
        destination.shutdown();
    }
    
    private Conduit getBackChannel(CamelDestination destination, Message m) throws IOException {
        return destination.getInbuiltBackChannel(m);
    }
    
    @Test
    public void testExceptionForwardedToExchange() throws IOException {
        final RuntimeException expectedException = new RuntimeException("We simulate an exception in CXF processing");
        
        DefaultCamelContext camelContext = new DefaultCamelContext();
        CamelDestination dest = mock(CamelDestination.class);
        doThrow(expectedException).when(dest).incoming(isA(org.apache.camel.Exchange.class));
        ConsumerProcessor consumerProcessor = dest.new ConsumerProcessor();
        
        // Send our dummy exchange and check that the exception that occurred on incoming is set
        DefaultExchange exchange = new DefaultExchange(camelContext);
        consumerProcessor.process(exchange);
        Exception exc = exchange.getException();
        assertNotNull(exc);
        assertEquals(expectedException, exc);
    }
    
    @Test
    public void testCAMEL4073() throws Exception {
        try {
            Endpoint.publish("camel://foo", new Person() {
                public void getPerson(Holder<String> personId, Holder<String> ssn, Holder<String> name)
                    throws UnknownPersonFault {
                }
            });
            fail("Should throw and Exception");
        } catch (WebServiceException ex) {
            Throwable c = ex.getCause();
            assertNotNull(c);
            assertTrue(c instanceof NoSuchEndpointException);
        }
    }

}
