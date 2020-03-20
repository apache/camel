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
package org.apache.camel.builder.endpoint;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.junit.Test;

public class LogToDAdvancedTest extends ContextTestSupport {

    private final AtomicBoolean handled = new AtomicBoolean();

    private ExchangeFormatter myFormatter = new DefaultExchangeFormatter() {
        @Override
        public String format(Exchange exchange) {
            handled.set(true);
            return "Hello";
        }
    };

    @Test
    public void testLog() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();

        assertTrue(handled.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(timer("foo").delay(-1).period(0).repeatCount(10))
                    .noAutoStartup()
                        .to("mock:result")
                        .toD(log("foo").advanced().exchangeFormatter(myFormatter));
            }
        };
    }
}
