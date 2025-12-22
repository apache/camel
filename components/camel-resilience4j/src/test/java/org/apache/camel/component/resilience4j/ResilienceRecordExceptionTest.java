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
package org.apache.camel.component.resilience4j;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class ResilienceRecordExceptionTest extends CamelTestSupport {

    @Test
    public void testHello() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, true);
        template.sendBody("direct:start", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFile() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Fallback message");
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, false);
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED, true);
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, true);
        template.sendBody("direct:start", "file");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testKaboom() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("kaboom");
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, true);
        template.sendBody("direct:start", "kaboom");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testIo() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Fallback message");
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, false);
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED, true);
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, true);
        template.sendBody("direct:start", "io");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("log:start")
                        .circuitBreaker().resilience4jConfiguration().recordException(IOException.class).end()
                        .process(e -> {
                            String b = e.getMessage().getBody(String.class);
                            if ("kaboom".equals(b)) {
                                throw new NullPointerException();
                            } else if ("file".equals(b)) {
                                throw new FileNotFoundException("unknown.txt");
                            } else if ("io".equals(b)) {
                                throw new IOException("Host not found");
                            }
                        })
                        .onFallback()
                        .transform().constant("Fallback message")
                        .end()
                        .to("log:result")
                        .to("mock:result");
            }
        };
    }

}
