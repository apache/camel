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

import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ToDynamicStopProducerTest extends ContextTestSupport {

    private static String events = "";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testNoCache() throws Exception {
        events = "";

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("mymock", new MyMockComponent());

                from("direct:a")
                        .toD("${header.myHeader}", -1).id("foo");
            }
        });
        context.start();

        assertEquals(1, context.getEndpointRegistry().size());

        sendBody("foo", "mymock:x");
        sendBody("foo", "mymock:y");
        sendBody("foo", "mymock:z");
        sendBody("bar", "mymock:x");
        sendBody("bar", "mymock:y");
        sendBody("bar", "mymock:z");

        List<Processor> list = getProcessors("foo");
        SendDynamicProcessor sdp = (SendDynamicProcessor) list.get(0);
        assertNotNull(sdp);
        assertEquals(-1, sdp.getCacheSize());

        assertEquals(1, context.getEndpointRegistry().size());

        // should be stopped after use
        assertEquals("xyzxyz", events);

        context.stop();

        assertEquals("xyzxyz", events);
    }

    @Test
    public void testDefaultCache() throws Exception {
        events = "";

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("mymock", new MyMockComponent());

                from("direct:a")
                        .toD("${header.myHeader}").id("foo");
            }
        });
        context.start();

        assertEquals(1, context.getEndpointRegistry().size());

        sendBody("foo", "mymock:x");
        sendBody("foo", "mymock:y");
        sendBody("foo", "mymock:z");
        sendBody("bar", "mymock:x");
        sendBody("bar", "mymock:y");
        sendBody("bar", "mymock:z");

        List<Processor> list = getProcessors("foo");
        SendDynamicProcessor sdp = (SendDynamicProcessor) list.get(0);
        assertNotNull(sdp);
        assertEquals(0, sdp.getCacheSize());

        assertEquals(4, context.getEndpointRegistry().size());

        assertEquals("", events);

        context.stop();

        assertEquals("xyz", events);
    }

    @Test
    public void testCacheOne() throws Exception {
        events = "";

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("mymock", new MyMockComponent());

                from("direct:a")
                        .toD("${header.myHeader}", 1).id("foo");
            }
        });
        context.start();

        assertEquals(1, context.getEndpointRegistry().size());

        sendBody("foo", "mymock:x");
        sendBody("foo", "mymock:y");
        sendBody("foo", "mymock:z");
        sendBody("bar", "mymock:x");
        sendBody("bar", "mymock:y");
        sendBody("bar", "mymock:z");

        List<Processor> list = getProcessors("foo");
        SendDynamicProcessor sdp = (SendDynamicProcessor) list.get(0);
        assertNotNull(sdp);
        assertEquals(1, sdp.getCacheSize());

        assertEquals(2, context.getEndpointRegistry().size());

        assertEquals("xyzxy", events);

        context.stop();

        assertEquals("xyzxyz", events);
    }

    @Test
    public void testCacheTwo() throws Exception {
        events = "";

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("mymock", new MyMockComponent());

                from("direct:a")
                        .toD("${header.myHeader}", 2).id("foo");
            }
        });
        context.start();

        assertEquals(1, context.getEndpointRegistry().size());

        sendBody("foo", "mymock:x");
        sendBody("foo", "mymock:y");
        sendBody("foo", "mymock:z");
        sendBody("bar", "mymock:x");
        sendBody("bar", "mymock:y");
        sendBody("bar", "mymock:z");

        List<Processor> list = getProcessors("foo");
        SendDynamicProcessor sdp = (SendDynamicProcessor) list.get(0);
        assertNotNull(sdp);
        assertEquals(2, sdp.getCacheSize());

        assertEquals(3, context.getEndpointRegistry().size());

        assertEquals("xyzx", events);

        context.stop();

        assertEquals("xyzxyz", events);
    }

    @Test
    public void testCacheThree() throws Exception {
        events = "";

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("mymock", new MyMockComponent());

                from("direct:a")
                        .toD("${header.myHeader}", 3).id("foo");
            }
        });
        context.start();

        assertEquals(1, context.getEndpointRegistry().size());

        sendBody("foo", "mymock:x");
        sendBody("foo", "mymock:y");
        sendBody("foo", "mymock:z");
        sendBody("bar", "mymock:x");
        sendBody("bar", "mymock:y");
        sendBody("bar", "mymock:z");

        List<Processor> list = getProcessors("foo");
        SendDynamicProcessor sdp = (SendDynamicProcessor) list.get(0);
        assertNotNull(sdp);
        assertEquals(3, sdp.getCacheSize());

        assertEquals(4, context.getEndpointRegistry().size());

        assertEquals("", events);

        context.stop();

        assertEquals("xyz", events);
    }

    protected void sendBody(String body, String uri) {
        template.sendBodyAndHeader("direct:a", body, "myHeader", uri);
    }

    private class MyMockComponent extends MockComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new MyMockEndpoint(this, uri, remaining);
        }
    }

    private class MyMockEndpoint extends MockEndpoint {

        private final String queue;

        public MyMockEndpoint(MyMockComponent component, String uri, String queue) {
            super(uri, component);
            this.queue = queue;
        }

        public String getQueue() {
            return queue;
        }

        @Override
        public Producer createProducer() throws Exception {
            return new MyMockProducer(this);
        }
    }

    private class MyMockProducer extends DefaultProducer {

        public MyMockProducer(MyMockEndpoint endpoint) {
            super(endpoint);
        }

        @Override
        public MyMockEndpoint getEndpoint() {
            return (MyMockEndpoint) super.getEndpoint();
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            // noop
        }

        @Override
        protected void doStop() throws Exception {
            super.doStop();
            events += getEndpoint().getQueue();
        }

    }

}
