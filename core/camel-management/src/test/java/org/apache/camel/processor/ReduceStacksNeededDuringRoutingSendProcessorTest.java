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

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReduceStacksNeededDuringRoutingSendProcessorTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ReduceStacksNeededDuringRoutingSendProcessorTest.class);

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testReduceStacksNeeded() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // context.setTracing(true);
                MyEndpoint my = new MyEndpoint("myendpoint:foo", getContext());

                from("seda:start")
                        .to("log:foo")
                        .to("log:bar")
                        .to("log:baz")
                        .to(my)
                        .to("mock:result");
            }
        };
    }

    public static final class MyEndpoint extends DefaultEndpoint {

        public MyEndpoint(String uri, CamelContext context) {
            super("myendpoint:foo", null);
            setCamelContext(context);
        }

        @Override
        public Producer createProducer() throws Exception {
            return new MyProducer(this);
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    public static final class MyProducer extends DefaultAsyncProducer {

        public MyProducer(Endpoint endpoint) {
            super(endpoint);
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            try {
                throw new IllegalArgumentException("Forced to dump stacktrace");
            } catch (Exception e) {
                e.fillInStackTrace();
                LOG.info("There are " + e.getStackTrace().length + " lines in the stacktrace");
                LOG.error("Dump stacktrace to log", e);
            }
            callback.done(true);
            return true;
        }
    }
}
