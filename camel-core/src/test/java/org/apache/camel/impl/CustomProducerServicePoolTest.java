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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ServicePool;

/**
 * Unit test for a custom ServicePool for producer.
 *
 * @version 
 */
public class CustomProducerServicePoolTest extends ContextTestSupport {

    private static int counter;

    private static final class MyEndpoint extends DefaultEndpoint {

        private MyEndpoint(String endpointUri, CamelContext camelContext) {
            setCamelContext(camelContext);
            setEndpointUri(endpointUri);
        }

        public Producer createProducer() throws Exception {
            return new MyProducer(this);
        }

        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        public boolean isSingleton() {
            return true;
        }

        @Override
        protected String createEndpointUri() {
            return "my";
        }
    }

    private static final class MyProducer extends DefaultProducer implements ServicePoolAware {

        MyProducer(Endpoint endpoint) {
            super(endpoint);
        }

        public void process(Exchange exchange) throws Exception {
            counter++;
        }
    }

    private static class MyPool implements ServicePool<Endpoint, Producer> {

        private Producer producer;

        public void setCapacity(int capacity) {
        }

        public int getCapacity() {
            return 0;
        }

        public Producer addAndAcquire(Endpoint endpoint, Producer producer) {
            if (endpoint instanceof MyEndpoint) {
                return producer;
            } else {
                return null;
            }
        }

        public Producer acquire(Endpoint endpoint) {
            if (endpoint instanceof MyEndpoint) {
                Producer answer = producer;
                producer = null;
                return answer;
            } else {
                return null;
            }
        }

        public void release(Endpoint endpoint, Producer producer) {
            this.producer = producer;
        }

        public void start() throws Exception {
        }

        public int size() {
            return producer != null ? 1 : 0;
        }

        public void purge() {
            producer = null;
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testCustomProducerServicePool() throws Exception {
        MyPool pool = new MyPool();
        pool.start();
        context.setProducerServicePool(pool);

        context.addEndpoint("my", new MyEndpoint("my", context));

        Endpoint endpoint = context.getEndpoint("my");

        assertNull(pool.acquire(endpoint));
        assertEquals(0, pool.size());

        Producer producer = new MyProducer(endpoint);
        producer = pool.addAndAcquire(endpoint, producer);
        assertEquals(0, pool.size());

        pool.release(endpoint, producer);
        assertEquals(1, pool.size());

        producer = pool.acquire(endpoint);
        assertNotNull(producer);
        assertEquals(0, pool.size());

        pool.release(endpoint, producer);
        assertEquals(1, pool.size());

        pool.purge();
        assertEquals(0, pool.size());

        assertIsInstanceOf(MyPool.class, context.getProducerServicePool());
    }

    public void testCustomProducerServicePoolInRoute() throws Exception {
        context.addEndpoint("my", new MyEndpoint("my", context));

        MyPool pool = new MyPool();
        pool.start();
        context.setProducerServicePool(pool);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("my", "mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        assertEquals(2, counter);
        assertEquals(1, pool.size());

        pool.purge();
        assertEquals(0, pool.size());
    }

}
