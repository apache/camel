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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.DelegateAsyncProcessor;
import org.apache.camel.spi.InterceptStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class AdviceWithStartTargetIssueTest extends ContextTestSupport {

    public void testAdvised() throws Exception {
        context.getRouteDefinitions().get(0).adviceWith(context,
                new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        interceptSendToEndpoint("mock:foo").skipSendToOriginalEndpoint()
                                .to("log:foo")
                                .to("mock:advised");
                    }
                });

        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:advised").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                getContext().addInterceptStrategy(new ContainerWideInterceptor());

                from("direct:start").to("mock:foo").to("mock:result");
            }
        };
    }

    static class ContainerWideInterceptor implements InterceptStrategy {

        private static final Logger LOG = LoggerFactory.getLogger(ContainerWideInterceptor.class);
        private static int count;

        public Processor wrapProcessorInInterceptors(final CamelContext context, final ProcessorDefinition<?> definition,
                                                     final Processor target, final Processor nextTarget) throws Exception {

            return new DelegateAsyncProcessor(new Processor() {

                public void process(Exchange exchange) throws Exception {
                    // we just count number of interceptions
                    count++;
                    LOG.info("I am the container wide interceptor. Intercepted total count: " + count);
                    target.process(exchange);
                }

                @Override
                public String toString() {
                    return "ContainerWideInterceptor[" + target + "]";
                }
            });
        }

        public int getCount() {
            return count;
        }
    }
}
