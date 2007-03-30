/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.CamelClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.Bus;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.DestinationFactory;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.local.LocalConduit;
import org.apache.cxf.transport.local.LocalTransportFactory;
import org.xmlsoap.schemas.wsdl.http.AddressType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

/**
 * @version $Revision$
 */
public class CxfTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(CxfTest.class);
    protected CamelContext camelContext = new DefaultCamelContext();
    protected CamelClient client = new CamelClient(camelContext);

    public void testInvokeOfServer() throws Exception {
        // lets register a service
        EndpointInfo ei = new EndpointInfo(null, "http://schemas.xmlsoap.org/soap/http");
        AddressType a = new AddressType();
        a.setLocation("http://localhost/test");
        ei.addExtensor(a);

        Bus bus = CXFBusFactory.getDefaultBus();
        DestinationFactoryManager dfm = bus.getExtension(DestinationFactoryManager.class);
        DestinationFactory factory = dfm.getDestinationFactory(LocalTransportFactory.TRANSPORT_ID);

        Destination destination = factory.getDestination(ei);
        destination.setMessageObserver(new EchoObserver());

        // now lets invoke it via Camel
        CxfExchange exchange = (CxfExchange) client.send("cxf:http://localhost/test", new Processor<Exchange>() {
            public void onExchange(Exchange exchange) {
                exchange.getIn().setHeader("requestHeader", "foo");
                exchange.getIn().setBody("<hello>world</hello>");
            }
        });

        org.apache.camel.Message out = exchange.getOut();
        Message cxfOutMessage = exchange.getOutMessage();
        log.info("Received output message: " + out + " and CXF out: " + cxfOutMessage);

        assertEquals("replyHeader on CXF", "foo2", cxfOutMessage.get("replyHeader"));
        assertEquals("replyHeader on Camel", "foo2", out.getHeader("replyHeader"));

        String output = out.getBody(String.class);
        log.info("Received output text: " + output);
    }

    protected class EchoObserver implements MessageObserver {
        public void onMessage(Message message) {
            try {
                log.info("Received message: " + message + " with content types: " + message.getContentFormats());

                Conduit backChannel = message.getDestination().getBackChannel(message, null, null);
                message.remove(LocalConduit.DIRECT_DISPATCH);

                TypeConverter converter = camelContext.getTypeConverter();
                String request = converter.convertTo(String.class, message.getContent(InputStream.class));
                log.info("Request body: " + request);

                org.apache.cxf.message.Exchange exchange = message.getExchange();
                MessageImpl reply = new MessageImpl();
                reply.put("foo", "bar");
                assertEquals("foo header", "bar", reply.get("foo"));

                reply.put("replyHeader", message.get("requestHeader") + "2");

                Set<Map.Entry<String, Object>> entries = reply.entrySet();
                assertEquals("entrySet.size()", 2, entries.size());

                //reply.setContent(String.class, "<reply>true</reply>");
                InputStream payload = converter.convertTo(InputStream.class, "<reply>true</reply>");
                reply.setContent(InputStream.class, payload);
                exchange.setOutMessage(reply);

                log.info("sending reply: " + reply);
                backChannel.send(message);

/*
                backChannel.send(message);

                OutputStream out = message.getContent(OutputStream.class);
                InputStream in = message.getContent(InputStream.class);

                copy(in, out, 1024);

                out.close();
                in.close();
*/
            }
            catch (Exception e) {
                log.error("Caught: " + e, e);
                fail("Caught: " + e);
            }
        }
    }

    private static void copy(final InputStream input, final OutputStream output, final int bufferSize)
            throws IOException {
        try {
            final byte[] buffer = new byte[bufferSize];

            int n = input.read(buffer);
            while (-1 != n) {
                output.write(buffer, 0, n);
                n = input.read(buffer);
            }
        }
        finally {
            input.close();
            output.close();
        }
    }
}
