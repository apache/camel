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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.engine.DefaultProducerCache;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.Test;

public class ProducerCacheNonSingletonTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testNonSingleton() throws Exception {
        context.addComponent("dummy", new MyDummyComponent());

        DefaultProducerCache cache = new DefaultProducerCache(this, context, 100);
        cache.start();

        Endpoint endpoint = context.getEndpoint("dummy:foo");
        DefaultAsyncProducer producer = (DefaultAsyncProducer)cache.acquireProducer(endpoint);
        assertNotNull(producer);
        assertTrue("Should be started", producer.getStatus().isStarted());

        Object found = context.hasService(MyDummyProducer.class);
        assertNull("Should not store producer on CamelContext", found);

        cache.releaseProducer(endpoint, producer);
        assertTrue("Should still be started", producer.getStatus().isStarted());

        cache.stop();

        assertTrue("Should be stopped", producer.getStatus().isStopped());
    }

    public class MyDummyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new MyDummyEndpoint();
        }
    }

    public class MyDummyEndpoint extends DefaultEndpoint {

        @Override
        public Producer createProducer() throws Exception {
            return new MyDummyProducer(this);
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        @Override
        public boolean isSingleton() {
            return false;
        }

        @Override
        protected String createEndpointUri() {
            return "dummy://foo";
        }
    }

    private class MyDummyProducer extends DefaultAsyncProducer {

        public MyDummyProducer(Endpoint endpoint) {
            super(endpoint);
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            return false;
        }
    }
}
