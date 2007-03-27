/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.camel.component.cxf.transport;

import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.MessageObserver;
import org.easymock.classextension.EasyMock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CamelDestinationTest extends CamelTestSupport {
    private Message destMessage;

    private void waitForReceiveInMessage() {
        int waitTime = 0;
        while (inMessage == null && waitTime < 3000) {
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                // do nothing here
            }
            waitTime = waitTime + 1000;
        }
        assertTrue("Can't receive the Conduit Message in 3 seconds", inMessage != null);
    }

    private void waitForReceiveDestMessage() {
        int waitTime = 0;
        while (destMessage == null && waitTime < 3000) {
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                // do nothing here
            }
            waitTime = waitTime + 1000;
        }
        assertTrue("Can't receive the Destination message in 3 seconds", destMessage != null);
    }

    public CamelDestination setupCamelDestination(boolean send) throws IOException {
        ConduitInitiator conduitInitiator = EasyMock.createMock(ConduitInitiator.class);
        CamelDestination camelDestination = new CamelDestination(camelContext, bus, conduitInitiator, endpointInfo);
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

    public void testGetConfiguration() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        BusFactory.setDefaultBus(null);
        bus = bf.createBus("/wsdl/camel_test_config.xml");
        BusFactory.setDefaultBus(bus);
        setupServiceInfo("http://cxf.apache.org/camel_conf_test",
                "/wsdl/camel_test_no_addr.wsdl",
                "HelloWorldQueueBinMsgService",
                "HelloWorldQueueBinMsgPort");
        CamelDestination destination = setupCamelDestination(false);

        /*assertEquals("Can't get the right ServerConfig's MessageTimeToLive ",
        500L,
        destination.getServerConfig().getMessageTimeToLive());
assertEquals("Can't get the right Server's MessageSelector",
        "cxf_message_selector",
        destination.getRuntimePolicy().getMessageSelector());
assertEquals("Can't get the right SessionPoolConfig's LowWaterMark",
        10,
        destination.getSessionPool().getLowWaterMark());
assertEquals("Can't get the right AddressPolicy's ConnectionPassword",
        "testPassword",
        destination.getCamelAddress().getConnectionPassword());*/
        BusFactory.setDefaultBus(null);
    }

    public void testOneWayDestination() throws Exception {
        destMessage = null;
        inMessage = null;
        setupServiceInfo("http://cxf.apache.org/hello_world_camel",
                "/wsdl/camel_test.wsdl",
                "HWStaticReplyQBinMsgService",
                "HWStaticReplyQBinMsgPort");
        CamelConduit conduit = setupCamelConduit(true, false);
        Message outMessage = new MessageImpl();
        setupMessageHeader(outMessage);
        CamelDestination destination = null;
        try {
            destination = setupCamelDestination(true);
            //destination.activate();
        }
        catch (IOException e) {
            assertFalse("The CamelDestination activate should not through exception ", false);
            e.printStackTrace();
        }
        sendoutMessage(conduit, outMessage, true);
        // wait for the message to be get from the destination
        waitForReceiveDestMessage();
        // just verify the Destination inMessage
        assertTrue("The destiantion should have got the message ", destMessage != null);
        verifyReceivedMessage(destMessage);
        verifyHeaders(destMessage, outMessage);
        destination.shutdown();
    }

    private void setupMessageHeader(Message outMessage) {
/*
        CamelMessageHeadersType header = new CamelMessageHeadersType();
        header.setCamelCorrelationID("Destination test");
        header.setCamelDeliveryMode(3);
        header.setCamelPriority(1);
        header.setTimeToLive(1000);
        outMessage.put(CamelConstants.Camel_CLIENT_REQUEST_HEADERS, header);
*/
    }

    private void verifyReceivedMessage(Message inMessage) {
        ByteArrayInputStream bis =
                (ByteArrayInputStream) inMessage.getContent(InputStream.class);
        byte bytes[] = new byte[bis.available()];
        try {
            bis.read(bytes);
        }
        catch (IOException ex) {
            assertFalse("Read the Destination recieved Message error ", false);
            ex.printStackTrace();
        }
        String reponse = new String(bytes);
        assertEquals("The reponse date should be equals", reponse, "HelloWorld");
    }

    private void verifyRequestResponseHeaders(Message inMessage, Message outMessage) {
/*
        CamelMessageHeadersType outHeader =
            (CamelMessageHeadersType)outMessage.get(CamelConstants.Camel_CLIENT_REQUEST_HEADERS);
        
        CamelMessageHeadersType inHeader =
            (CamelMessageHeadersType)inMessage.get(CamelConstants.Camel_CLIENT_RESPONSE_HEADERS);

        verifyJmsHeaderEquality(outHeader, inHeader);
*/

    }

    private void verifyHeaders(Message inMessage, Message outMessage) {
/*
        CamelMessageHeadersType outHeader =
            (CamelMessageHeadersType)outMessage.get(CamelConstants.Camel_CLIENT_REQUEST_HEADERS);
        
        CamelMessageHeadersType inHeader =
            (CamelMessageHeadersType)inMessage.get(CamelConstants.Camel_SERVER_REQUEST_HEADERS);
        verifyJmsHeaderEquality(outHeader, inHeader);
*/

    }

    /*
        private void verifyJmsHeaderEquality(CamelMessageHeadersType outHeader, CamelMessageHeadersType inHeader) {
            assertEquals("The inMessage and outMessage Camel Header's CorrelationID should be equals",
                         outHeader.getCamelCorrelationID(), inHeader.getCamelCorrelationID());
            assertEquals("The inMessage and outMessage Camel Header's CamelPriority should be equals",
                         outHeader.getCamelPriority(), inHeader.getCamelPriority());
            assertEquals("The inMessage and outMessage Camel Header's CamelType should be equals",
                         outHeader.getCamelType(), inHeader.getCamelType());

        }

    */
    public void testRoundTripDestination() throws Exception {

        inMessage = null;
        setupServiceInfo("http://cxf.apache.org/hello_world_camel",
                "/wsdl/camel_test.wsdl",
                "HelloWorldService",
                "HelloWorldPort");
        //set up the conduit send to be true 
        CamelConduit conduit = setupCamelConduit(true, false);
        final Message outMessage = new MessageImpl();
        setupMessageHeader(outMessage);
        final CamelDestination destination = setupCamelDestination(true);

        //set up MessageObserver for handlering the conduit message
        MessageObserver observer = new MessageObserver() {
            public void onMessage(Message m) {
                Exchange exchange = new ExchangeImpl();
                exchange.setInMessage(m);
                m.setExchange(exchange);
                verifyReceivedMessage(m);
                verifyHeaders(m, outMessage);
                //setup the message for 
                Conduit backConduit;
                try {
                    backConduit = destination.getBackChannel(m, null, null);
                    //wait for the message to be got from the conduit
                    Message replyMessage = new MessageImpl();
                    sendoutMessage(backConduit, replyMessage, true);
                }
                catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        };
        destination.setMessageObserver(observer);
        //set is oneway false for get response from destination
        sendoutMessage(conduit, outMessage, false);
        //wait for the message to be got from the destination, 
        // create the thread to handler the Destination incomming message

        waitForReceiveInMessage();
        verifyReceivedMessage(inMessage);
        // wait for a while for the camel session recycling
        Thread.sleep(1000);
        destination.shutdown();
    }

    public void testPropertyExclusion() throws Exception {

        final String customPropertyName =
                "THIS_PROPERTY_WILL_NOT_BE_AUTO_COPIED";

        inMessage = null;
        setupServiceInfo("http://cxf.apache.org/hello_world_camel",
                "/wsdl/camel_test.wsdl",
                "HelloWorldService",
                "HelloWorldPort");
        //set up the conduit send to be true 
        CamelConduit conduit = setupCamelConduit(true, false);
        final Message outMessage = new MessageImpl();
        setupMessageHeader(outMessage);

/*
        CamelPropertyType excludeProp = new CamelPropertyType();
        excludeProp.setName(customPropertyName);
        excludeProp.setValue(customPropertyName);
        
        CamelMessageHeadersType headers = (CamelMessageHeadersType)
            outMessage.get(CamelConstants.Camel_CLIENT_REQUEST_HEADERS);
        headers.getProperty().add(excludeProp);
*/

        final CamelDestination destination = setupCamelDestination(true);

        //set up MessageObserver for handlering the conduit message
        MessageObserver observer = new MessageObserver() {
            public void onMessage(Message m) {
                Exchange exchange = new ExchangeImpl();
                exchange.setInMessage(m);
                m.setExchange(exchange);
                verifyReceivedMessage(m);
                verifyHeaders(m, outMessage);
                //setup the message for 
                Conduit backConduit;
                try {
                    backConduit = destination.getBackChannel(m, null, null);
                    //wait for the message to be got from the conduit
                    Message replyMessage = new MessageImpl();
                    sendoutMessage(backConduit, replyMessage, true);
                }
                catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        };
        destination.setMessageObserver(observer);
        //set is oneway false for get response from destination
        sendoutMessage(conduit, outMessage, false);
        //wait for the message to be got from the destination, 
        // create the thread to handler the Destination incomming message

        waitForReceiveInMessage();
        verifyReceivedMessage(inMessage);

        verifyRequestResponseHeaders(inMessage, outMessage);

/*
        CamelMessageHeadersType inHeader =
            (CamelMessageHeadersType)inMessage.get(CamelConstants.Camel_CLIENT_RESPONSE_HEADERS);
        assertTrue("property has been excluded", inHeader.getProperty().isEmpty());
*/

        // wait for a while for the camel session recycling
        Thread.sleep(1000);
        destination.shutdown();
    }
}
