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
package org.apache.camel.impl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.xml.StringSource;
import org.junit.Test;

public class LogDebugBodyStreamsTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        registry.bind("logFormatter", new TraceExchangeFormatter());
        return registry;
    }

    @Test
    public void testLogBodyStreamStringSourceDisabled() throws Exception {
        context.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_STREAMS, "false");

        StringSource body = new StringSource("<?xml version=\"1.0\"?><person><name>Claus</name></person>");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        // should be logged anyway
        TraceExchangeFormatter myFormatter = context.getRegistry().lookupByNameAndType("logFormatter", TraceExchangeFormatter.class);
        String msg = myFormatter.getMessage();
        assertTrue(msg.endsWith("Body: <?xml version=\"1.0\"?><person><name>Claus</name></person>]"));
    }

    @Test
    public void testLogBodyStreamStringSourceDisabledByDefault() throws Exception {
        context.getGlobalOptions().remove(Exchange.LOG_DEBUG_BODY_STREAMS);

        StringSource body = new StringSource("<?xml version=\"1.0\"?><person><name>Claus</name></person>");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        // should be logged anyway
        TraceExchangeFormatter myFormatter = context.getRegistry().lookupByNameAndType("logFormatter", TraceExchangeFormatter.class);
        String msg = myFormatter.getMessage();
        assertTrue(msg.endsWith("Body: <?xml version=\"1.0\"?><person><name>Claus</name></person>]"));
    }

    @Test
    public void testLogBodyStreamStringSourceEnabled() throws Exception {
        context.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_STREAMS, "true");

        StringSource body = new StringSource("<?xml version=\"1.0\"?><person><name>Claus</name></person>");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        // should be logged anyway
        TraceExchangeFormatter myFormatter = context.getRegistry().lookupByNameAndType("logFormatter", TraceExchangeFormatter.class);
        String msg = myFormatter.getMessage();
        assertTrue(msg.endsWith("Body: <?xml version=\"1.0\"?><person><name>Claus</name></person>]"));
    }

    @Test
    public void testLogBodyStreamDisabled() throws Exception {
        context.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_STREAMS, "false");

        InputStream body = new ByteArrayInputStream("Hello World".getBytes());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        // should NOT be logged
        TraceExchangeFormatter myFormatter = context.getRegistry().lookupByNameAndType("logFormatter", TraceExchangeFormatter.class);
        String msg = myFormatter.getMessage();
        assertTrue(msg.endsWith("Body: [Body is instance of java.io.InputStream]]"));
    }

    @Test
    public void testLogBodyStreamDisabledByDefault() throws Exception {
        context.getGlobalOptions().remove(Exchange.LOG_DEBUG_BODY_STREAMS);

        InputStream body = new ByteArrayInputStream("Hello World".getBytes());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        // should NOT be logged
        TraceExchangeFormatter myFormatter = context.getRegistry().lookupByNameAndType("logFormatter", TraceExchangeFormatter.class);
        String msg = myFormatter.getMessage();
        assertTrue(msg.endsWith("Body: [Body is instance of java.io.InputStream]]"));
    }

    @Test
    public void testLogBodyStreamEnabled() throws Exception {
        context.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_STREAMS, "true");

        InputStream body = new ByteArrayInputStream("Hello World".getBytes());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        // should be logged
        TraceExchangeFormatter myFormatter = context.getRegistry().lookupByNameAndType("logFormatter", TraceExchangeFormatter.class);
        String msg = myFormatter.getMessage();
        assertTrue(msg.endsWith("Body: [Body is instance of java.io.InputStream]]"));
        assertIsInstanceOf(InputStream.class, mock.getReceivedExchanges().get(0).getIn().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("mock:result");
            }
        };
    }
}
