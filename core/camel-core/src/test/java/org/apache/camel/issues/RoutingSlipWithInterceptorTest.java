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
package org.apache.camel.issues;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoutingSlipWithInterceptorTest extends ContextTestSupport {

    private final MyInterceptStrategy interceptStrategy = new MyInterceptStrategy();

    public static class MyInterceptStrategy implements InterceptStrategy {
        private static final Logger LOGGER = LoggerFactory.getLogger(MyInterceptStrategy.class);
        private static int doneCount;

        @Override
        public Processor wrapProcessorInInterceptors(final CamelContext context, final NamedNode definition, final Processor target, final Processor nextTarget) throws Exception {
            if (definition instanceof RoutingSlipDefinition<?>) {
                final DelegateAsyncProcessor delegateAsyncProcessor = new DelegateAsyncProcessor() {

                    @Override
                    public boolean process(final Exchange exchange, final AsyncCallback callback) {
                        LOGGER.info("I'm doing someting");
                        return super.process(exchange, new AsyncCallback() {
                            public void done(final boolean doneSync) {
                                LOGGER.info("I'm done");
                                doneCount++;
                                callback.done(doneSync);
                            }
                        });
                    }
                };
                delegateAsyncProcessor.setProcessor(target);
                return delegateAsyncProcessor;
            }
            return new DelegateAsyncProcessor(target);
        }

        public void reset() {
            doneCount = 0;
        }
    }

    @Test
    public void testRoutingSlipOne() throws Exception {
        interceptStrategy.reset();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "slip", "direct:foo");

        assertMockEndpointsSatisfied();

        assertEquals("Done method shall be called only once", 1, MyInterceptStrategy.doneCount);
    }

    @Test
    public void testRoutingSlipTwo() throws Exception {
        interceptStrategy.reset();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "slip", "direct:foo,direct:bar");

        assertMockEndpointsSatisfied();

        assertEquals("Done method shall be called only once", 1, MyInterceptStrategy.doneCount);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.adapt(ExtendedCamelContext.class).addInterceptStrategy(interceptStrategy);

                from("direct:start").routingSlip(header("slip")).to("mock:result");

                from("direct:foo").to("log:foo").to("mock:foo");

                from("direct:bar").to("log:bar").to("mock:bar");
            }
        };
    }
}
