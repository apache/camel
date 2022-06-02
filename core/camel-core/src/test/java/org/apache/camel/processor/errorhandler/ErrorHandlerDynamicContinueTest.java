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
package org.apache.camel.processor.errorhandler;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ErrorHandlerDynamicContinueTest extends ContextTestSupport {

    @Test
    public void testContinued() throws Exception {
        getMockEndpoint("mock:start").expectedMessageCount(1);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);

        template.sendBodyAndHeader(
                "direct:start", "Hello World",
                "exception", "iae");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNotContinued() throws Exception {
        getMockEndpoint("mock:start").expectedMessageCount(1);

        CamelExecutionException exception = assertThrows(CamelExecutionException.class, () -> template.sendBodyAndHeader(
                "direct:start", "Hello World",
                "exception", "uoe"));

        assertEquals(UnsupportedOperationException.class, exception.getCause().getClass());
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class)
                        .maximumRedeliveries(1)
                        .continued(exchange -> {
                            Exception e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                            return e instanceof IllegalArgumentException;
                        });

                from("direct:start")
                    .to("mock:start")
                    .choice()
                        .when(simple("${header.exception} == 'iae'")).throwException(new IllegalArgumentException("Forced"))
                        .otherwise().throwException(new UnsupportedOperationException())
                    .end()
                    .to("mock:result");
            }
        };
    }
}
