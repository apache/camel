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
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.local.LocalConduit;
import org.apache.cxf.transport.local.LocalDestination;
import org.apache.cxf.transport.local.LocalTransportFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xmlsoap.schemas.wsdl.http.AddressType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @version $Revision$
 */
public class CxfTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(CxfTest.class);

    protected CamelContext camelContext = new DefaultCamelContext();
    protected CamelClient client = new CamelClient(camelContext);

    public void testInvokeOfServer() throws Exception {
        CxfEndpoint endpoint = (CxfEndpoint) camelContext.resolveEndpoint("cxf:http://localhost/test");
        assertNotNull(endpoint);

        // lets make sure we use the same factory
        LocalTransportFactory factory = endpoint.getLocalTransportFactory();

        EndpointInfo ei = new EndpointInfo(null, "http://schemas.xmlsoap.org/soap/http");
        AddressType a = new AddressType();
        a.setLocation("http://localhost/test");
        ei.addExtensor(a);

        LocalDestination d = (LocalDestination) factory.getDestination(ei);
        d.setMessageObserver(new EchoObserver());

        Exchange exchange = client.send("cxf:http://localhost/test", new Processor<Exchange>() {
            public void onExchange(Exchange exchange) {
                exchange.getIn().setBody("<hello>world</hello>");
            }
        });

        org.apache.camel.Message out = exchange.getOut();
        log.info("Received output message: " + out);

/*
        String output = out.getBody(String.class);
        log.info("Received output text: "+ output);
*/
    }

    protected class EchoObserver implements MessageObserver {
        public void onMessage(Message message) {
            try {
                log.info("Received message: "+ message + " with content types: " + message.getContentFormats());
                
                Conduit backChannel = message.getDestination().getBackChannel(message, null, null);
                message.remove(LocalConduit.DIRECT_DISPATCH);

                TypeConverter converter = camelContext.getTypeConverter();
                String request = converter.convertTo(String.class, message.getContent(InputStream.class));
                log.info("Request body: " + request);
                
                org.apache.cxf.message.Exchange exchange = message.getExchange();
                MessageImpl reply = new MessageImpl();
                //reply.setContent(String.class, "<reply>true</reply>");
                InputStream payload = converter.convertTo(InputStream.class, "<reply>true</reply>");
                reply.setContent(InputStream.class, payload);
                exchange.setOutMessage(reply);


                backChannel.send(reply);

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
                log.error("Caught: "+ e, e);
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
