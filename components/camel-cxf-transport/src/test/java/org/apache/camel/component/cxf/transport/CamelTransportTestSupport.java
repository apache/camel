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

import java.io.IOException;
import java.io.OutputStream;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mockito;

public abstract class CamelTransportTestSupport extends CamelTestSupport {

    protected Bus bus;
    protected EndpointInfo endpointInfo;
    protected EndpointReferenceType target;
    protected MessageObserver observer;
    protected Message inMessage;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        BusFactory bf = BusFactory.newInstance();
        //setup the camel transport for the bus
        bus = bf.createBus();
        DestinationFactoryManager dfm = bus.getExtension(DestinationFactoryManager.class);
        CamelTransportFactory camelTransportFactory = new CamelTransportFactory();
        //set the context here to the transport factory;
        camelTransportFactory.setCamelContext(context);
        ConduitInitiatorManager cim = bus.getExtension(ConduitInitiatorManager.class);
        dfm.registerDestinationFactory(CamelTransportFactory.TRANSPORT_ID, camelTransportFactory);
        cim.registerConduitInitiator(CamelTransportFactory.TRANSPORT_ID, camelTransportFactory);
        BusFactory.setDefaultBus(bus);
        endpointInfo = new EndpointInfo();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        bus.shutdown(true);
        super.tearDown();
    }

    protected CamelConduit setupCamelConduit(EndpointInfo endpointInfo, boolean send, boolean decoupled) {
        if (decoupled) {
            // setup the reference type
        } else {
            target = Mockito.mock(EndpointReferenceType.class);
        }

        CamelConduit camelConduit = new CamelConduit(context, bus, endpointInfo, target);

        if (send) {
            // setMessageObserver
            observer = new MessageObserver() {
                public void onMessage(Message m) {
                    inMessage = m;
                }
            };
            camelConduit.setMessageObserver(observer);
        }

        return camelConduit;
    }

    protected void sendoutMessage(Conduit conduit, Message message, Boolean isOneWay, String content) throws IOException {
        Exchange cxfExchange = message.getExchange();
        if (cxfExchange == null) {
            cxfExchange = new ExchangeImpl();
            cxfExchange.setOneWay(isOneWay);
            message.setExchange(cxfExchange);
            cxfExchange.setInMessage(message);
        }
        try {
            conduit.prepare(message);
        } catch (IOException ex) {
            assertFalse("CamelConduit can't perpare to send out message", false);
            ex.printStackTrace();
        }
        OutputStream os = message.getContent(OutputStream.class);
        assertTrue("The OutputStream should not be null ", os != null);
        os.write(content.getBytes());
        os.close();
    }


}
