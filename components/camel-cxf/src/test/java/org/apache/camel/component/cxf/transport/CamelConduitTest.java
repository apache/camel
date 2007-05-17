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
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CamelConduitTest extends CamelTestSupport {
    public void DISABLED_testGetConfiguration() throws Exception {
        // setup the new bus to get the configuration file
        SpringBusFactory bf = new SpringBusFactory();
        BusFactory.setDefaultBus(null);
        bus = bf.createBus("/wsdl/camel_test_config.xml");
        BusFactory.setDefaultBus(bus);
        setupServiceInfo("http://cxf.apache.org/camel_conf_test",
                "/wsdl/camel_test_no_addr.wsdl",
                "HelloWorldQueueBinMsgService",
                "HelloWorldQueueBinMsgPort");
        CamelConduit conduit = setupCamelConduit(false, false);
        /*
        assertEquals("Can't get the right ClientReceiveTimeout",
                     500L,
                     conduit.getClientConfig().getClientReceiveTimeout());
        assertEquals("Can't get the right SessionPoolConfig's LowWaterMark",
                     10,
                     conduit.getSessionPool().getLowWaterMark());
        assertEquals("Can't get the right AddressPolicy's ConnectionPassword",
                     "testPassword",
                     conduit.getCamelAddress().getConnectionPassword());
                     */
        bus.shutdown(false);
        BusFactory.setDefaultBus(null);
    }

    public void testPrepareSend() throws Exception {
        setupServiceInfo("http://cxf.apache.org/hello_world_camel",
                "/wsdl/camel_test.wsdl",
                "HelloWorldService",
                "HelloWorldPort");

        CamelConduit conduit = setupCamelConduit(false, false);
        Message message = new MessageImpl();
        try {
            conduit.prepare(message);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        verifySentMessage(false, message);
    }

    public void verifySentMessage(boolean send, Message message) {
        OutputStream os = message.getContent(OutputStream.class);
        assertTrue("OutputStream should not be null", os != null);
    }

    public void testSendOut() throws Exception {
        setupServiceInfo("http://cxf.apache.org/hello_world_camel",
                "/wsdl/camel_test.wsdl",
                "HelloWorldServiceLoop",
                "HelloWorldPortLoop");

        CamelConduit conduit = setupCamelConduit(true, false);
        Message message = new MessageImpl();
        // set the isOneWay to false
        sendoutMessage(conduit, message, false);
        verifyReceivedMessage(message);
    }

    public void verifyReceivedMessage(Message message) {
        ByteArrayInputStream bis =
                (ByteArrayInputStream) inMessage.getContent(InputStream.class);
        byte bytes[] = new byte[bis.available()];
        try {
            bis.read(bytes);
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        String reponse = new String(bytes);
        assertEquals("The reponse date should be equals", reponse, "HelloWorld");

/*
        CamelMessageHeadersType inHeader =
            (CamelMessageHeadersType)inMessage.get(CamelConstants.Camel_CLIENT_RESPONSE_HEADERS);
        
        assertTrue("The inMessage Camel Header should not be null", inHeader != null);
*/

    }
}
