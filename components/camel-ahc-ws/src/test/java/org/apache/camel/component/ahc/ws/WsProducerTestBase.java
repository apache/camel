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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Timeout(10)
public abstract class WsProducerTestBase {
    protected static final String TEST_MESSAGE = "Hello World!";

    protected CamelContext camelContext;
    protected ProducerTemplate template;

    @BeforeEach
    public void setUp() {
        TestMessages.getInstance().getMessages().clear();

        camelContext = new DefaultCamelContext();
        camelContext.start();

        setUpComponent();
        template = camelContext.createProducerTemplate();
    }

    @AfterEach
    public void tearDown() {
        template.stop();
        camelContext.stop();
    }

    protected abstract void setUpComponent();

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

    @Test
    public void testWriteBytesToWebsocket() throws Exception {
        byte[] testMessageBytes = getByteTestMessage();
        testWriteToWebsocket(testMessageBytes);
        assertEquals(1, TestMessages.getInstance().getMessages().size());
        verifyMessage(testMessageBytes, TestMessages.getInstance().getMessages().get(0));
    }

    @Disabled
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
            assertEquals(new String((byte[]) original), new String((byte[]) result));
        } else if (original instanceof InputStream) {
            assertTrue(result instanceof byte[] || result instanceof InputStream);
            if (result instanceof byte[]) {
                result = new ByteArrayInputStream((byte[]) result);
            }
            try {
                int oc = 0;
                int or = 0;
                while (oc != -1) {
                    oc = ((InputStream) original).read();
                    or = ((InputStream) result).read();
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

    protected String createLongTextTestMessage() {
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
