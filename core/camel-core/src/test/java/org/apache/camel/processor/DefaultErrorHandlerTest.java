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

import org.apache.camel.Channel;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.processor.errorhandler.DefaultErrorHandler;
import org.junit.Test;

/**
 * Default error handler test
 */
public class DefaultErrorHandlerTest extends ContextTestSupport {

    @Test
    public void testRoute() {
        Route route = context.getRoutes().get(0);
        DefaultRoute consumerRoute = assertIsInstanceOf(DefaultRoute.class, route);

        Processor processor = unwrap(consumerRoute.getProcessor());
        Pipeline pipeline = assertIsInstanceOf(Pipeline.class, processor);

        // there should be a default error handler in front of each processor in
        // this pipeline
        for (Processor child : pipeline.next()) {
            Channel channel = assertIsInstanceOf(Channel.class, child);
            assertNotNull("There should be an error handler", channel.getErrorHandler());
            assertIsInstanceOf(DefaultErrorHandler.class, channel.getErrorHandler());
        }
    }

    @Test
    public void testOk() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWithError() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Kabom");
            fail("Should have thrown a RuntimeCamelException");
        } catch (RuntimeCamelException e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // if no error handler is configured it should
                // use the default error handler

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        if ("Kabom".equals(body)) {
                            throw new IllegalArgumentException("Boom");
                        }
                        exchange.getIn().setBody("Bye World");
                    }
                }).to("mock:result");
            }
        };
    }
}
