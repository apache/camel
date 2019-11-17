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
package org.apache.camel.component.hystrix.processor;

import com.netflix.hystrix.exception.HystrixBadRequestException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HystrixBadRequestExceptionTest extends CamelTestSupport {

    @Test
    public void testHystrix() throws Exception {
        getMockEndpoint("mock:fallback").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello World"));
        assertTrue(out.isFailed());
        assertFalse(out.getProperty(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, boolean.class));
        assertFalse(out.getProperty(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, boolean.class));
        assertTrue(out.getException() instanceof HystrixBadRequestException);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("log:start")
                    .circuitBreaker()
                        .throwException(new HystrixBadRequestException("Should not fallback"))
                    .onFallback()
                        .to("mock:fallback")
                        .transform().constant("Fallback message")
                    .end()
                    .to("log:result")
                    .to("mock:result");
            }
        };
    }

}
