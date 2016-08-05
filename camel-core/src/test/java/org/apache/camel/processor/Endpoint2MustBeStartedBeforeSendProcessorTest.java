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
package org.apache.camel.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;

/**
 * @version 
 */
public class Endpoint2MustBeStartedBeforeSendProcessorTest extends ContextTestSupport {

    private MyEndpoint myendpoint;
    private volatile String order = "";

    public void testEndpointMustBeStartedBeforeProducer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                myendpoint = new MyEndpoint("myendpoint", context);

                from("direct:start")
                    .to(myendpoint);
            }
        });
        context.start();

        assertEquals("EndpointProducer", order);
    }

    public void testEndpointMustBeStartedBeforeConsumer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                myendpoint = new MyEndpoint("myendpoint", context);

                from(myendpoint)
                    .to("mock:result");
            }
        });
        context.start();

        assertEquals("EndpointConsumer", order);
    }

    public void testEndpointMustBeStartedBeforeConsumerAndProducer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                myendpoint = new MyEndpoint("myendpoint", context);

                from(myendpoint)
                    .to("mock:result")
                    .to(myendpoint);
            }
        });
        context.start();

        assertEquals("EndpointProducerConsumer", order);
    }

    public void testEndpointStartedOnceAndOnlyStoppedOnShutdown() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                myendpoint = new MyEndpoint("myendpoint", context);

                from(myendpoint).routeId("foo")
                    .to("mock:result")
                    .to(myendpoint);
            }
        });
        context.start();

        assertEquals("EndpointProducerConsumer", order);
        order = "";

        context.stopRoute("foo");
        assertEquals("StopConsumerStopProducer", order);

        order = "";
        context.startRoute("foo");
        assertEquals("ProducerConsumer", order);

        order = "";
        context.stop();
        assertEquals("StopConsumerStopProducerStopEndpoint", order);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private final class MyEndpoint extends DefaultEndpoint {

        private MyEndpoint(String endpointUri, CamelContext camelContext) {
            setCamelContext(camelContext);
            setEndpointUri(endpointUri);
        }

        public Producer createProducer() throws Exception {
            return new MyProducer(this);
        }

        public Consumer createConsumer(Processor processor) throws Exception {
            return new MyConsumer(this, null);
        }

        public boolean isSingleton() {
            return true;
        }

        // in this test we use start/stop to implement logic
        // this is however discouraged, as you should prefer to use doStart/doStop

        @Override
        public void doStart() throws Exception {
            order += "Endpoint";
        }

        @Override
        public void doStop() throws Exception {
            order += "StopEndpoint";
        }
    }

    private class MyProducer extends DefaultProducer {

        MyProducer(Endpoint endpoint) {
            super(endpoint);
        }

        public void process(Exchange exchange) throws Exception {
            // noop
        }

        @Override
        protected void doStart() throws Exception {
            order += "Producer";
        }

        @Override
        protected void doStop() throws Exception {
            order += "StopProducer";
        }
    }

    private class MyConsumer extends DefaultConsumer {

        MyConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        protected void doStart() throws Exception {
            order += "Consumer";
        }

        @Override
        protected void doStop() throws Exception {
            order += "StopConsumer";
        }
    }
}
