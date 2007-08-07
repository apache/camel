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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;

import javax.xml.namespace.QName;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.cxf.wsdl11.WSDLServiceFactory;

import org.easymock.classextension.EasyMock;

public abstract class CamelTestSupport extends TestCase {
    protected CamelContext camelContext = new DefaultCamelContext();
    protected Bus bus;
    protected EndpointInfo endpointInfo;
    protected EndpointReferenceType target;
    protected MessageObserver observer;
    protected Message inMessage;

    public void setUp() throws Exception {
        camelContext.start();
        BusFactory bf = BusFactory.newInstance();
        bus = bf.createBus();
        BusFactory.setDefaultBus(bus);
    }

    public void tearDown() throws Exception {
        bus.shutdown(true);
        if (System.getProperty("cxf.config.file") != null) {
            System.clearProperty("cxf.config.file");
        }
        camelContext.stop();
    }

    protected void setupServiceInfo(String ns, String wsdl, String serviceName, String portName) {
        URL wsdlUrl = getClass().getResource(wsdl);

        assertNotNull("Could not find WSDL: " + wsdl, wsdlUrl);
        WSDLServiceFactory factory = new WSDLServiceFactory(bus, wsdlUrl, new QName(ns, serviceName));

        Service service = factory.create();
        List<ServiceInfo> list = service.getServiceInfos();
        for (ServiceInfo serviceInfo : list) {
            endpointInfo = serviceInfo.getEndpoint(new QName(ns, portName));
            if (endpointInfo != null) {
                break;
            }
        }
    }

    protected void sendoutMessage(Conduit conduit, Message message, Boolean isOneWay) throws IOException {

        Exchange exchange = new ExchangeImpl();
        exchange.setOneWay(isOneWay);
        message.setExchange(exchange);
        exchange.setInMessage(message);
        try {
            conduit.prepare(message);
        } catch (IOException ex) {
            assertFalse("CamelConduit can't perpare to send out message", false);
            ex.printStackTrace();
        }
        OutputStream os = message.getContent(OutputStream.class);
        assertTrue("The OutputStream should not be null ", os != null);
        os.write("HelloWorld".getBytes());
        os.close();
    }

    protected CamelConduit setupCamelConduit(boolean send, boolean decoupled) {
        if (decoupled) {
            // setup the reference type
        } else {
            target = EasyMock.createMock(EndpointReferenceType.class);
        }

        CamelConduit camelConduit = new CamelConduit(camelContext, bus, endpointInfo, target);

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
}
