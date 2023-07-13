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
package org.apache.camel.processor;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.StringReader;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StreamCachingAllowDenyTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testAllow() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getStreamCachingStrategy().setAllowClasses(ByteArrayInputStream.class);
                from("direct:a").to("mock:a");
            }
        });
        context.start();

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);
        template.sendBody("direct:a", new ByteArrayInputStream("Hello World".getBytes()));
        assertMockEndpointsSatisfied();
        // should be converted
        assertTrue(a.getReceivedExchanges().get(0).getMessage().getBody() instanceof StreamCache);

        assertEquals("Hello World", a.assertExchangeReceived(0).getIn().getBody(String.class));

        // reset
        a.reset();
        a.expectedMessageCount(1);
        template.sendBody("direct:a", new StringReader("Bye World"));
        assertMockEndpointsSatisfied();
        // should not be converted
        assertFalse(a.getReceivedExchanges().get(0).getMessage().getBody() instanceof StreamCache);
        assertEquals("Bye World", a.assertExchangeReceived(0).getIn().getBody(String.class));
    }

    @Test
    public void testDeny() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getStreamCachingStrategy().setDenyClasses(ByteArrayInputStream.class);
                from("direct:a").to("mock:a");
            }
        });
        context.start();

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);
        template.sendBody("direct:a", new ByteArrayInputStream("Hello World".getBytes()));
        assertMockEndpointsSatisfied();
        // should NOT be converted
        assertFalse(a.getReceivedExchanges().get(0).getMessage().getBody() instanceof StreamCache);

        assertEquals("Hello World", a.assertExchangeReceived(0).getIn().getBody(String.class));

        // reset
        a.reset();
        a.expectedMessageCount(1);
        template.sendBody("direct:a", new StringReader("Bye World"));
        assertMockEndpointsSatisfied();
        // should be converted
        assertTrue(a.getReceivedExchanges().get(0).getMessage().getBody() instanceof StreamCache);
        assertEquals("Bye World", a.assertExchangeReceived(0).getIn().getBody(String.class));
    }

    @Test
    public void testAllowAndDeny() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getStreamCachingStrategy().setAllowClasses(ByteArrayInputStream.class);
                context.getStreamCachingStrategy().setDenyClasses(Reader.class);
                from("direct:a").to("mock:a");
            }
        });
        context.start();

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);
        template.sendBody("direct:a", new ByteArrayInputStream("Hello World".getBytes()));
        assertMockEndpointsSatisfied();
        // should be converted
        assertTrue(a.getReceivedExchanges().get(0).getMessage().getBody() instanceof StreamCache);

        assertEquals("Hello World", a.assertExchangeReceived(0).getIn().getBody(String.class));

        // reset
        a.reset();
        a.expectedMessageCount(1);
        template.sendBody("direct:a", new StringReader("Bye World"));
        assertMockEndpointsSatisfied();
        // should not be converted
        assertFalse(a.getReceivedExchanges().get(0).getMessage().getBody() instanceof StreamCache);
        assertEquals("Bye World", a.assertExchangeReceived(0).getIn().getBody(String.class));
    }

    @Test
    public void testDualDeny() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getStreamCachingStrategy().setDenyClasses(ByteArrayInputStream.class, Reader.class);
                from("direct:a").to("mock:a");
            }
        });
        context.start();

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);
        template.sendBody("direct:a", new ByteArrayInputStream("Hello World".getBytes()));
        assertMockEndpointsSatisfied();
        // should NOT be converted
        assertFalse(a.getReceivedExchanges().get(0).getMessage().getBody() instanceof StreamCache);

        assertEquals("Hello World", a.assertExchangeReceived(0).getIn().getBody(String.class));

        // reset
        a.reset();
        a.expectedMessageCount(1);
        template.sendBody("direct:a", new StringReader("Bye World"));
        assertMockEndpointsSatisfied();
        // should not be converted
        assertFalse(a.getReceivedExchanges().get(0).getMessage().getBody() instanceof StreamCache);
        assertEquals("Bye World", a.assertExchangeReceived(0).getIn().getBody(String.class));
    }

}
