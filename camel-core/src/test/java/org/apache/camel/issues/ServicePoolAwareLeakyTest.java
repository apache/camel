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
package org.apache.camel.issues;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.ServiceSupport;

public class ServicePoolAwareLeakyTest extends ContextTestSupport {

    private static final String LEAKY_SIEVE_STABLE = "leaky://sieve-stable";
    private static final String LEAKY_SIEVE_TRANSIENT = "leaky://sieve-transient";

    /**
     * Component that provides leaks producers.
     */
    private static class LeakySieveComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new LeakySieveEndpoint(uri);
        }
    }

    /**
     * Endpoint that provides leaky producers.
     */
    private static class LeakySieveEndpoint extends DefaultEndpoint {

        private final String uri;

        LeakySieveEndpoint(String uri) {
            this.uri = uri;
        }

        @Override
        public Producer createProducer() throws Exception {
            return new LeakySieveProducer(this);
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        @Override
        protected String createEndpointUri() {
            return uri;
        }
    }

    /**
     * Leaky producer - implements {@link ServicePoolAware}.
     */
    private static class LeakySieveProducer extends DefaultProducer implements ServicePoolAware {

        LeakySieveProducer(Endpoint endpoint) {
            super(endpoint);
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            // do nothing
        }

    }

    @Override
    protected boolean useJmx() {
        // only occurs when using JMX as the GC root for the producer is through a ManagedProducer created by the
        // context.addService() invocation
        return true;
    }

    /**
     * Returns true if verification of state should be performed during the test as opposed to at the end.
     */
    public boolean isFailFast() {
        return false;
    }

    /**
     * Returns true if during fast failure we should verify that the service pool remains in the started state.
     */
    public boolean isVerifyProducerServicePoolRemainsStarted() {
        return false;
    }

    public void testForMemoryLeak() throws Exception {
        registerLeakyComponent();

        final Map<String, AtomicLong> references = new HashMap<String, AtomicLong>();

        // track LeakySieveProducer lifecycle
        context.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onServiceAdd(CamelContext context, Service service, Route route) {
                if (service instanceof LeakySieveProducer) {
                    String key = ((LeakySieveProducer) service).getEndpoint().getEndpointKey();
                    AtomicLong num = references.get(key);
                    if (num == null) {
                        num = new AtomicLong();
                        references.put(key, num);
                    }
                    num.incrementAndGet();
                }
            }

            @Override
            public void onServiceRemove(CamelContext context, Service service, Route route) {
                if (service instanceof LeakySieveProducer) {
                    String key = ((LeakySieveProducer) service).getEndpoint().getEndpointKey();
                    AtomicLong num = references.get(key);
                    if (num == null) {
                        num = new AtomicLong();
                        references.put(key, num);
                    }
                    num.decrementAndGet();
                }
            }
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:sieve-transient")
                        .id("sieve-transient")
                        .to(LEAKY_SIEVE_TRANSIENT);

                from("direct:sieve-stable")
                        .id("sieve-stable")
                        .to(LEAKY_SIEVE_STABLE);
            }
        });

        context.start();

        for (int i = 0; i < 1000; i++) {
            ServiceSupport service = (ServiceSupport) context.getProducerServicePool();
            assertEquals(ServiceStatus.Started, service.getStatus());
            if (isFailFast()) {
                assertEquals(2, context.getProducerServicePool().size());
                assertEquals(1, references.get(LEAKY_SIEVE_TRANSIENT).get());
                assertEquals(1, references.get(LEAKY_SIEVE_STABLE).get());
            }

            context.stopRoute("sieve-transient");

            if (isFailFast()) {
                assertEquals("Expected no service references to remain", 0, references.get(LEAKY_SIEVE_TRANSIENT));
            }

            if (isFailFast()) {
                // looks like we cleared more than just our route, we've stopped and cleared the global ProducerServicePool
                // since SendProcessor.stop() invokes ServiceHelper.stopServices(producerCache, producer); which in turn invokes
                // ServiceHelper.stopAndShutdownService(pool);.
                //
                // Whilst stop on the SharedProducerServicePool is a NOOP shutdown is not and effects a stop of the pool.

                if (isVerifyProducerServicePoolRemainsStarted()) {
                    assertEquals(ServiceStatus.Started, service.getStatus());
                }
                assertEquals("Expected one stable producer to remain pooled", 1, context.getProducerServicePool().size());
                assertEquals("Expected one stable producer to remain as service", 1, references.get(LEAKY_SIEVE_STABLE).get());
            }

            // Send a body to verify behaviour of send producer after another route has been stopped
            sendBody("direct:sieve-stable", "");

            if (isFailFast()) {
                // shared pool is used despite being 'Stopped'
                if (isVerifyProducerServicePoolRemainsStarted()) {
                    assertEquals(ServiceStatus.Started, service.getStatus());
                }

                assertEquals("Expected only stable producer in pool", 1, context.getProducerServicePool().size());
                assertEquals("Expected no references to transient producer", 0, references.get(LEAKY_SIEVE_TRANSIENT).get());
                assertEquals("Expected reference to stable producer", 1, references.get(LEAKY_SIEVE_STABLE).get());
            }

            context.startRoute("sieve-transient");

            // ok, back to normal
            assertEquals(ServiceStatus.Started, service.getStatus());
            if (isFailFast()) {
                assertEquals("Expected both producers in pool", 2, context.getProducerServicePool().size());
                assertEquals("Expected one transient producer as service", 1, references.get(LEAKY_SIEVE_TRANSIENT).get());
                assertEquals("Expected one stable producer as service", 1, references.get(LEAKY_SIEVE_STABLE).get());
            }
        }

        if (!isFailFast()) {
            assertEquals("Expected both producers in pool", 2, context.getProducerServicePool().size());

            // if not fixed these will equal the number of iterations in the loop + 1
            assertEquals("Expected one transient producer as service", 1, references.get(LEAKY_SIEVE_TRANSIENT).get());
            assertEquals("Expected one stable producer as service", 1, references.get(LEAKY_SIEVE_STABLE).get());
        }
    }

    private void registerLeakyComponent() {
        // register leaky component
        context.addComponent("leaky", new LeakySieveComponent());
    }

}
