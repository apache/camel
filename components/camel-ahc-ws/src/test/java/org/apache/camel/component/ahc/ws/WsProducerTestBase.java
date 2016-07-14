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
package org.apache.camel.component.ahc.ws;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
public abstract class WsProducerTestBase extends Assert {
    
    protected static final String TEST_MESSAGE = "Hello World!";
    protected static final int PORT = AvailablePortFinder.getNextAvailable();
    
    protected CamelContext camelContext;
    protected ProducerTemplate template;
    protected Server server;
   
    public void startTestServer() throws Exception {
        // start a simple websocket echo service
        server = new Server(PORT);
        Connector connector = getConnector();
        server.addConnector(connector);
        
        ServletContextHandler ctx = new ServletContextHandler();
        ctx.setContextPath("/");
        ctx.addServlet(TestServletFactory.class.getName(), "/*");
 
        server.setHandler(ctx);

        server.start();
        assertTrue(server.isStarted());
    }
    
    public void stopTestServer() throws Exception {
        server.stop();
        server.destroy();
    }
    
    @Before
    public void setUp() throws Exception {
        TestMessages.getInstance().getMessages().clear();

        startTestServer();
        
        camelContext = new DefaultCamelContext();
        camelContext.start();
        
        setUpComponent();
        template = camelContext.createProducerTemplate();
    }

    @After
    public void tearDown() throws Exception {
        template.stop();
        camelContext.stop();
        
        stopTestServer();
    }

    protected abstract void setUpComponent() throws Exception;

    protected abstract Connector getConnector() throws Exception;

    protected abstract String getTargetURL();
    
    protected String getTextTestMessage() {
        return TEST_MESSAGE;
    }

    protected byte[] getByteTestMessage() throws UnsupportedEncodingException {
        return TEST_MESSAGE.getBytes("utf-8");
    }
    
    @Test
    public void testWriteToWebsocket() throws Exception {
        String testMessage = getTextTestMessage();
        testWriteToWebsocket(testMessage);
        assertEquals(1, TestMessages.getInstance().getMessages().size());
        verifyMessage(testMessage, TestMessages.getInstance().getMessages().get(0));
    }

    @Ignore
    @Test
    public void testWriteBytesToWebsocket() throws Exception {
        byte[] testMessageBytes = getByteTestMessage();
        testWriteToWebsocket(testMessageBytes);
        assertEquals(1, TestMessages.getInstance().getMessages().size());
        verifyMessage(testMessageBytes, TestMessages.getInstance().getMessages().get(0));
    }

    @Ignore
    @Test
    public void testWriteStreamToWebsocket() throws Exception {
        byte[] testMessageBytes = createLongByteTestMessage();
        testWriteToWebsocket(new ByteArrayInputStream(testMessageBytes)); 
        assertEquals(1, TestMessages.getInstance().getMessages().size());
        verifyMessage(testMessageBytes, TestMessages.getInstance().getMessages().get(0));
    }
    
    private void testWriteToWebsocket(Object message) throws Exception {
        Exchange exchange = sendMessage(getTargetURL(), message);
        assertNull(exchange.getException());
        
        long towait = 5000;
        while (towait > 0) {
            if (TestMessages.getInstance().getMessages().size() == 1) {
                break;
            }
            towait -= 500;
            Thread.sleep(500);
        }
    }

    private Exchange sendMessage(String endpointUri, final Object msg) {
        Exchange exchange = template.request(endpointUri, new Processor() {
            public void process(final Exchange exchange) {
                exchange.getIn().setBody(msg);
            }
        });
        return exchange;

    }

    private void verifyMessage(Object original, Object result) {
        if (original instanceof String && result instanceof String) {
            assertEquals(original, result);
        } else if (original instanceof byte[] && result instanceof byte[]) {
            // use string-equals as our bytes are string'able
            assertEquals(new String((byte[])original), new String((byte[])result));
        } else if (original instanceof InputStream) {
            assertTrue(result instanceof byte[] || result instanceof InputStream);
            if (result instanceof byte[]) {
                result = new ByteArrayInputStream((byte[])result);
            }
            try {
                int oc = 0;
                int or = 0;
                while (oc != -1) {
                    oc = ((InputStream)original).read();
                    or = ((InputStream)result).read();
                    assertEquals(oc, or);
                }
                assertEquals(-1, or);
            } catch (Exception e) {
                fail("unable to verify data: " + e);
            }
        } else {
            fail("unexpected message type for input " + original + ": " + result);
        }
    }

    protected byte[] createLongByteTestMessage() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] bs = TEST_MESSAGE.getBytes();
        try {
            for (int i = 1; i <= 100; i++) {
                baos.write(Integer.toString(i).getBytes());
                baos.write(0x20);
                baos.write(bs);
                baos.write(';');
            }
        } catch (IOException e) {
            // ignore
        }
        return baos.toByteArray();
    }

    protected String createLongTextTestMessage() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            sb.append(Integer.toString(i));
            sb.append(' ');
            sb.append(TEST_MESSAGE);
            sb.append(';');
        }
        return sb.toString();
    }
}
