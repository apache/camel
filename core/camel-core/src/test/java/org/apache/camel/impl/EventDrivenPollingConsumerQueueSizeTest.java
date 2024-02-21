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

import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.EventDrivenPollingConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EventDrivenPollingConsumerQueueSizeTest extends ContextTestSupport {

    private String uri = "my:foo?pollingConsumerQueueSize=10&pollingConsumerBlockWhenFull=false";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        context.addComponent("my", new MyQueueComponent());
    }

    @Test
    public void testQueueSize() throws Exception {
        // must start context as we do not use route builder that auto-start
        context.start();

        PollingConsumer consumer = context.getEndpoint(uri).createPollingConsumer();
        consumer.start();

        assertNotNull(consumer);
        EventDrivenPollingConsumer edpc = assertIsInstanceOf(EventDrivenPollingConsumer.class, consumer);
        assertEquals(0, edpc.getQueueSize());
        assertEquals(10, edpc.getQueueCapacity());
        assertFalse(edpc.isBlockWhenFull());

        for (int i = 0; i < 10; i++) {
            template.sendBody(uri, "Message " + i);
        }

        assertEquals(10, edpc.getQueueSize());

        CamelExecutionException e = assertThrows(CamelExecutionException.class, () -> template.sendBody(uri, "Message 10"),
                "Should have thrown exception");

        assertIsInstanceOf(IllegalStateException.class, e.getCause());

        Exchange out = consumer.receive(5000);
        assertNotNull(out);
        assertEquals("Message 0", out.getIn().getBody());

        assertEquals(9, edpc.getQueueSize());
        assertEquals(10, edpc.getQueueCapacity());

        // now there is room
        template.sendBody(uri, "Message 10");

        assertEquals(10, edpc.getQueueSize());
        assertEquals(10, edpc.getQueueCapacity());

        ServiceHelper.stopService(consumer);
        // not cleared if we stop
        assertEquals(10, edpc.getQueueSize());
        assertEquals(10, edpc.getQueueCapacity());

        ServiceHelper.stopAndShutdownService(consumer);
        // now its cleared as we shutdown
        assertEquals(0, edpc.getQueueSize());
        assertEquals(10, edpc.getQueueCapacity());
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private static final class MyQueueComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new MyQueueEndpoint(uri, this);
        }
    }

    private static final class MyQueueEndpoint extends DefaultEndpoint {

        private EventDrivenPollingConsumer consumer;

        private MyQueueEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        @Override
        public Producer createProducer() throws Exception {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) throws Exception {
                    consumer.process(exchange);
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return consumer;
        }

        @Override
        public PollingConsumer createPollingConsumer() throws Exception {
            return consumer;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        @Override
        protected void doStart() throws Exception {
            consumer = (EventDrivenPollingConsumer) super.createPollingConsumer();
            super.doStart();
        }
    }
}
