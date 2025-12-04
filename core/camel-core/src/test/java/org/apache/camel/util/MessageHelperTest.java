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

package org.apache.camel.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.support.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test cases for {@link MessageHelper}
 */
public class MessageHelperTest {

    private Message message;
    private final CamelContext camelContext = new DefaultCamelContext();

    @BeforeEach
    public void setUp() {
        message = new DefaultMessage(camelContext);
    }

    /*
     * Tests the {@link MessageHelper#resetStreamCache(Message)} method
     */
    @Test
    public void testResetStreamCache() {
        // should not throw exceptions when Message or message body is null
        MessageHelper.resetStreamCache(null);
        MessageHelper.resetStreamCache(message);

        // handle StreamCache
        final AtomicBoolean reset = new AtomicBoolean();
        message.setBody(new StreamCache() {
            public void reset() {
                reset.set(true);
            }

            public void writeTo(OutputStream os) {
                // noop
            }

            public StreamCache copy(Exchange exchange) {
                return null;
            }

            public boolean inMemory() {
                return true;
            }

            @Override
            public long length() {
                return 0;
            }

            @Override
            public long position() {
                return 0;
            }
        });
        MessageHelper.resetStreamCache(message);
        assertTrue(reset.get(), "Should have reset the stream cache");
    }

    @Test
    public void testGetContentType() {
        message.setHeader(Exchange.CONTENT_TYPE, "text/xml");
        assertEquals("text/xml", MessageHelper.getContentType(message));
    }

    @Test
    public void testGetContentEncpding() {
        message.setHeader(Exchange.CONTENT_ENCODING, "iso-8859-1");
        assertEquals("iso-8859-1", MessageHelper.getContentEncoding(message));
    }

    @Test
    public void testCopyHeaders() {
        Message source = message;
        Message target = new DefaultMessage(camelContext);

        source.setHeader("foo", 123);
        source.setHeader("bar", 456);
        target.setHeader("bar", "yes");

        MessageHelper.copyHeaders(source, target, false);

        assertEquals(123, target.getHeader("foo"));
        assertEquals("yes", target.getHeader("bar"));
    }

    @Test
    public void testCopyHeadersOverride() {
        Message source = message;
        Message target = new DefaultMessage(camelContext);

        source.setHeader("foo", 123);
        source.setHeader("bar", 456);
        target.setHeader("bar", "yes");

        MessageHelper.copyHeaders(source, target, true);

        assertEquals(123, target.getHeader("foo"));
        assertEquals(456, target.getHeader("bar"));
    }

    @Test
    public void testCopyHeadersWithHeaderFilterStrategy() {
        CamelContext context = new DefaultCamelContext();
        context.start();

        message = new DefaultExchange(context).getIn();

        Message source = message;
        Message target = message.getExchange().getOut();

        DefaultHeaderFilterStrategy headerFilterStrategy = new DefaultHeaderFilterStrategy();
        headerFilterStrategy.setInFilterPattern("foo");

        source.setHeader("foo", 123);
        source.setHeader("bar", 456);
        target.setHeader("bar", "yes");

        MessageHelper.copyHeaders(source, target, headerFilterStrategy, true);

        assertNull(target.getHeader("foo"));
        assertEquals(456, target.getHeader("bar"));
        context.stop();
    }

    @Test
    public void testDumpAsXmlPlainBody() {
        CamelContext context = new DefaultCamelContext();
        context.start();

        message = new DefaultExchange(context).getIn();

        // xml message body
        message.setBody("Hello World");
        message.setHeader("foo", 123);

        String out = MessageHelper.dumpAsXml(message);
        assertTrue(out.contains("<body type=\"java.lang.String\">Hello World</body>"), "Should contain body");

        context.stop();
    }

    @Test
    public void testDumpAsXmlBody() {
        CamelContext context = new DefaultCamelContext();
        context.start();

        message = new DefaultExchange(context).getIn();

        // xml message body
        message.setBody("<?xml version=\"1.0\"?><hi>Hello World</hi>");
        message.setHeader("foo", 123);

        String out = MessageHelper.dumpAsXml(message);
        assertTrue(
                out.contains(
                        "<body type=\"java.lang.String\">&lt;?xml version=&quot;1.0&quot;?&gt;&lt;hi&gt;Hello World&lt;/hi&gt;</body>"),
                "Should contain body");
        assertTrue(out.contains(message.getExchange().getExchangeId()), "Should contain exchangeId");

        context.stop();
    }

    @Test
    public void testDumpAsXmlNoBody() {
        CamelContext context = new DefaultCamelContext();
        context.start();

        message = new DefaultExchange(context).getIn();

        // xml message body
        message.setBody("Hello World");
        message.setHeader("foo", 123);

        String out = MessageHelper.dumpAsXml(message, false);

        assertEquals(
                "<message exchangeId=\"" + message.getExchange().getExchangeId()
                        + "\" exchangePattern=\"InOnly\" exchangeType=\"org.apache.camel.support.DefaultExchange\" messageType=\"org.apache.camel.support.DefaultMessage\">"
                        + "\n  <headers>\n    <header key=\"foo\" type=\"java.lang.Integer\">123</header>\n  </headers>\n</message>",
                out);

        context.stop();
    }

    @Test
    public void testDumpAsXmlNoBodyIndent() {
        CamelContext context = new DefaultCamelContext();
        context.start();

        message = new DefaultExchange(context, ExchangePattern.InOut).getIn();

        // xml message body
        message.setBody("Hello World");
        message.setHeader("foo", 123);

        String out = MessageHelper.dumpAsXml(message, false, 2);

        assertEquals(
                "  <message exchangeId=\"" + message.getExchange().getExchangeId()
                        + "\" exchangePattern=\"InOut\" exchangeType=\"org.apache.camel.support.DefaultExchange\" messageType=\"org.apache.camel.support.DefaultMessage\">"
                        + "\n    <headers>\n      <header key=\"foo\" type=\"java.lang.Integer\">123</header>\n    </headers>\n  </message>",
                out);

        context.stop();
    }

    @Test
    public void testMessageDumpBody() {
        CamelContext context = new DefaultCamelContext();
        context.start();

        message = new DefaultExchange(context).getIn();

        // xml message body
        message.setBody("Hello World");
        message.setHeader("foo", 123);

        String out = MessageHelper.dumpAsXml(message, true);
        assertNotNull(out);
        assertTrue(out.contains("Hello World"));
    }

    @Test
    public void testMessageDumpBodyJSon() {
        CamelContext context = new DefaultCamelContext();
        context.start();

        message = new DefaultExchange(context).getIn();

        // xml message body
        message.setBody("Hello World");
        message.setHeader("foo", 123);

        String out = MessageHelper.dumpAsJSon(message, true);
        assertNotNull(out);
        assertTrue(out.contains("Hello World"));
    }

    @Test
    public void testDumpAsXmlBodyJSon() {
        CamelContext context = new DefaultCamelContext();
        context.start();

        message = new DefaultExchange(context).getIn();

        // xml message body
        message.setBody("<?xml version=\"1.0\"?><hi>Hello World</hi>");
        message.setHeader("foo", 123);

        String out = MessageHelper.dumpAsJSon(message);
        // xml is escaped in json output
        assertTrue(out.contains(message.getExchange().getExchangeId()), "Should contain exchangeId");

        context.stop();
    }

    @Test
    public void testMessageDumpBodyIndentJSon() {
        CamelContext context = new DefaultCamelContext();
        context.start();

        message = new DefaultExchange(context).getIn();

        // xml message body
        message.setBody("Hello World");
        message.setHeader("foo", 123);

        String out = MessageHelper.dumpAsJSon(message, true, 4);
        assertNotNull(out);
        assertTrue(out.contains("Hello World"));
    }
}
